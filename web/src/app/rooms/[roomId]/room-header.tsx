"use client";

import { useState } from "react";
import {
  advanceRoom,
  handOver,
  issueInvite,
  removeFranchise,
  revokeInvite,
  type Invite,
  type Member,
  type Room,
  type RoomState,
} from "../../../api/client";
import { explainRoomError } from "../../../rooms/errors";
import { hueLabel, teamStyle } from "../../../rooms/hues";
import { isPreAuction, STATE_LABELS } from "../../../rooms/state";

interface Props {
  room: Room;
  members: Member[];
  invite: Invite | null;
  /** Called after any change, and when an action found the screen stale; reloads everything. */
  onChanged: () => void;
  onError: (message: string) => void;
}

/** The room's name and state for every member, plus the Commissioner's controls. */
export function RoomHeader({ room, members, invite, onChanged, onError }: Props) {
  const [busy, setBusy] = useState(false);
  const [confirm, setConfirm] = useState<{ kind: "remove" | "handover"; franchiseId: string } | null>(
    null,
  );
  const commissioner = room.myRole === "commissioner";

  async function act(action: () => Promise<unknown>) {
    setBusy(true);
    setConfirm(null);
    try {
      await action();
      onChanged();
    } catch (e) {
      const explained = explainRoomError(e);
      onError(explained.message);
      if (explained.refetch) onChanged();
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <header className="room-header">
        <div className="row between">
          <div>
            <h1>{room.config.name}</h1>
            {room.config.competition && <p>{room.config.competition}</p>}
          </div>
          <span
            className={`badge ${room.state === "auction_live" || room.state === "season_live" ? "badge-live" : ""}`}
            data-testid="room-state"
          >
            {STATE_LABELS[room.state]}
          </span>
        </div>
        <p>
          {commissioner ? "You are the Commissioner." : `Commissioner: ${room.commissioner.displayName}.`}{" "}
          <span className="num">
            {room.franchiseCount} of {room.config.maxManagers}
          </span>{" "}
          franchises (at least {room.config.minManagers} to start).
        </p>

        {commissioner && (
          <div className="stack" aria-label="Commissioner controls">
            {room.advanceTo.length > 0 && (
              <div className="row">
                {room.advanceTo.map((to: RoomState) => (
                  <button
                    key={to}
                    type="button"
                    className="btn btn-primary"
                    disabled={busy}
                    onClick={() => void act(() => advanceRoom(room.id, to))}
                  >
                    Move to {STATE_LABELS[to]}
                  </button>
                ))}
              </div>
            )}
            {isPreAuction(room.state) && (
              <div className="code-box">
                <span className="helper">Invite</span>
                {invite ? (
                  <>
                    <span className="code" data-testid="room-invite-code">
                      {invite.code}
                    </span>
                    <span className="link">{invite.link}</span>
                    <span className="helper">
                      Works until{" "}
                      <time dateTime={invite.expiresAt}>
                        {new Date(invite.expiresAt).toLocaleString()}
                      </time>
                    </span>
                  </>
                ) : (
                  <span className="helper">There is no active invite.</span>
                )}
                <div className="row">
                  <button
                    type="button"
                    className="btn"
                    disabled={busy}
                    onClick={() => void act(() => issueInvite(room.id))}
                  >
                    {invite ? "New code" : "Create invite"}
                  </button>
                  {invite && (
                    <button
                      type="button"
                      className="btn btn-danger"
                      disabled={busy}
                      onClick={() => void act(() => revokeInvite(room.id))}
                    >
                      Revoke
                    </button>
                  )}
                </div>
              </div>
            )}
          </div>
        )}
      </header>

      <section className="card" aria-label="Members">
        <h2>Members</h2>
        <ul className="members">
          {members.map((m) => {
            const franchise = m.franchise;
            const mine = franchise?.id === room.myFranchiseId;
            return (
              <li key={m.userId} className={franchise ? "team" : undefined} style={franchise ? teamStyle(franchise.colour) : undefined}>
                {franchise ? (
                  <span className="monogram" title={hueLabel(franchise.colour)}>
                    {franchise.monogram}
                  </span>
                ) : null}
                <span className="grow">
                  <strong>{franchise ? franchise.name : m.displayName}</strong>
                  <br />
                  <span className="helper">
                    {m.displayName}
                    {m.role === "commissioner" ? " (Commissioner)" : ""}
                    {mine ? " (you)" : ""}
                    {!franchise ? ", admin only" : ""}
                  </span>
                </span>
                {commissioner && franchise && m.role !== "commissioner" && (
                  <span className="row">
                    {confirm?.franchiseId === franchise.id ? (
                      <>
                        <button
                          type="button"
                          className="btn btn-danger"
                          disabled={busy}
                          onClick={() =>
                            void act(() =>
                              confirm.kind === "remove"
                                ? removeFranchise(room.id, franchise.id)
                                : handOver(room.id, franchise.id),
                            )
                          }
                        >
                          Confirm {confirm.kind === "remove" ? "remove" : "handover"}
                        </button>
                        <button type="button" className="btn btn-quiet" onClick={() => setConfirm(null)}>
                          Cancel
                        </button>
                      </>
                    ) : (
                      <>
                        {isPreAuction(room.state) && (
                          <button
                            type="button"
                            className="btn"
                            onClick={() => setConfirm({ kind: "remove", franchiseId: franchise.id })}
                          >
                            Remove {franchise.name}
                          </button>
                        )}
                        {room.state !== "completed" && (
                          <button
                            type="button"
                            className="btn"
                            onClick={() => setConfirm({ kind: "handover", franchiseId: franchise.id })}
                          >
                            Make {franchise.name} Commissioner
                          </button>
                        )}
                      </>
                    )}
                  </span>
                )}
              </li>
            );
          })}
        </ul>
      </section>
    </>
  );
}
