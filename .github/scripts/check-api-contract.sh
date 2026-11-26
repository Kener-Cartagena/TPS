#!/usr/bin/env bash
# =============================================================================
# API contract guard: the public REST API only changes on purpose.
#
# The test ApiContractTests writes the OpenAPI document of the application to
# backend/target/openapi/openapi.json (keys sorted, without the server URL and the
# version). This script compares it with the reviewed snapshot committed in
# docs/api/openapi.json:
#
#   - equal     -> the API did not change, or its change is already committed. OK.
#   - different -> the code changes the API, but the snapshot was not updated: fail,
#                  and show the difference in the run summary.
#
# Why: a renamed field, a removed endpoint or a renamed error code breaks every client.
# With the snapshot in the pull request, such a change is visible in the diff and has
# to be reviewed; it cannot slip through unnoticed.
#
# Usage, from the repository root (after `./mvnw verify` in backend/):
#   bash .github/scripts/check-api-contract.sh                 # compare
#   UPDATE=true bash .github/scripts/check-api-contract.sh     # accept the change
# In CI the generated file comes from the build job, so its path is passed:
#   bash .github/scripts/check-api-contract.sh path/to/openapi.json
# =============================================================================
set -euo pipefail

GENERATED="${1:-backend/target/openapi/openapi.json}"
SNAPSHOT="docs/api/openapi.json"
MAX_DIFF_LINES=300

if [[ ! -f "${GENERATED}" ]]; then
  echo "::error title=API contract::${GENERATED} not found. Run ./mvnw verify in backend/ first (ApiContractTests writes it)."
  exit 1
fi

if [[ "${UPDATE:-false}" == "true" ]]; then
  mkdir -p "$(dirname "${SNAPSHOT}")"
  cp "${GENERATED}" "${SNAPSHOT}"
  echo "Snapshot updated: ${SNAPSHOT}. Review the diff and commit it with your change."
  exit 0
fi

summary() {
  if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
    cat >> "${GITHUB_STEP_SUMMARY}"
  else
    cat > /dev/null
  fi
}

if [[ ! -f "${SNAPSHOT}" ]]; then
  echo "::error file=${SNAPSHOT},title=No API snapshot::${SNAPSHOT} does not exist. Create it with UPDATE=true bash .github/scripts/check-api-contract.sh"
  exit 1
fi

diff_file="$(mktemp)"
trap 'rm -f "${diff_file}"' EXIT

if diff -u --label "${SNAPSHOT} (committed)" --label "generated from the code" \
    "${SNAPSHOT}" "${GENERATED}" > "${diff_file}"; then
  echo "API contract unchanged: the code matches ${SNAPSHOT}."
  {
    echo "## API contract"
    echo
    echo "✅ The API generated from the code matches \`${SNAPSHOT}\`."
  } | summary
  exit 0
fi

changed=$(grep -c '^[-+][^-+]' "${diff_file}" || true)
echo "::error file=${SNAPSHOT},title=API contract changed::The code changes the public API but ${SNAPSHOT} was not updated (${changed} lines differ). If the change is intended, run UPDATE=true bash .github/scripts/check-api-contract.sh and commit the snapshot."
head -n "${MAX_DIFF_LINES}" "${diff_file}"
{
  echo "## API contract"
  echo
  echo "❌ The API generated from the code differs from \`${SNAPSHOT}\` (${changed} lines)."
  echo
  echo "If the change is intended, update the snapshot and commit it:"
  echo
  echo '```bash'
  echo "UPDATE=true bash .github/scripts/check-api-contract.sh"
  echo '```'
  echo
  echo "<details><summary>Difference (first ${MAX_DIFF_LINES} lines)</summary>"
  echo
  echo '```diff'
  head -n "${MAX_DIFF_LINES}" "${diff_file}"
  echo '```'
  echo
  echo "</details>"
} | summary
exit 1
