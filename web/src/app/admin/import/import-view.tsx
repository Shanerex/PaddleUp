"use client";

import { useCallback, useEffect, useRef, useState, type DragEvent } from "react";
import {
  ApiError,
  importDataset,
  listDatasetPacks,
  listDatasets,
  type DatasetPack,
  type DatasetSummary,
  type ImportReport,
} from "../../../api/client";
import { HOME } from "../../../auth/redirect";
import { AppShell, shell } from "../../../ui/AppShell";
import { Button, ButtonLink } from "../../../ui/Button";
import { Icon } from "../../../ui/Icon";
import { fileMeta, versionSub } from "./describe";
import s from "./import.module.css";

type Result =
  | null
  | { kind: "report"; report: ImportReport }
  | { kind: "failed"; report: ImportReport; latest: number | null }
  | { kind: "unreadable"; message: string };

/**
 * Season data import for app admins (docs/design AdminImport `import`, `importEmpty`,
 * `importReport`, `importFailed`, `importDenied`). The page asks the server whether the caller is an
 * admin: a 403 shows the denied screen and never the form. Pack name, role keys and tag keys come
 * from the chosen Sport Pack.
 */
export function ImportView() {
  const [packs, setPacks] = useState<DatasetPack[] | null>(null);
  const [denied, setDenied] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [packId, setPackId] = useState("");
  const [versions, setVersions] = useState<DatasetSummary[] | null>(null);
  const [file, setFile] = useState<{ file: File; meta: string } | null>(null);
  const [result, setResult] = useState<Result>(null);
  const [busy, setBusy] = useState(false);
  const [hovering, setHovering] = useState(false);
  const input = useRef<HTMLInputElement>(null);

  const loadVersions = useCallback((id: string) => {
    return listDatasets(id)
      .then(setVersions)
      .catch(() => setVersions([]));
  }, []);

  useEffect(() => {
    let active = true;
    listDatasetPacks()
      .then((list) => {
        if (!active) return;
        setPacks(list);
        if (list[0]) {
          setPackId(list[0].id);
          void loadVersions(list[0].id);
        }
      })
      .catch((e) => {
        if (!active) return;
        if (e instanceof ApiError && e.status === 403) setDenied(true);
        else setLoadError("We couldn't load the packs. Check your connection and try again.");
      });
    return () => {
      active = false;
    };
  }, [loadVersions]);

  const pack = packs?.find((p) => p.id === packId);
  const packName = pack?.name ?? "";

  async function pick(next: File | null) {
    if (!next) return;
    let text: string | null = null;
    try {
      text = await next.text();
    } catch {
      text = null;
    }
    setFile({ file: next, meta: fileMeta(next.name, next.size, text) });
    setResult(null);
  }

  function onDrop(e: DragEvent) {
    e.preventDefault();
    setHovering(false);
    void pick(e.dataTransfer.files[0] ?? null);
  }

  async function submit() {
    if (!file || !pack) return;
    setBusy(true);
    try {
      const report = await importDataset(pack.id, file.file);
      setResult({ kind: "report", report });
      await loadVersions(pack.id);
    } catch (e) {
      if (e instanceof ApiError && e.status === 422) {
        const report = (e.body as { report?: ImportReport } | null)?.report;
        if (report) {
          const latest = versions?.[0]?.version ?? null;
          setResult({ kind: "failed", report, latest });
        } else {
          setResult({ kind: "unreadable", message: "The server rejected the file." });
        }
      } else if (e instanceof ApiError && e.status === 403) {
        setDenied(true);
      } else if (e instanceof ApiError && e.status === 413) {
        setResult({ kind: "unreadable", message: "The file is larger than 1 MB." });
      } else if (e instanceof ApiError && e.status === 400) {
        const message = (e.body as { message?: string } | null)?.message;
        setResult({ kind: "unreadable", message: message ?? "The file couldn't be read." });
      } else {
        setResult({ kind: "unreadable", message: "Something went wrong. Check your connection and try again." });
      }
    } finally {
      setBusy(false);
    }
  }

  if (denied) {
    return (
      <AppShell bleed>
        <div className={s.denied} style={{ padding: "32px 20px" }}>
          <div className={s.deniedBody}>
            <span className={s.deniedTile}>
              <Icon name="lock" size={32} />
            </span>
            <h1 className={s.deniedTitle}>This page is for app admins</h1>
            <p className={s.deniedText}>
              Your account isn&apos;t on the admin list. If you think it should be, ask whoever manages this PaddleUp.
            </p>
            <ButtonLink href={HOME} variant="brand" size="L">
              Back to your rooms
            </ButtonLink>
          </div>
        </div>
      </AppShell>
    );
  }

  const report = result && result.kind !== "unreadable" ? result.report : null;
  const ok = result?.kind === "report";
  const label = result ? "IMPORT ANOTHER FILE" : "NEW IMPORT";
  const latestVersion = versions?.[0]?.version ?? null;

  return (
    <AppShell badge="APP ADMIN">
      <div className={shell.wide} style={{ margin: "0 auto", width: "100%" }}>
        <div className={s.head}>
          <h1 className={s.title}>Season data</h1>
          <span className={s.lede}>
            Load the player list for a Sport Pack. Each import becomes a new version. New rooms copy the latest; rooms that already exist keep their copy.
          </span>
        </div>

        {result && (
          <div className={s.result} style={{ background: ok ? "var(--ok-tint)" : "var(--bad-tint)" }} role={ok ? "status" : "alert"}>
            <span className={s.resultTile} style={{ color: ok ? "var(--ok)" : "var(--bad)" }}>
              <Icon name={ok ? "check_circle" : "error"} size={24} />
            </span>
            <div className={s.resultBody}>
              <div className={s.resultText}>
                {result.kind === "report" && (
                  <>
                    <h2 className={s.resultTitle}>
                      v{result.report.version} is live with {result.report.imported} players.
                    </h2>
                    <span className={s.resultSub}>
                      {result.report.skipped === 0
                        ? "No rows were skipped. "
                        : `${result.report.skipped} ${result.report.skipped === 1 ? "row was" : "rows were"} skipped. `}
                      New rooms get v{result.report.version}. Existing rooms keep their copy until their Commissioner resets.
                    </span>
                  </>
                )}
                {result.kind === "failed" && (
                  <>
                    <h2 className={s.resultTitle}>Nothing imported.</h2>
                    <span className={s.resultSub}>
                      All {result.report.totalRows} {result.report.totalRows === 1 ? "row was" : "rows were"} skipped, so no new version was made.{" "}
                      {result.latest !== null ? `v${result.latest} is still the latest.` : "There is no season data for this pack yet."} Is this file for a different Sport Pack?
                    </span>
                  </>
                )}
                {result.kind === "unreadable" && (
                  <>
                    <h2 className={s.resultTitle}>That file couldn&apos;t be imported.</h2>
                    <span className={s.resultSub}>{result.message} Nothing was imported.</span>
                  </>
                )}
              </div>
              {report && (
                <div className={s.stats}>
                  {(ok
                    ? [
                        ["IMPORTED", String(report.imported), "var(--ok)"],
                        ["SKIPPED", String(report.skipped), report.skipped ? "var(--bad)" : "var(--ink)"],
                        ["VERSION", `v${report.version}`, "var(--ink)"],
                      ]
                    : [
                        ["IMPORTED", "0", "var(--ink)"],
                        ["SKIPPED", String(report.skipped), "var(--bad)"],
                        ["LATEST", result.kind === "failed" && result.latest !== null ? `v${result.latest}` : "none", "var(--ink)"],
                      ]
                  ).map(([k, v, ink]) => (
                    <div key={k} className={s.stat}>
                      <span className={s.statKey}>{k}</span>
                      <span className={s.statValue} style={{ color: ink }}>
                        {v}
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}

        {report && report.rows.length > 0 && (
          <div className={s.card} aria-label="Rejected rows">
            <div className={s.cardHead}>
              <span className={s.label}>REJECTED ROWS</span>
              <span className={s.count}>{report.skipped}</span>
            </div>
            <div className={s.thead}>
              <span style={{ width: 48 }}>ROW</span>
              <span style={{ width: 96 }}>FIELD</span>
              <span style={{ width: 180 }}>VALUE</span>
              <span style={{ flex: 1 }}>PROBLEM</span>
            </div>
            {report.rows.map((r, i) => (
              <div key={`${r.row}-${r.field}-${i}`} className={s.problem} data-testid="rejected-row">
                <span className={s.cRow}>{r.row}</span>
                <span className={s.cField}>{r.field}</span>
                <span className={s.cValue}>{r.value}</span>
                <span className={s.cProblem}>{r.message}</span>
              </div>
            ))}
            {report.moreProblems > 0 && <span className={s.more}>and {report.moreProblems} more like these</span>}
          </div>
        )}

        {!packs && !loadError ? (
          <div className={s.cols} aria-busy="true">
            <div className={s.skeleton} style={{ flex: "3 1 380px", height: 360 }} />
            <div className={s.skeleton} style={{ flex: "2 1 280px", height: 240 }} />
          </div>
        ) : loadError ? (
          <div className={s.result} style={{ background: "var(--bad-tint)" }} role="alert">
            <div className={s.resultBody}>
              <h2 className={s.resultTitle}>Couldn&apos;t load season data.</h2>
              <span className={s.resultSub}>{loadError}</span>
              <div>
                <Button variant="outline" onClick={() => window.location.reload()}>
                  Try again
                </Button>
              </div>
            </div>
          </div>
        ) : (
          <div className={s.cols}>
            <div className={s.form}>
              <span className={s.label}>{label}</span>
              <div className={s.group}>
                <span className={s.fieldLabel}>Sport Pack</span>
                <span className={s.selectWrap}>
                  <span className={s.selectFace} aria-hidden="true">
                    {pack?.icon && <Icon name={pack.icon} size={20} className={s.packIcon} />}
                    <span>{pack?.name}</span>
                    <span className={s.packVersion}>{pack?.version}</span>
                  </span>
                  <select
                    className={s.select}
                    value={packId}
                    aria-label="Sport Pack"
                    onChange={(e) => {
                      setPackId(e.target.value);
                      setVersions(null);
                      setResult(null);
                      void loadVersions(e.target.value);
                    }}
                  >
                    {packs?.map((p) => (
                      <option key={p.id} value={p.id}>
                        {p.name} {p.version}
                      </option>
                    ))}
                  </select>
                  <Icon name="expand_more" size={20} className={s.selectIcon} />
                </span>
              </div>

              <div className={s.group}>
                <span className={s.fieldLabel}>File</span>
                <input
                  ref={input}
                  type="file"
                  accept=".csv,.json,text/csv,application/json"
                  hidden
                  data-testid="season-file"
                  onChange={(e) => {
                    void pick(e.target.files?.[0] ?? null);
                    e.target.value = "";
                  }}
                />
                {file ? (
                  <div className={s.file}>
                    <Icon name="description" size={24} className={s.fileIcon} />
                    <div className={s.fileText}>
                      <span className={s.fileName}>{file.file.name}</span>
                      <span className={s.fileMeta}>{file.meta}</span>
                    </div>
                    <button type="button" className={s.clear} title="Remove file" aria-label="Remove file" onClick={() => setFile(null)}>
                      <Icon name="close" />
                    </button>
                  </div>
                ) : (
                  <button
                    type="button"
                    className={s.drop}
                    data-active={hovering}
                    onClick={() => input.current?.click()}
                    onDragOver={(e) => {
                      e.preventDefault();
                      setHovering(true);
                    }}
                    onDragLeave={() => setHovering(false)}
                    onDrop={onDrop}
                  >
                    <Icon name="upload_file" size={28} />
                    <span className={s.dropTitle}>Drop a CSV or JSON file</span>
                    <span>or choose one</span>
                  </button>
                )}
              </div>

              <div className={s.hint}>
                <span>
                  <b>Columns:</b> <span className={s.mono}>name, country, role, tags, base_price, set, provider_id</span>
                </span>
                {pack && (
                  <span>
                    <b>{pack.name} roles:</b> <span className={s.mono}>{pack.roles.map((r) => r.id).join(" · ")}</span>
                    {pack.tags.length > 0 && (
                      <>
                        {" "}
                        · <b>tags:</b> <span className={s.mono}>{pack.tags.map((t) => t.id).join(" · ")}</span>
                      </>
                    )}
                  </span>
                )}
                <span>Rows that don&apos;t fit the pack are skipped and listed. The rest still import.</span>
              </div>

              <Button variant="brand" size="L" icon="upload" block busy={busy} disabled={!file} onClick={() => void submit()}>
                Import
              </Button>
            </div>

            <div className={s.versions}>
              <div className={s.cardHead} style={{ padding: 10 }}>
                <span className={s.label}>VERSIONS · {packName.toUpperCase()}</span>
              </div>
              {versions && versions.length === 0 && <span className={s.noVersions}>No season data yet. The first import becomes v1.</span>}
              {versions?.map((v) => (
                <div key={v.id} className={s.version} data-testid="version-row">
                  <span className={`${s.versionTile} ${v.version === latestVersion ? s.versionLatest : ""}`}>v{v.version}</span>
                  <div className={s.versionText}>
                    <div className={s.versionLine}>
                      <span>
                        <span className={s.mono}>{v.playerCount}</span> players
                      </span>
                      {v.version === latestVersion && <span className={s.latest}>LATEST</span>}
                    </div>
                    <span className={s.versionSub}>{versionSub(v)}</span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </AppShell>
  );
}
