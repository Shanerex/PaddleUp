"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { formatCode, normaliseCode } from "../../rooms/codes";
import { Button } from "../../ui/Button";
import { BackLink, shell } from "../../ui/AppShell";
import { Icon } from "../../ui/Icon";
import { Field } from "../../ui/kit";
import s from "./join.module.css";

/** Home `joinCode`: paste a code, then continue to the join link it stands for. */
export function JoinCode() {
  const router = useRouter();
  const [raw, setRaw] = useState("");
  const [error, setError] = useState<string | null>(null);

  function submit(event: FormEvent) {
    event.preventDefault();
    const code = normaliseCode(raw);
    if (code.length < 10) {
      setError("That's too short. Codes have 12 letters and numbers.");
      return;
    }
    router.push(`/join/${code}`);
  }

  return (
    <form className={shell.narrow} onSubmit={submit} noValidate>
      <BackLink />
      <span className={s.tile} style={{ background: "var(--brand-tint)", color: "var(--brand-ink)" }}>
        <Icon name="key" size={28} />
      </span>
      <div className={s.titles}>
        <h1 className={s.title}>Join a room</h1>
        <p className={shell.lede}>Paste the invite code a friend sent you. Codes work for 7 days.</p>
      </div>
      <Field
        label="Invite code"
        mono
        value={raw}
        autoComplete="off"
        autoCapitalize="characters"
        spellCheck={false}
        placeholder="XXXX-XXXX-XXXX"
        error={error}
        helper="12 letters and numbers. Dashes and spaces don't matter."
        onChange={(e) => {
          setError(null);
          setRaw(e.target.value);
        }}
        onBlur={() => raw && setRaw(formatCode(raw))}
      />
      <Button type="submit" variant="brand" size="L">
        Continue
      </Button>
    </form>
  );
}
