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
import { BackLink } from "../../ui/AppShell";
import { Button } from "../../ui/Button";
import { Icon } from "../../ui/Icon";
import { avatarSrc } from "./avatar";
import s from "./profile.module.css";

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
      <div className={s.page}>
        <p aria-live="assertive" className={s.error}>
          {error}
        </p>
        {!error && <p className={s.helper}>Loading your profile.</p>}
      </div>
    );
  }

  const choices = [
    ...presets.map((id, i) => ({ value: id, label: `Avatar ${i + 1}` })),
    ...(profile.googlePictureUrl ? [{ value: profile.googlePictureUrl, label: "Google picture" }] : []),
  ];

  return (
    <div className={s.page}>
      <BackLink />
      <h1 className={s.title}>Your profile</h1>
      <div className={s.cols}>
        <section className={`${s.card} ${s.identity}`} aria-label="Your account">
          {/* eslint-disable-next-line @next/next/no-img-element -- small bundled svg or Google url */}
          <img className={s.avatarLarge} src={avatarSrc(profile.avatar)} alt="Your avatar" />
          <div className={s.who}>
            <h2 className={s.name}>{profile.displayName}</h2>
            <span className={s.email}>{profile.email}</span>
          </div>
          <span className={s.note}>Your name shows next to your franchise in every room you&apos;re in.</span>
        </section>

        <form onSubmit={save} className={`${s.card} ${s.form}`} noValidate>
          <label className={s.label}>
            Display name
            <span className={s.input}>
              <input value={name} maxLength={30} required onChange={(e) => setName(e.target.value)} />
              <span className={s.count} aria-hidden="true">
                {name.length} / 30
              </span>
            </span>
            <span className={s.helper}>2–30 characters.</span>
          </label>

          <div className={s.avatars}>
            <span className={s.label}>Avatar</span>
            <div className={s.grid}>
              {choices.map((choice) => (
                <button
                  key={choice.value}
                  type="button"
                  className={s.avatar}
                  aria-label={choice.label}
                  aria-pressed={avatar === choice.value}
                  onClick={() => setAvatar(choice.value)}
                >
                  {/* eslint-disable-next-line @next/next/no-img-element -- small bundled svg or Google url */}
                  <img src={avatarSrc(choice.value)} alt="" />
                </button>
              ))}
            </div>
            {profile.googlePictureUrl && <span className={s.helper}>The last one is your Google picture.</span>}
          </div>

          <p aria-live="assertive" className={s.error}>
            {error}
          </p>
          <div className={s.actions}>
            <Button type="submit" variant="brand" size="M48" busy={busy}>
              Save
            </Button>
            <output className={s.saved}>
              {saved && (
                <>
                  <Icon name="check_circle" size={18} />
                  Saved.
                </>
              )}
            </output>
          </div>
        </form>
      </div>

      <section className={s.signOut} aria-label="Sign out">
        <div className={s.signOutText}>
          <span className={s.signOutTitle}>Sign out</span>
          <span className={s.helper}>You stay in your rooms. Sign back in with Google or a magic link.</span>
        </div>
        <Button variant="outline" size="M48" icon="logout" onClick={leave} style={{ color: "var(--bad)" }}>
          Sign out
        </Button>
      </section>
    </div>
  );
}
