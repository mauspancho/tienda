#!/usr/bin/env bash
set -euo pipefail

jar=$(realpath "${1:?JAR path required}")
test -f "$jar"
directory=${2:-$(mktemp -d)}
mkdir -p "$directory"
directory=$(realpath "$directory")
if [ -n "$(find "$directory" -mindepth 1 -print -quit)" ]; then
  echo "Smoke test requires an empty directory: $directory" >&2
  exit 1
fi

# Do not inherit a configured database or application profile from the host.
while IFS= read -r name; do
  case "$name" in
    SPRING_*|TIENDA_*|JAVA_TOOL_OPTIONS|JDK_JAVA_OPTIONS|_JAVA_OPTIONS) unset "$name" ;;
  esac
done < <(compgen -e)

(
  cd "$directory"
  exec java -jar "$jar" --server.address=127.0.0.1 --server.port=0
) > "$directory/startup.log" 2>&1 &
pid=$!
trap 'kill "$pid" 2>/dev/null || true; wait "$pid" 2>/dev/null || true' EXIT

port=''
for attempt in {1..90}; do
  if ! kill -0 "$pid" 2>/dev/null; then
    cat "$directory/startup.log"
    exit 1
  fi
  port=$(sed -nE 's/.*Tomcat started on port ([0-9]+).*/\1/p' "$directory/startup.log" | tail -1)
  if [ -n "$port" ]; then break; fi
  sleep 1
done
test -n "$port"
curl --fail --silent --show-error "http://127.0.0.1:$port/setup" -o "$directory/setup.html"
curl --fail --silent --show-error "http://127.0.0.1:$port/" -o "$directory/root.html"
grep -q 'Configurar Tienda' "$directory/setup.html"
grep -q 'Configurar Tienda' "$directory/root.html"
test ! -e "$directory/config/application.yml"
kill -0 "$pid"
echo "CLEAN_JAR_OK: /setup=200, /=200, setup rendered without a database or external configuration"
