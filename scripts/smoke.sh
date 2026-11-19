#!/usr/bin/env bash
#
# End-to-end REST smoke test for the Stark Group VMS API.
# Proves the full workflow against a running backend:
#   login -> paginated list -> register -> duplicate rule -> validation
#   -> edit -> read -> delete -> 404
#
# Usage: BASE_URL=http://localhost:8080 ./scripts/smoke.sh
set -uo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
# Local development defaults; override for any other environment.
ADMIN_USER="${ADMIN_USER:-admin}"
ADMIN_PASS="${ADMIN_PASS:-admin123}"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

PASS=0
FAIL=0

check() {
  local label="$1" expected="$2" actual="$3"
  if [ "$actual" = "$expected" ]; then
    printf '  PASS  %-52s %s\n' "$label" "$actual"
    PASS=$((PASS + 1))
  else
    printf '  FAIL  %-52s expected=%s got=%s\n' "$label" "$expected" "$actual"
    FAIL=$((FAIL + 1))
  fi
}

contains() {
  local label="$1" needle="$2" haystack="$3"
  if printf '%s' "$haystack" | grep -q -- "$needle"; then
    printf '  PASS  %-52s "%s"\n' "$label" "$needle"
    PASS=$((PASS + 1))
  else
    printf '  FAIL  %-52s missing "%s" in: %s\n' "$label" "$needle" "$haystack"
    FAIL=$((FAIL + 1))
  fi
}

json_field() {
  python3 -c "import json,sys;d=json.load(open(sys.argv[1]));print(d$2)" "$1"
}

status() {
  curl -s -o "$2" -w '%{http_code}' "${@:3}"
}

echo "Stark Group VMS - REST smoke test"
echo "Target: ${BASE_URL}"
echo

# ---------- Health & auth ----------
check "GET /health is public" \
  "200" "$(status '' "$TMP_DIR/health.json" "${BASE_URL}/health")"

check "POST /auth/login with bad password -> 401" \
  "401" "$(status '' "$TMP_DIR/badlogin.json" -X POST "${BASE_URL}/auth/login" \
            -H 'Content-Type: application/json' -d "{\"username\":\"${ADMIN_USER}\",\"password\":\"wrong\"}")"

check "POST /auth/login with demo credentials -> 200" \
  "200" "$(status '' "$TMP_DIR/login.json" -X POST "${BASE_URL}/auth/login" \
            -H 'Content-Type: application/json' -d "{\"username\":\"${ADMIN_USER}\",\"password\":\"${ADMIN_PASS}\"}")"

TOKEN="$(json_field "$TMP_DIR/login.json" "['token']")"
if [ -n "$TOKEN" ]; then
  printf '  PASS  %-52s %s\n' "login response contains a JWT" "yes"
  PASS=$((PASS + 1))
else
  printf '  FAIL  %-52s\n' "login response contains a JWT"
  FAIL=$((FAIL + 1))
fi

AUTH=(-H "Authorization: Bearer ${TOKEN}")

check "GET /vehicles without token -> 401" \
  "401" "$(status '' "$TMP_DIR/noauth.json" "${BASE_URL}/vehicles")"

# ---------- Self-service registration ----------
NEW_USER="smoke-$(date +%H%M%S)"
REG_BODY="{\"username\":\"${NEW_USER}\",\"fullName\":\"Smoke Tester\",\"password\":\"smoke-passphrase\"}"

check "POST /auth/register -> 201" \
  "201" "$(status '' "$TMP_DIR/register.json" -X POST "${BASE_URL}/auth/register" \
            -H 'Content-Type: application/json' -d "$REG_BODY")"

check "register returns a JWT" \
  "yes" "$([ -n "$(json_field "$TMP_DIR/register.json" "['token']")" ] && echo yes || echo no)"

check "duplicate username -> 409" \
  "409" "$(status '' "$TMP_DIR/register-dup.json" -X POST "${BASE_URL}/auth/register" \
            -H 'Content-Type: application/json' -d "$REG_BODY")"

check "new operator can sign in" \
  "200" "$(status '' "$TMP_DIR/newlogin.json" -X POST "${BASE_URL}/auth/login" \
            -H 'Content-Type: application/json' \
            -d "{\"username\":\"${NEW_USER}\",\"password\":\"smoke-passphrase\"}")"

# ---------- Paginated list ----------
check "GET /vehicles?page=0&size=10 -> 200" \
  "200" "$(status '' "$TMP_DIR/list.json" "${BASE_URL}/vehicles?page=0&size=10&sort=vehicleNumber,asc" "${AUTH[@]}")"

PAGE_KEYS="$(python3 - "$TMP_DIR/list.json" <<'PY'
import json, sys
d = json.load(open(sys.argv[1]))
print(','.join(k for k in ['content', 'number', 'size', 'totalElements', 'totalPages', 'last'] if k in d))
PY
)"
check "paginated payload has all required Spring Page fields" \
  "content,number,size,totalElements,totalPages,last" "$PAGE_KEYS"

check "page size is honoured" \
  "10" "$(json_field "$TMP_DIR/list.json" "['size']")"

check "GET /vehicles?page=999 returns empty content" \
  "0" "$(python3 - "$TMP_DIR/list.json" "${BASE_URL}" "$TOKEN" <<'PY'
import json, sys, urllib.request
base, token = sys.argv[2], sys.argv[3]
req = urllib.request.Request(base + '/vehicles?page=999&size=10', headers={'Authorization': 'Bearer ' + token})
print(len(json.load(urllib.request.urlopen(req))['content']))
PY
)"

# ---------- Register vehicle ----------
NUMBER="SMOKE-$(date +%H%M%S)"
BODY="{\"vehicleNumber\":\"${NUMBER}\",\"company\":\"Stark Construction\",\"type\":\"PICKUP\",\"brand\":\"Toyota\",\"model\":\"Hilux\",\"status\":\"ACTIVE\"}"

check "POST /vehicles -> 201" \
  "201" "$(status '' "$TMP_DIR/create.json" -X POST "${BASE_URL}/vehicles" \
            -H 'Content-Type: application/json' "${AUTH[@]}" -d "$BODY")"

check "created vehicle number is normalized to uppercase" \
  "$NUMBER" "$(json_field "$TMP_DIR/create.json" "['vehicleNumber']")"

ID="$(json_field "$TMP_DIR/create.json" "['id']")"

# ---------- Duplicate vehicle number -> 409 ----------
check "duplicate vehicle number -> 409" \
  "409" "$(status '' "$TMP_DIR/dup.json" -X POST "${BASE_URL}/vehicles" \
            -H 'Content-Type: application/json' "${AUTH[@]}" -d "$BODY")"

DUP_MESSAGE="$(json_field "$TMP_DIR/dup.json" "['message']")"
contains "409 carries an error message" "already exists" "$DUP_MESSAGE"
check "409 identifies the vehicleNumber field" \
  "Vehicle number already exists." "$(json_field "$TMP_DIR/dup.json" "['fieldErrors']['vehicleNumber']")"

# ---------- Validation -> 400 ----------
BAD_BODY="{\"vehicleNumber\":\"SMOKE-BAD-${RANDOM}\",\"company\":\"\",\"type\":\"PICKUP\",\"brand\":\"\",\"model\":\"\"}"
check "invalid Registration payload -> 400" \
  "400" "$(status '' "$TMP_DIR/bad.json" -X POST "${BASE_URL}/vehicles" \
            -H 'Content-Type: application/json' "${AUTH[@]}" -d "$BAD_BODY")"

contains "400 carries an error message" "Validation failed" "$(json_field "$TMP_DIR/bad.json" "['message']")"
contains "400 carries field errors" "Company" "$(json_field "$TMP_DIR/bad.json" "['fieldErrors']['company']")"

# ---------- Read one ----------
check "GET /vehicles/{id} -> 200" \
  "200" "$(status '' "$TMP_DIR/get.json" "${BASE_URL}/vehicles/${ID}" "${AUTH[@]}")"

# ---------- Update ----------
UPD_BODY="{\"vehicleNumber\":\"${NUMBER}\",\"company\":\"Stark Logistics\",\"type\":\"SUV\",\"brand\":\"Toyota\",\"model\":\"Land Cruiser\",\"status\":\"MAINTENANCE\"}"
check "PUT /vehicles/{id} -> 200" \
  "200" "$(status '' "$TMP_DIR/update.json" -X PUT "${BASE_URL}/vehicles/${ID}" \
            -H 'Content-Type: application/json' "${AUTH[@]}" -d "$UPD_BODY")"

check "update persisted the new model" \
  "Land Cruiser" "$(json_field "$TMP_DIR/update.json" "['model']")"
check "update persisted the new status" \
  "MAINTENANCE" "$(json_field "$TMP_DIR/update.json" "['status']")"
check "update persisted the new company" \
  "Stark Logistics" "$(json_field "$TMP_DIR/update.json" "['company']")"

# ---------- Delete ----------
check "DELETE /vehicles/{id} -> 204" \
  "204" "$(status '' "$TMP_DIR/delete.json" -X DELETE "${BASE_URL}/vehicles/${ID}" "${AUTH[@]}")"

check "GET deleted vehicle -> 404" \
  "404" "$(status '' "$TMP_DIR/after-delete.json" "${BASE_URL}/vehicles/${ID}" "${AUTH[@]}")"

echo
echo "------------------------------------------------------------"
echo "RESULT: ${PASS} passed, ${FAIL} failed"
echo "------------------------------------------------------------"
[ "$FAIL" -eq 0 ]
