"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";
import {
  ApiError,
  getAvatars,
  getMe,
  signOut,
  updateProfile,
  type Profile,
} from "../../api/client";
import { avatarSrc } from "./avatar";

export function ProfileView() {
  const router = useRouter();
  const [profile, setProfile] = useState<Profile | null>(null);
  const [presets, setPresets] = useState<string[]>([]);
  const [name, setName] = useState("");
  const [avatar, setAvatar] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let active = true;
    (async () => {
      const [me, avatars] = await Promise.all([getMe(), getAvatars().catch(() => null)]);
      if (!active) return;
      if (!me) {
        router.push("/sign-in");
        return;
      }
      setProfile(me);
      setName(me.displayName);
      setAvatar(me.avatar);
      setPresets(avatars?.presets ?? []);
    })().catch(() => active && setError("Could not load your profile. Reload to try again."));
    return () => {
      active = false;
    };
  }, [router]);

  async function save(event: FormEvent) {
    event.preventDefault();
    if (!profile) return;
    setBusy(true);
    setError(null);
    setSaved(false);
    try {
      const updated = await updateProfile({ displayName: name, avatar });
      setProfile(updated);
      setName(updated.displayName);
      setAvatar(updated.avatar);
      setSaved(true);
    } catch (e) {
      setError(
        e instanceof ApiError && e.status === 400
          ? "Names are 2 to 30 characters, and avatars must come from the list."
          : "Could not save. Try again.",
      );
    } finally {
      setBusy(false);
    }
  }

  async function leave() {
    await signOut();
    router.push("/sign-in");
  }

  if (!profile) {
    return (
      <section className="panel">
        <p aria-live="assertive" className="alert">
          {error}
        </p>
        {!error && <p>Loading your profile.</p>}
      </section>
    );
  }

  const choices = [
    ...presets.map((id, i) => ({ value: id, label: `Avatar ${i + 1}` })),
    ...(profile.googlePictureUrl
      ? [{ value: profile.googlePictureUrl, label: "Google picture" }]
      : []),
  ];

  return (
    <section className="panel">
      <div className="identity">
        {/* eslint-disable-next-line @next/next/no-img-element -- small bundled svg or Google url */}
        <img className="avatar-large" src={avatarSrc(profile.avatar)} alt="Your avatar" />
        <div>
          <h1>{profile.displayName}</h1>
          <p>{profile.email}</p>
        </div>
      </div>

      <form onSubmit={save} className="stack" noValidate>
        <label className="field">
          <span>Display name</span>
          <input
            value={name}
            maxLength={30}
            required
            onChange={(e) => setName(e.target.value)}
          />
        </label>

        <div className="field">
          <span>Avatar</span>
          <div className="avatar-grid">
            {choices.map((choice) => (
              <button
                key={choice.value}
                type="button"
                className="avatar-option"
                aria-label={choice.label}
                aria-pressed={avatar === choice.value}
                onClick={() => setAvatar(choice.value)}
              >
                {/* eslint-disable-next-line @next/next/no-img-element -- small bundled svg or Google url */}
                <img src={avatarSrc(choice.value)} alt="" />
              </button>
            ))}
          </div>
        </div>

        <p aria-live="assertive" className="alert">
          {error}
        </p>
        <output className="notice">{saved ? "Saved." : ""}</output>
        <button type="submit" className="btn btn-primary" disabled={busy}>
          Save
        </button>
      </form>

      <button type="button" className="btn btn-quiet" onClick={leave}>
        Sign out
      </button>
    </section>
  );
}
