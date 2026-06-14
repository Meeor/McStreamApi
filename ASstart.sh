#!/usr/bin/env bash
set -euo pipefail

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG_FILE="${CONFIG_FILE:-$APP_DIR/config.yml}"
JAVA_BIN="${JAVA_BIN:-java}"
PID_FILE="${PID_FILE:-$APP_DIR/auth-server.pid}"
LOG_DIR="${LOG_DIR:-$APP_DIR/logs}"
PORT="${PORT:-49534}"

cd "$APP_DIR"
mkdir -p "$LOG_DIR"

if [[ -n "${JAR_FILE:-}" ]]; then
  JAR_FILE="$JAR_FILE"
elif [[ -f "$APP_DIR/McStreamApi-AuthServer.jar" ]]; then
  JAR_FILE="$APP_DIR/McStreamApi-AuthServer.jar"
else
  JAR_FILE="$(find "$APP_DIR" -maxdepth 1 -type f -name 'McStreamApi-AuthServer-*.jar' | sort | tail -n 1)"
fi

if [[ -z "${JAR_FILE:-}" || ! -f "$JAR_FILE" ]]; then
  echo "AuthServer jar 파일을 찾을 수 없습니다."
  echo "$APP_DIR 안에 McStreamApi-AuthServer.jar를 넣거나 JAR_FILE 환경변수로 jar 경로를 지정해주세요."
  exit 1
fi

if [[ ! -f "$CONFIG_FILE" ]]; then
  echo "config.yml 파일을 찾을 수 없습니다: $CONFIG_FILE"
  exit 1
fi

if [[ -f "$PID_FILE" ]]; then
  OLD_PID="$(cat "$PID_FILE")"
  if [[ -n "$OLD_PID" ]] && kill -0 "$OLD_PID" 2>/dev/null; then
    echo "AuthServer가 이미 실행 중입니다. pid=$OLD_PID"
    exit 0
  fi
  rm -f "$PID_FILE"
fi

echo "설정 파일을 검증합니다..."
"$JAVA_BIN" -jar "$JAR_FILE" --config "$CONFIG_FILE" --check-config

echo "AuthServer를 시작합니다..."
nohup "$JAVA_BIN" -jar "$JAR_FILE" --config "$CONFIG_FILE" > "$LOG_DIR/console.log" 2>&1 &
PID="$!"
echo "$PID" > "$PID_FILE"

sleep 3

if ! kill -0 "$PID" 2>/dev/null; then
  echo "AuthServer 시작에 실패했습니다. 로그를 확인해주세요: $LOG_DIR/console.log"
  rm -f "$PID_FILE"
  exit 1
fi

echo "AuthServer가 시작되었습니다. pid=$PID"
echo "상태 확인 주소: http://127.0.0.1:$PORT/health"
