#!/usr/bin/env bash
set -euo pipefail

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PID_FILE="${PID_FILE:-$APP_DIR/auth-server.pid}"
TIMEOUT="${TIMEOUT:-15}"

cd "$APP_DIR"

if [[ ! -f "$PID_FILE" ]]; then
  echo "AuthServer PID 파일이 없습니다. 이미 종료된 상태일 수 있습니다."
  exit 0
fi

PID="$(tr -d '[:space:]' < "$PID_FILE")"
if [[ -z "$PID" || ! "$PID" =~ ^[0-9]+$ ]]; then
  echo "PID 파일 내용이 올바르지 않습니다. PID 파일을 정리합니다: $PID_FILE"
  rm -f "$PID_FILE"
  exit 0
fi

if ! kill -0 "$PID" 2>/dev/null; then
  echo "실행 중인 AuthServer 프로세스를 찾을 수 없습니다. pid=$PID"
  echo "잔여 PID 파일을 정리합니다."
  rm -f "$PID_FILE"
  exit 0
fi

COMMAND="$(ps -p "$PID" -o command= 2>/dev/null || true)"
if [[ "$COMMAND" != *"McStreamApi-AuthServer"* && "$COMMAND" != *"auth-server"* ]]; then
  echo "PID가 AuthServer 프로세스로 보이지 않아 종료하지 않습니다. pid=$PID"
  echo "process=$COMMAND"
  echo "필요하면 직접 확인 후 종료해주세요."
  exit 1
fi

echo "AuthServer 종료를 요청합니다. pid=$PID"
kill "$PID"

COUNT=0
while kill -0 "$PID" 2>/dev/null; do
  if [[ "$COUNT" -ge "$TIMEOUT" ]]; then
    echo "정상 종료 시간이 초과되어 강제 종료합니다. pid=$PID"
    kill -9 "$PID" 2>/dev/null || true
    break
  fi
  sleep 1
  COUNT=$((COUNT + 1))
done

rm -f "$PID_FILE"
echo "AuthServer가 종료되었습니다."
