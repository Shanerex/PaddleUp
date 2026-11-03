#!/usr/bin/env bash
# Self-test for check-agnostic.sh: a specific name must fail, generic concept words must pass.
set -u
here="$(cd "$(dirname "$0")" && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

mkdir -p "$tmp/bad" "$tmp/good"
echo 'int wicket = 0;' >"$tmp/bad/Bad.java"
printf 'List<String> roles;\nString currencyCode;\nString stat;\nlong sports;\n' >"$tmp/good/Good.java"

fail=0
if "$here/check-agnostic.sh" "$tmp/bad" >/dev/null 2>&1; then
  echo "test-check-agnostic: FAIL, 'wicket' was not rejected" >&2
  fail=1
fi
if ! "$here/check-agnostic.sh" "$tmp/good" >/dev/null 2>&1; then
  echo "test-check-agnostic: FAIL, generic words were rejected" >&2
  fail=1
fi
[ "$fail" -eq 0 ] && echo "test-check-agnostic: ok"
exit "$fail"
