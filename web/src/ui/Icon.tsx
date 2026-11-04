import type { CSSProperties } from "react";

/** A Material Symbols Rounded glyph (docs/design/README.md). Decorative unless `label` is set. */
export function Icon({
  name,
  size = 20,
  fill = false,
  label,
  className,
  style,
}: {
  name: string;
  size?: number;
  fill?: boolean;
  label?: string;
  className?: string;
  style?: CSSProperties;
}) {
  return (
    <span
      className={className ? `ms ${className}` : "ms"}
      data-fill={fill ? "1" : undefined}
      style={{ fontSize: size, ...style }}
      aria-hidden={label ? undefined : true}
      role={label ? "img" : undefined}
      aria-label={label}
    >
      {name}
    </span>
  );
}
