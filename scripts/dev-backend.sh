#!/usr/bin/env bash
set -euo pipefail
. "$(dirname "$0")/_env.sh"
cd "$ROOT/backend" && mvn -q spring-boot:run
