"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";
import { ApiError, joinRoom, previewJoin, type JoinPreview } from "../../../api/client";
import { explainRoomError } from "../../../rooms/errors";
import { suggestMonogram } from "../../../rooms/format";
import { HUES, teamStyle } from "../../../rooms/hues";

/** Joining a room by invite: pick a name, monogram and colour for the new franchise. */
export function JoinForm({ code }: { code: string }) {
  const router = useRouter();
  const [preview, setPreview] = useState<JoinPreview | null>(null);
  const [name, setName] = useState("");
  const [monogram, setMonogram] = useState("");
  const [monogramTouched, setMonogramTouched] = useState(false);
  const [colour, setColour] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [dead, setDead] = useState(false);
  const [busy, setBusy] = useState(false);

  const [reloads, setReloads] = useState(0);

  useEffect(() => {
    let active = true;
    previewJoin(code)
      .then((next) => {
        if (!active) return;
        if (next.alreadyJoined && next.roomId) {
          router.replace(`/rooms/${next.roomId}`);
          return;
        }
        setPreview(next);
        setColour((current) =>
          current && !next.takenColours.includes(current)
            ? current
            : (HUES.find((h) => !next.takenColours.includes(h.key))?.key ?? ""),
        );
      })
      .catch((e) => {
        if (!active) return;
        setError(explainRoomError(e).message);
        if (e instanceof ApiError && (e.status === 404 || e.status === 429)) setDead(true);
      });
    return () => {
      active = false;
    };
  }, [code, router, reloads]);

  function changeName(value: string) {
    setName(value);
    if (!monogramTouched) setMonogram(suggestMonogram(value));
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const joined = await joinRoom(code, { name, colour, monogram });
      router.push(`/rooms/${joined.roomId}`);
    } catch (e) {
      const explained = explainRoomError(e);
      setError(explained.message);
      if (e instanceof ApiError && e.status === 404) setDead(true);
      else if (explained.refetch) setReloads((n) => n + 1);
      setBusy(false);
    }
  }

  if (dead) {
    return (
      <section className="card">
        <h1>Join a room</h1>
        <p aria-live="assertive" className="alert">
          {error}
        </p>
        <Link className="btn" href="/profile">
          Back to my profile
        </Link>
      </section>
    );
  }

  if (!preview) {
    return (
      <section className="card">
        <h1>Join a room</h1>
        <p aria-live="assertive" className="alert">
          {error}
        </p>
        {!error && <p>Checking your invite.</p>}
      </section>
    );
  }

  return (
    <form className="card" onSubmit={submit}>
      <h1>Join {preview.roomName}</h1>
      <p>Name your franchise. Other managers will see it in the room.</p>
      <p aria-live="assertive" className="alert">
        {error}
      </p>
      {preview.full && <p className="alert">This room is full.</p>}
      <label className="field">
        <span>Franchise name</span>
        <input value={name} maxLength={30} required onChange={(e) => changeName(e.target.value)} />
      </label>
      <label className="field">
        <span>Monogram (1 to 3 letters)</span>
        <input
          className="num"
          value={monogram}
          maxLength={3}
          required
          onChange={(e) => {
            setMonogramTouched(true);
            setMonogram(e.target.value);
          }}
        />
      </label>
      <div className="field">
        <span id="colour-label">Colour</span>
        <div className="hue-grid" role="group" aria-labelledby="colour-label">
          {HUES.map((h) => {
            const taken = preview.takenColours.includes(h.key);
            return (
              <button
                key={h.key}
                type="button"
                className="hue-option team"
                style={teamStyle(h.key)}
                aria-label={`${h.label}${taken ? " (taken)" : ""}`}
                aria-pressed={colour === h.key}
                disabled={taken}
                onClick={() => setColour(h.key)}
              />
            );
          })}
        </div>
      </div>
      <button
        type="submit"
        className="btn btn-primary"
        disabled={busy || preview.full || !colour || name.trim().length < 2 || monogram.trim().length < 1}
      >
        Join room
      </button>
    </form>
  );
}
