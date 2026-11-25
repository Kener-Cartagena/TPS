#!/usr/bin/env bash
# =============================================================================
# Migration guard: Flyway migrations that were already merged must never change.
#
# Flyway stores a checksum of every migration it applies. If a merged migration is
# edited, every database that already ran it fails to start ("checksum mismatch"),
# and databases created before and after the edit end up with different schemas.
# A schema change is always a NEW migration (ADR 0002).
#
# This script compares the commit being checked with a base commit and fails if,
# in the migrations folder, a file that exists in the base was:
#   - modified (M), deleted (D) or renamed (R), or
# if a new migration (A):
#   - does not follow the naming rule V<version>__<snake_case>.sql, or
#   - has a version not greater than every version already in the base (it would
#     run out of order, or clash with a migration merged meanwhile).
#
# Base commit, from the GitHub event (passed as environment variables):
#   pull_request -> the target branch (origin/<base_ref>)
#   push         -> the commit before the push (github.event.before)
#
# Run locally against develop:  EVENT_NAME=pull_request BASE_REF=develop .github/scripts/check-migrations.sh
# =============================================================================
set -euo pipefail

MIGRATIONS_DIR="backend/src/main/resources/db/migration"
NAME_PATTERN='^V([0-9]+)__[a-z0-9_]+\.sql$'

case "${EVENT_NAME:-}" in
  pull_request)
    base="origin/${BASE_REF:?BASE_REF is required for pull requests}"
    ;;
  push)
    if [[ -z "${BEFORE_SHA:-}" || "${BEFORE_SHA}" =~ ^0+$ ]]; then
      echo "::notice title=Migration guard::New branch, nothing to compare with."
      exit 0
    fi
    base="${BEFORE_SHA}"
    ;;
  *)
    echo "::notice title=Migration guard::Event '${EVENT_NAME:-none}' has no base to compare with."
    exit 0
    ;;
esac

if ! git cat-file -e "${base}^{commit}" 2>/dev/null; then
  echo "::warning title=Migration guard::Base commit ${base} is not available (force push?). Skipped."
  exit 0
fi

echo "Comparing ${MIGRATIONS_DIR} between ${base} and HEAD"
failed=0

# 1. Existing migrations: modified, deleted or renamed.
#    "base...HEAD" (three dots) compares HEAD with the common ancestor, so changes that
#    only exist on the base branch are not blamed on this branch.
while IFS=$'\t' read -r status path rest; do
  [[ -z "${status}" ]] && continue
  file="${rest:-$path}"
  echo "::error file=${file},title=Merged migration changed::${path} was ${status:0:1} (M=modified, D=deleted, R=renamed). Merged migrations must never change: add a new migration instead."
  failed=1
done < <(git diff --name-status --diff-filter=MDR "${base}...HEAD" -- "${MIGRATIONS_DIR}")

# 2. New migrations: name and version.
highest_base=0
while read -r existing; do
  name=$(basename "${existing}")
  if [[ "${name}" =~ ${NAME_PATTERN} ]] && (( 10#${BASH_REMATCH[1]} > highest_base )); then
    highest_base=$((10#${BASH_REMATCH[1]}))
  fi
done < <(git ls-tree -r --name-only "${base}" -- "${MIGRATIONS_DIR}")

added=0
while read -r path; do
  [[ -z "${path}" ]] && continue
  added=$((added + 1))
  name=$(basename "${path}")
  if [[ ! "${name}" =~ ${NAME_PATTERN} ]]; then
    echo "::error file=${path},title=Invalid migration name::${name} must be V<version>__<snake_case_description>.sql"
    failed=1
  elif (( 10#${BASH_REMATCH[1]} <= highest_base )); then
    echo "::error file=${path},title=Migration out of order::${name} must have a version greater than V${highest_base}, the latest one in ${base}."
    failed=1
  else
    echo "New migration: ${name}"
  fi
done < <(git diff --name-only --diff-filter=A "${base}...HEAD" -- "${MIGRATIONS_DIR}")

# Summary shown on the run page (only when running inside GitHub Actions).
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  {
    echo "## Migration guard"
    echo
    echo "| Compared with | Latest version in base | New migrations | Result |"
    echo "|---|---|---|---|"
    echo "| \`${base}\` | V${highest_base} | ${added} | $([[ ${failed} -eq 0 ]] && echo '✅ OK' || echo '❌ Failed') |"
  } >> "${GITHUB_STEP_SUMMARY}"
fi

if (( failed )); then
  echo "Migration guard failed: see the errors above."
  exit 1
fi
echo "Migration guard passed: no merged migration changed, ${added} new migration(s) in order."
