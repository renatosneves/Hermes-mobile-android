#!/bin/sh
# Installs the hermes-push plugin into every Hermes profile, enables it and restarts
# the gateways + dashboard so it loads. Safe to re-run; each old copy is kept as a backup.
set -eu
export PATH=/command:$PATH
URL=https://raw.githubusercontent.com/renatosneves/Hermes-mobile-android/apk-builds/server/hermes-push.tar.gz
ROOT=${HERMES_HOME:-$HOME/.hermes}; ROOT=${ROOT%%/profiles/*}
TS=$(date -u +%Y%m%dT%H%M%SZ)
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
curl -fsSL "$URL" -o "$TMP/p.tgz"
echo "sha256: $(sha256sum "$TMP/p.tgz" | cut -c1-16)"
tar xzf "$TMP/p.tgz" -C "$TMP"
for HOME_DIR in "$ROOT" "$ROOT"/profiles/*; do
  [ -d "$HOME_DIR" ] || continue
  mkdir -p "$HOME_DIR/plugins"
  if [ -d "$HOME_DIR/plugins/hermes-push" ]; then mv "$HOME_DIR/plugins/hermes-push" "$HOME_DIR/plugins/.hermes-push-bak-$TS"; fi
  cp -r "$TMP/hermes-push" "$HOME_DIR/plugins/hermes-push"
  NAME=$(basename "$HOME_DIR"); [ "$HOME_DIR" = "$ROOT" ] && NAME=default
  if yes | HERMES_HOME="$HOME_DIR" hermes plugins enable hermes-push >/dev/null 2>&1; then
    echo "enabled: $NAME"
  else
    echo "ENABLE FAILED: $NAME (run: HERMES_HOME=$HOME_DIR hermes plugins enable hermes-push)"
  fi
done
echo "services: $(ls /run/service 2>/dev/null | tr '\n' ' ')"
for S in /run/service/*; do
  case "$(basename "$S")" in *gateway*|*dashboard*) s6-svc -r "$S" && echo "restarted: $(basename "$S")";; esac
done
echo "done. Then in the app: Settings > Behavior > Push notifications > Send test"
