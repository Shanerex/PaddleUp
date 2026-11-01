#!/usr/bin/env bash
# Fails if core code names a sport, role, stat or currency (CLAUDE.md invariant).
# Scans server/src/main and web/src, tests excluded. Word-boundary, case-insensitive.
set -u
cd "$(dirname "$0")/.."

WORDS='sport|sports|role|roles|stat|stats|currency|currencies|cricket|batter|batsman|bowler|wicket|over|overs|run|runs|all-rounder|keeper|t20|football|soccer|goal|goals|inr|usd|rupee|rupees|crore|lakh|dollar|dollars'
SYMBOLS='₹'
ROOTS=(server/src/main web/src)

found=0
for root in "${ROOTS[@]}"; do
  [ -d "$root" ] || continue
  hits=$(grep -rniE -w "$WORDS" "$root" \
    --exclude='*.test.*' --exclude-dir=test --exclude-dir=node_modules --exclude-dir=.next \
    --exclude='*.png' --exclude='*.ico' 2>/dev/null || true)
  # Allowlist: the framework entry point SpringApplication.run( is not a domain term.
  hits=$(printf '%s\n' "$hits" | sed 's/SpringApplication\.run(//' | grep -iE -w "$WORDS" || true)
  sym=$(grep -rn "$SYMBOLS" "$root" --exclude='*.test.*' --exclude-dir=test 2>/dev/null || true)
  if [ -n "$hits$sym" ]; then
    printf '%s\n%s\n' "$hits" "$sym" | sed '/^$/d'
    found=1
  fi
done

if [ "$found" -ne 0 ]; then
  echo "check-agnostic: forbidden sport/role/stat/currency terms found" >&2
  exit 1
fi
echo "check-agnostic: ok"
