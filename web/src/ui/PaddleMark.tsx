/** The paddle mark from Design System 01: one circle and one handle, tilted -16 degrees. */
export function PaddleMark({ className, size = 28 }: { className?: string; size?: number }) {
  return (
    <svg className={className} width={size} height={size} viewBox="0 0 64 64" aria-hidden="true">
      <g transform="rotate(-16 32 32)" fill="currentColor">
        <rect x="28" y="35" width="8" height="24" rx="4" />
        <circle cx="32" cy="23" r="18" />
      </g>
    </svg>
  );
}
