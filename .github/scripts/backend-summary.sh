#!/usr/bin/env bash
# =============================================================================
# Prints a Markdown summary of the backend build: test counts (from the Surefire
# reports) and code coverage (from the JaCoCo CSV report), overall and per module.
#
# In CI the output is appended to $GITHUB_STEP_SUMMARY, so it appears on the run's
# summary page without downloading any artifact. Run from backend/ after
# `./mvnw verify`:   ../.github/scripts/backend-summary.sh
# =============================================================================
set -euo pipefail

REPORTS_DIR="target/surefire-reports"
COVERAGE_CSV="target/site/jacoco/jacoco.csv"

echo "## Backend tests"
echo
shopt -s nullglob
reports=("${REPORTS_DIR}"/TEST-*.xml)
if (( ${#reports[@]} == 0 )); then
  echo "No test reports found."
else
  # Each report starts with <testsuite tests="..." failures="..." errors="..." skipped="...">.
  total() {
    grep -ho '<testsuite [^>]*' "${reports[@]}" \
      | grep -o " $1=\"[0-9]*\"" | grep -o '[0-9]\+' \
      | awk '{ sum += $1 } END { print sum + 0 }'
  }
  tests=$(total tests)
  failures=$(total failures)
  errors=$(total errors)
  skipped=$(total skipped)
  passed=$((tests - failures - errors - skipped))
  result=$([[ $((failures + errors)) -eq 0 ]] && echo '✅' || echo '❌')
  echo "| Result | Tests | Passed | Failed | Errors | Skipped | Test classes |"
  echo "|---|---|---|---|---|---|---|"
  echo "| ${result} | ${tests} | ${passed} | ${failures} | ${errors} | ${skipped} | ${#reports[@]} |"
fi

echo
echo "## Coverage (JaCoCo)"
echo
if [[ ! -f "${COVERAGE_CSV}" ]]; then
  echo "No coverage report found."
  exit 0
fi

# CSV columns: GROUP,PACKAGE,CLASS,INSTRUCTION_MISSED,INSTRUCTION_COVERED,
#              BRANCH_MISSED,BRANCH_COVERED,LINE_MISSED,LINE_COVERED,...
# The module is the package segment after io.github.kenercartagena.tps.
# Prints one row per module plus a "~total" row (sorted last), in plain POSIX awk.
rows=$(awk -F, '
  function pct(covered, missed) {
    return (covered + missed) == 0 ? "-" : sprintf("%.1f%%", 100 * covered / (covered + missed))
  }
  NR > 1 {
    split($2, parts, ".")
    module = (parts[5] == "") ? "(application)" : parts[5]
    im[module] += $4; ic[module] += $5; bm[module] += $6; bc[module] += $7
    lm[module] += $8; lc[module] += $9
    IM += $4; IC += $5; BM += $6; BC += $7; LM += $8; LC += $9
  }
  END {
    for (m in im) {
      printf "| `%s` | %s | %s | %s |\n", m, pct(ic[m], im[m]), pct(bc[m], bm[m]), pct(lc[m], lm[m])
    }
    printf "~| **Total** | **%s** | **%s** | **%s** |\n", pct(IC, IM), pct(BC, BM), pct(LC, LM)
  }' "${COVERAGE_CSV}" | sort)

echo "| Module | Instructions | Branches | Lines |"
echo "|---|---|---|---|"
grep '^~' <<< "${rows}" | cut -c2-
grep -v '^~' <<< "${rows}"

echo
echo "_No minimum is enforced yet: a coverage gate is planned for Phase 8._"
