#!/usr/bin/env bash
# Runs unit + MySQL integration tests (uses DB_TEST_URL, database commonweave_test)
set -euo pipefail
. "$(dirname "$0")/_env.sh"
cd "$ROOT/backend" && mvn verify "$@"
