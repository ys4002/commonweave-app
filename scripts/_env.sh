# shared env loader (sourced by the other scripts)
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}"
export PATH="$JAVA_HOME/bin:$PATH"
if [ -f "$ROOT/.env" ]; then set -a; . "$ROOT/.env"; set +a; else echo "Missing .env - copy .env.example to .env and set DB_PASSWORD" >&2; exit 1; fi
