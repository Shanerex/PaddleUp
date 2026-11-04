import Link from "next/link";
import type { ButtonHTMLAttributes, ReactNode } from "react";
import s from "./Button.module.css";
import { Icon } from "./Icon";

export type ButtonVariant =
  | "brand"
  | "brandSoft"
  | "primary"
  | "secondary"
  | "outline"
  | "ghost"
  | "ghostBad"
  | "danger"
  | "onTeam"
  | "glass"
  | "onBrand"
  | "glassBrand";

type Common = {
  variant?: ButtonVariant;
  size?: "L" | "M48" | "M" | "S";
  icon?: string;
  block?: boolean;
  busy?: boolean;
  children: ReactNode;
  className?: string;
};

function classes({ variant = "outline", size = "M", block, busy, className }: Common) {
  return [s.btn, s[variant], size === "M" ? "" : s[size], block ? s.block : "", busy ? s.busy : "", className ?? ""]
    .filter(Boolean)
    .join(" ");
}

/** Design System 07 button. While `busy` it shows the "progress_activity" spinner and is disabled. */
export function Button({
  variant,
  size,
  icon,
  block,
  busy,
  children,
  className,
  disabled,
  type = "button",
  ...rest
}: Common & ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button
      type={type}
      className={classes({ variant, size, block, busy, className, children })}
      disabled={disabled || busy}
      aria-busy={busy || undefined}
      {...rest}
    >
      {busy ? <Icon name="progress_activity" className={s.spin} /> : icon && <Icon name={icon} />}
      {children}
    </button>
  );
}

/** A link styled as a Design System button. */
export function ButtonLink({
  href,
  variant,
  size,
  icon,
  block,
  children,
  className,
}: Common & { href: string }) {
  return (
    <Link href={href} className={classes({ variant, size, block, className, children })}>
      {icon && <Icon name={icon} />}
      {children}
    </Link>
  );
}
