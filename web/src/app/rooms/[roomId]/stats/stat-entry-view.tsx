"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import {
  ApiError,
  clearStatFields,
  getMembers,
  getRoom,
  getStatMatchPlayers,
  getStatMatches,
  getStatSheet,
  saveStatSheet,
  type MatchPlayers,
  type Member,
  type Room,
  type SheetResult,
  type StatMatch,
  type StatSheet,
} from "../../../../api/client";
import { explainRoomError } from "../../../../rooms/errors";
import { hueOf } from "../../../../rooms/hues";
import { isPreAuction } from "../../../../rooms/state";
import { buildSave, EMPTY_SAVE, fixBanner, matchTitle, overriddenFields, refusalOf, savedLine, type Draft } from "../../../../rooms/stat-sheet";
import { Toast } from "../../../../ui/Sheet";
import { RoomFrame } from "../room-frame";
import { ClearConfirm } from "./clear-confirm";
import { MatchList } from "./match-list";
import { PlayerList } from "./player-list";
import { SheetForm } from "./sheet-form";
import s from "./stats.module.css";

const BRAND_HUE = 38;

/** What to tell the person about a refused stat entry request. */
function explainStat(e: unknown): { message: string; refetch: boolean } {
  if (e instanceof ApiError) {
    const code = e.body && typeof e.body === "object" ? (e.body as { error?: unknown }).error : null;
    if (code === "match_not_started") return { message: "That match has not started yet, so nothing can be entered.", refetch: true };
    if (code === "unknown_match" || code === "unknown_player") return { message: "That match or player is no longer in this room. The list was refreshed.", refetch: true };
  }
  return explainRoomError(e);
}

/**
 * Manual stat entry (Room `statEntry`, App Screens 9f-9j). The Commissioner picks a started match,
 * a player, fills the pack's stat fields and saves; the server validates every field, writes the
 * overrides and recomputes in one step, and answers with the new points and the new sheet. Typed
 * values stay when a sheet is refused. A tab that comes back to the front reads everything again.
 */
export function StatEntryView({ roomId }: { roomId: string }) {
  const [room, setRoom] = useState<Room | null>(null);
  const [members, setMembers] = useState<Member[]>([]);
  const [matches, setMatches] = useState<StatMatch[] | null>(null);
  const [matchId, setMatchId] = useState<string | null>(null);
  const [players, setPlayers] = useState<MatchPlayers | null>(null);
  const [playerId, setPlayerId] = useState<string | null>(null);
  const [sheet, setSheet] = useState<StatSheet | null>(null);
  const [draft, setDraft] = useState<Draft>({});
  const [hints, setHints] = useState<Record<string, string>>({});
  const [banner, setBanner] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [toast, setToast] = useState<{ title: string; body: string } | null>(null);
  const [clearing, setClearing] = useState(false);
  const [clearError, setClearError] = useState<string | null>(null);
  const [missing, setMissing] = useState(false);
  const [forbidden, setForbidden] = useState(false);
  const [failed, setFailed] = useState<string | null>(null);
  const reads = useRef({ matches: 0, players: 0, sheet: 0 });

  const commish = room?.myRole === "commissioner";

  const fail = useCallback((e: unknown) => {
    if (e instanceof ApiError && e.status === 404) setMissing(true);
    else if (e instanceof ApiError && e.status === 403) setForbidden(true);
    else setFailed(explainRoomError(e).message);
  }, []);

  /** The latest read of each kind wins, so a slow earlier answer never overwrites a newer one. */
  const readMatches = useCallback(
    (): Promise<StatMatch[] | null> => {
      const mine = ++reads.current.matches;
      return getStatMatches(roomId)
        .then((list) => {
          if (mine !== reads.current.matches) return null;
          setMatches(list);
          setFailed(null);
          return list;
        })
        .catch((e) => {
          if (mine === reads.current.matches) fail(e);
          return null;
        });
    },
    [roomId, fail],
  );

  const readPlayers = useCallback(
    (id: string): Promise<MatchPlayers | null> => {
      const mine = ++reads.current.players;
      return getStatMatchPlayers(roomId, id)
        .then((view) => {
          if (mine !== reads.current.players) return null;
          setPlayers(view);
          return view;
        })
        .catch((e) => {
          if (mine === reads.current.players) fail(e);
          return null;
        });
    },
    [roomId, fail],
  );

  const readSheet = useCallback(
    (id: string, player: string): Promise<StatSheet | null> => {
      const mine = ++reads.current.sheet;
      return getStatSheet(roomId, id, player)
        .then((next) => {
          if (mine !== reads.current.sheet) return null;
          setSheet(next);
          return next;
        })
        .catch((e) => {
          if (mine === reads.current.sheet) fail(e);
          return null;
        });
    },
    [roomId, fail],
  );

  // The room first; only the Commissioner reads the stat routes.
  useEffect(() => {
    let stopped = false;
    Promise.all([getRoom(roomId), getMembers(roomId)])
      .then(([r, m]) => {
        if (stopped) return;
        setRoom(r);
        setMembers(m);
        if (r.myRole === "commissioner") void readMatches();
      })
      .catch((e) => !stopped && fail(e));
    return () => {
      stopped = true;
    };
  }, [roomId, readMatches, fail]);

  // The open match: the one picked, else the newest that has started.
  const activeMatch = matchId ?? (matches ? ([...matches].reverse().find((m) => m.chip !== "UPCOMING")?.matchId ?? null) : null);

  // A match opens its players; the first one (players with a value come first) opens its sheet.
  useEffect(() => {
    if (activeMatch === null) return;
    void readPlayers(activeMatch).then((view) => {
      if (view && view.players.length > 0) setPlayerId((current) => current ?? view.players[0].poolPlayerId);
    });
  }, [activeMatch, readPlayers]);

  useEffect(() => {
    if (activeMatch === null || playerId === null) return;
    void readSheet(activeMatch, playerId);
  }, [activeMatch, playerId, readSheet]);

  function resetForm() {
    setDraft({});
    setHints({});
    setBanner(null);
  }

  function selectMatch(id: string) {
    if (id === activeMatch) return;
    setMatchId(id);
    setPlayers(null);
    setSheet(null);
    setPlayerId(null);
    resetForm();
  }

  function selectPlayer(id: string) {
    if (id === playerId) return;
    setPlayerId(id);
    setSheet(null);
    resetForm();
  }

  // A tab that returns to the front may be stale: the provider can have delivered, or someone else saved.
  useEffect(() => {
    if (!commish) return;
    const onVisible = () => {
      if (document.visibilityState !== "visible") return;
      void readMatches();
      if (activeMatch) void readPlayers(activeMatch);
      if (activeMatch && playerId) void readSheet(activeMatch, playerId);
    };
    document.addEventListener("visibilitychange", onVisible);
    return () => document.removeEventListener("visibilitychange", onVisible);
  }, [commish, activeMatch, playerId, readMatches, readPlayers, readSheet]);

  const mine = members.find((m) => m.franchise && m.franchise.id === room?.myFranchiseId)?.franchise;

  /** Shows what a save or clear answered with, and refreshes the lists behind it. */
  function applied(result: SheetResult, cleared: boolean) {
    setSheet(result.sheet);
    setDraft({});
    setHints({});
    setBanner(null);
    const owner = result.franchise && result.sheet.owner ? { name: result.sheet.owner.name, delta: result.franchise.delta } : null;
    const title = matchTitle({ n: result.sheet.match.n, label: null });
    setToast(
      result.changedFields === 0
        ? { title: "Nothing changed", body: `${result.sheet.name} already had those values.` }
        : { title: `${cleared ? "Cleared" : "Saved"} · ${title} recomputed`, body: savedLine(result.sheet.name, result.before, result.after, owner) },
    );
    void readMatches();
    if (activeMatch) void readPlayers(activeMatch);
  }

  function refused(e: unknown) {
    const refusal = sheet && e instanceof ApiError && e.status === 422 ? refusalOf(sheet, e.body) : null;
    if (refusal) {
      setHints(refusal.hints);
      setBanner(refusal.banner);
      return;
    }
    const why = explainStat(e);
    setHints({});
    setBanner(why.message);
    if (why.refetch) {
      void readMatches();
      if (activeMatch) void readPlayers(activeMatch);
    }
  }

  function save() {
    if (!sheet || !activeMatch || !playerId || busy) return;
    const built = buildSave(sheet, draft);
    if (!built.ok) {
      if (built.reason === "empty") {
        setHints({});
        setBanner(EMPTY_SAVE);
      } else {
        setHints(built.hints);
        setBanner(fixBanner(Object.keys(built.hints).length));
      }
      return;
    }
    setBusy(true);
    saveStatSheet(roomId, activeMatch, playerId, built.body.fields)
      .then((result) => applied(result, false))
      .catch(refused)
      .finally(() => setBusy(false));
  }

  function clear() {
    if (!sheet || !activeMatch || !playerId || busy) return;
    setBusy(true);
    setClearError(null);
    clearStatFields(roomId, activeMatch, playerId, overriddenFields(sheet))
      .then((result) => {
        setClearing(false);
        applied(result, true);
      })
      .catch((e) => setClearError(explainStat(e).message))
      .finally(() => setBusy(false));
  }

  const sub = !room ? "" : mine ? `${mine.name} · Commissioner` : "Commissioner · no franchise";
  const state = missing ? (
    <div className={s.state} role="alert">
      <span className={s.stateTitle}>Can&apos;t find that room</span>
      <span className={s.stateBody}>It doesn&apos;t exist, or you&apos;re not in it.</span>
    </div>
  ) : forbidden || (room && !commish) ? (
    <div className={s.state} role="alert">
      <span className={s.stateTitle}>Only the Commissioner enters stats</span>
      <span className={s.stateBody}>Ask the Commissioner to correct a match for you.</span>
    </div>
  ) : failed ? (
    <div className={s.state} role="alert">
      <span className={s.stateTitle}>Couldn&apos;t load stat entry</span>
      <span className={s.stateBody}>{failed}</span>
    </div>
  ) : matches !== null && matches.length === 0 ? (
    <div className={s.state}>
      <span className={s.stateTitle}>No matches yet</span>
      <span className={s.stateBody}>This room&apos;s season data has no matches to enter stats for.</span>
    </div>
  ) : matches !== null && activeMatch === null ? (
    <div className={s.state}>
      <span className={s.stateTitle}>No match has started</span>
      <span className={s.stateBody}>Stats can be entered once a match has started.</span>
    </div>
  ) : null;

  return (
    <RoomFrame
      roomId={roomId}
      hue={mine ? hueOf(mine.colour) : BRAND_HUE}
      roomName={room?.config.name ?? ""}
      state={room?.state}
      sub={missing ? "" : `${sub}${sub ? " · " : ""}Manual stat entry`}
      mine={mine}
      loaded={!!room}
      commish={!!commish}
      pre={room ? isPreAuction(room.state) : false}
      active="room"
      overlays={
        <>
          {clearing && sheet && <ClearConfirm sheet={sheet} busy={busy} error={clearError} onConfirm={clear} onClose={() => setClearing(false)} />}
          {toast && <Toast title={toast.title} body={toast.body} onDone={() => setToast(null)} />}
        </>
      }
    >
      <div className={s.page} data-testid="stat-entry">
        <div>
          <h2 className={s.title}>Manual stat entry</h2>
          <p className={s.lede}>Manual entries beat provider data and survive every re-import. Points recompute when you save.</p>
        </div>
        {state ?? (
          <div className={s.grid} aria-busy={matches === null}>
            {matches && <MatchList matches={matches} selected={activeMatch} onSelect={selectMatch} />}
            <div className={s.mainCol}>
              {players && room && <PlayerList view={players} labels={room.packLabels} selected={playerId} onSelect={selectPlayer} />}
              {sheet && room && (
                <SheetForm
                  sheet={sheet}
                  labels={room.packLabels}
                  draft={draft}
                  hints={hints}
                  banner={banner}
                  busy={busy}
                  onChange={(key, value) => {
                    setDraft((d) => ({ ...d, [key]: value }));
                    setHints((h) => {
                      if (!(key in h)) return h;
                      const rest = { ...h };
                      delete rest[key];
                      return rest;
                    });
                  }}
                  onSave={save}
                  onClear={() => {
                    setClearError(null);
                    setClearing(true);
                  }}
                />
              )}
            </div>
          </div>
        )}
      </div>
    </RoomFrame>
  );
}
