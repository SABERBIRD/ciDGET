#!/usr/bin/env bash
# Checks what a fine-grained GitHub token can read (RESEARCH.md, V1-V7).
# The token is read from a hidden prompt; it is never printed or saved.
# Output contains only status codes and a few response headers, safe to share.
#
# Usage: bash tools/verify-token.sh [owner/private-repo] [owner/repo-without-access] [owner/big-public-repo]
set -uo pipefail

REPO="${1:-SABERBIRD/testING}"
NOACCESS="${2:-SABERBIRD/bagling}"
BIG="${3:-microsoft/vscode}"
API="https://api.github.com"

read -rsp "Paste token (input hidden): " TOKEN; echo
[ -n "$TOKEN" ] || { echo "no token"; exit 1; }

req() { # $1 = label, $2 = path, $3.. = extra curl args
  local label="$1" path="$2"; shift 2
  local hdr; hdr="$(mktemp)"
  local code
  code=$(curl -sS -o /dev/null -D "$hdr" -w '%{http_code}' \
    -H "Authorization: Bearer $TOKEN" \
    -H "Accept: application/vnd.github+json" \
    -H "X-GitHub-Api-Version: 2022-11-28" \
    -H "User-Agent: ciDGET-verify" "$@" "$API$path")
  printf '%-34s %s\n' "$label" "$code"
  grep -iE '^(etag|x-ratelimit-remaining|x-accepted-github-permissions|github-authentication-token-expiration|link):' "$hdr" \
    | sed 's/^/    /' | tr -d '\r'
  rm -f "$hdr"
}

count() { # $1 = label, $2 = path, $3 = jq-less grep pattern
  local n
  n=$(curl -sS -H "Authorization: Bearer $TOKEN" -H "Accept: application/vnd.github+json" \
      -H "User-Agent: ciDGET-verify" "$API$2" | grep -c "$3")
  printf '%-34s %s\n' "$1" "$n"
}

echo "== V1 runs of private repo $REPO"
req "GET actions/runs" "/repos/$REPO/actions/runs?per_page=5"
count "  runs returned (<=5)" "/repos/$REPO/actions/runs?per_page=5" '"run_number"'

echo "== V2 branch list of $REPO"
req "GET branches" "/repos/$REPO/branches?per_page=100"
count "  branches returned" "/repos/$REPO/branches?per_page=100" '"protected"'

echo "== V3 repos visible to the token (names only)"
curl -sS -H "Authorization: Bearer $TOKEN" -H "Accept: application/vnd.github+json" \
  -H "User-Agent: ciDGET-verify" "$API/user/repos?per_page=100&sort=pushed" \
  | grep -oE '"full_name": *"[^"]*"' | sed -E 's/"full_name": *//; s/^/    /'

echo "== V4 token expiry header (see the line above under V1)"

echo "== V5 ETag: same request twice, 2nd should be 304 and not reduce remaining"
ETAG=$(curl -sS -o /dev/null -D - -H "Authorization: Bearer $TOKEN" -H "User-Agent: ciDGET-verify" \
  -H "Accept: application/vnd.github+json" -H "X-GitHub-Api-Version: 2022-11-28" \
  "$API/repos/$REPO/actions/runs?per_page=5" | grep -i '^etag:' | sed -E 's/^[Ee][Tt][Aa][Gg]: *//' | tr -d '\r')
req "GET runs (no If-None-Match)" "/repos/$REPO/actions/runs?per_page=5"
req "GET runs (If-None-Match)" "/repos/$REPO/actions/runs?per_page=5" -H "If-None-Match: $ETAG"
req "GET runs (If-None-Match) again" "/repos/$REPO/actions/runs?per_page=5" -H "If-None-Match: $ETAG"

echo "== V6 errors"
req "private repo without access" "/repos/$NOACCESS/actions/runs?per_page=1"
printf '%-34s %s\n' "invalid token" "$(curl -sS -o /dev/null -w '%{http_code}' \
  -H "Authorization: Bearer github_pat_invalid" -H "User-Agent: ciDGET-verify" "$API/repos/$REPO")"

echo "== V7 big public repo $BIG"
req "GET runs page 1" "/repos/$BIG/actions/runs?per_page=100&exclude_pull_requests=true"
req "GET branches page 1" "/repos/$BIG/branches?per_page=100"

unset TOKEN
echo "done"
