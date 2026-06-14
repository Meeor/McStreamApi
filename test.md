# McStreamApi Test Checklist

수동 테스트용 체크리스트입니다.

## 1. AuthServer

- [x] 서버에 `McStreamApi-AuthServer-0.1.0.jar` 배치
- [x] 서버에 `config.yml` 배치
- [x] `ASstart.sh` 실행
- [x] `--check-config` 성공 확인
- [x] 내부 health 확인
  - [x] `curl http://127.0.0.1:<PORT>/health`
- [x] 내부 ready 확인
  - [x] `curl http://127.0.0.1:<PORT>/ready`
- [x] 외부 health 확인
  - [x] `curl https://<DOMAIN>/mca/health`
- [x] 외부 ready 확인
  - [x] `curl https://<DOMAIN>/mca/ready`
- [ ] `ASstop.sh`로 정상 종료 확인
- [ ] 다시 `ASstart.sh`로 재기동 확인

## 2. Nginx / Cloudflare

- [x] Nginx `location /mca/` 설정 확인
- [x] `proxy_pass http://127.0.0.1:<PORT>/;` 끝의 `/` 확인
- [x] `sudo nginx -t` 성공 확인
- [x] `sudo systemctl reload nginx` 성공 확인
- [x] Cloudflare 프록시 상태에서 `https://<DOMAIN>/mca/health` 확인
- [ ] `http://127.0.0.1:<PORT>/health`는 되고 외부 URL만 안 되는 경우 Nginx 로그 확인

## 3. Plugin 로드

- [x] Paper 1.21.11 서버 준비
- [x] Java 21로 Paper 서버 실행
- [x] `plugins/`에 `McStreamApi-0.1.0.jar` 배치
- [x] 서버 시작 시 McStreamApi enable 로그 확인
- [x] 최초 실행이면 `plugins/McStreamApi/` 기본 파일 생성 확인
- [x] `config.yml` 설정 후 서버 재시작
- [x] `Api.yml`, `random.yml`, `secret.key`, `tokens/` 존재 확인

## 4. Plugin config

- [x] `auth.serverBaseUrl`이 외부 AuthServer URL과 일치
- [x] Plugin `auth.sharedSecret`과 AuthServer `security.sharedSecret` 일치
- [x] `platforms.chzzk.enabled: true`
- [x] Chzzk `clientId` 실제 값 입력
- [x] Chzzk `clientSecret` 실제 값 입력
- [x] SOOP 테스트 전까지 `platforms.soop.enabled: false`
- [x] 서버 콘솔에 config placeholder 경고가 없는지 확인

## 5. Chzzk OAuth 연결

- [x] Chzzk 개발자센터 Redirect URL 확인
  - [x] `https://<DOMAIN>/mca/oauth/chzzk/callback`
- [x] Chzzk API scope 확인
  - [x] 유저 조회
  - [x] 후원 조회
  - [x] 구독 조회
- [x] 플레이어로 접속
- [x] `/mca connect chzzk` 실행
- [x] 채팅에 일반 URL이 노출되지 않는지 확인
- [x] 채팅에 파란색 `[연결하러 가기]` 클릭 텍스트가 보이는지 확인
- [x] 인증 코드는 플레이어 채팅이 아니라 콘솔 로그에만 남는지 확인
- [x] 클릭 시 브라우저 인증 페이지가 열리는지 확인
- [x] OAuth 승인 후 성공 페이지 확인
- [x] 플레이어에게 `chzzk 인증이 완료되었습니다.` 메시지 확인
- [x] `plugins/McStreamApi/tokens/`에 암호화 token 파일 생성 확인
- [ ] token 파일에 `accessToken`, `refreshToken` 평문이 없는지 확인

## 6. `/mca` 명령어

- [x] 일반 플레이어가 `/mca connect chzzk` 사용 가능
- [ ] 권한 없는 플레이어가 `/mca reload` 거부되는지 확인
- [ ] 권한 없는 플레이어가 `/mca status` 거부되는지 확인
- [ ] 권한 없는 플레이어가 `/mca apply <player> <amount>` 거부되는지 확인
- [x] 콘솔은 권한 없이 `/mca reload` 가능
- [x] 콘솔은 권한 없이 `/mca status` 가능
- [x] 콘솔은 권한 없이 `/mca apply <player> <amount>` 가능
- [ ] Tab completion이 권한에 맞게 보이는지 확인

## 7. Reward / Api.yml

- [x] `chance` 없는 reward가 100% 후보로 처리되는지 확인
- [x] `chance: 0` reward가 제외되는지 확인
- [x] exact amount 보상이 range/plus보다 우선되는지 확인
- [x] range amount 보상이 plus보다 우선되는지 확인
- [x] 동일 우선순위 reward가 chance 가중치로 하나만 선택되는지 확인
- [ ] `/mca apply <player> <amount>`로 수동 보상 테스트
- [x] 잘못된 amount 문법이 있는 reward가 비활성화되고 서버는 계속 동작하는지 확인
- [x] actions가 비어 있는 reward가 비활성화되는지 확인

## 8. Action

- [ ] `broadcast` 메시지 실행 확인
- [ ] `cmd` 명령 실행 확인
- [ ] `give` 지급 확인
- [x] `give.target` 생략 시 인증 플레이어에게 지급되는지 확인
- [x] `give.amount` 생략 시 1개 지급되는지 확인
- [ ] `chat` 메시지 실행 확인
- [ ] `title` 표시 확인
- [x] Action 하나 실패 후 다음 Action이 계속 실행되는지 확인
- [x] 오프라인 플레이어 대상 Action이 실행되지 않고 실패 처리되는지 확인

## 9. Placeholder / random.yml

- [x] `{player}` 치환 확인
- [x] `{streamer}` 치환 확인
- [x] `{platform}` 치환 확인
- [x] `{donator}` 치환 확인
- [x] `{amount}` 치환 확인
- [x] `{message}` 치환 확인
- [x] `{random.key}`가 같은 이벤트 안에서 같은 값으로 유지되는지 확인
- [x] `{random_once.key}`가 호출마다 새로 선택되는지 확인
- [x] random 단순 목록이 chance 100으로 처리되는지 확인
- [x] random weighted 목록이 chance 가중치로 선택되는지 확인
- [x] `chance <= 0` random 항목이 제외되는지 확인
- [x] 없는 random key는 원문 placeholder로 남는지 확인

## 10. Chzzk 후원 이벤트

스트리머 계정 또는 실제 후원 이벤트가 가능한 환경에서 확인합니다.

- [ ] Chzzk 인증 완료된 플레이어가 온라인 상태
- [ ] 플레이어 접속 시 Chzzk Provider 세션 시작 로그 확인
- [ ] 실제 후원 이벤트 발생
- [ ] 후원 이벤트가 콘솔 로그에 오류 없이 처리되는지 확인
- [ ] Reward pipeline으로 이벤트가 들어가는지 확인
- [ ] 금액 조건에 맞는 Reward가 1개만 실행되는지 확인
- [ ] 같은 eventId가 중복 보상되지 않는지 확인
- [ ] 플레이어 퇴장 시 세션 종료 로그 확인
- [ ] 플레이어 퇴장 후 이벤트가 보상으로 이어지지 않는지 확인

## 11. SOOP

SOOP 공식 후원 이벤트 API 확인 후 진행합니다.

- [ ] SOOP 후원 이벤트 공식 API endpoint 확인
- [ ] SOOP WebSocket 또는 이벤트 세션 방식 확인
- [ ] eventId/cursor/sequence 제공 여부 확인
- [ ] rate limit 확인
- [ ] SOOP provider 구현 보강
- [ ] `platforms.soop.enabled: true`
- [ ] `/mca connect soop` OAuth 연결 확인
- [ ] SOOP 후원 이벤트 수신 확인
- [ ] SOOP Reward pipeline 처리 확인

## 12. 실패 케이스

- [x] Plugin `config.yml` 누락 시 기본 파일 생성 확인
- [x] Plugin placeholder config일 때 런타임 비활성화 확인
- [x] 잘못된 YAML일 때 서버가 터지지 않고 경고만 남는지 확인
- [x] AuthServer URL이 잘못됐을 때 `/mca connect` 실패 메시지 확인
- [x] AuthServer가 꺼져 있을 때 `/mca connect` 실패 메시지 확인
- [x] sharedSecret 불일치 시 실패 메시지 확인
- [x] Pairing timeout 확인
- [x] OAuth callback에서 code 누락 시 실패 페이지 확인
- [x] Token 저장 실패 시 플레이어에게 최종 실패 안내 확인
- [x] Token refresh 실패 시 재시도 후 최종 실패 안내 확인
- [x] Provider 세션 연결 실패 시 서버가 불안정해지지 않는지 확인

## 13. 보안 / 공개 전

- [x] `config.yml` 공개 대상에 없음
- [x] `secret.key` 공개 대상에 없음
- [x] `tokens/` 공개 대상에 없음
- [x] `logs/` 공개 대상에 없음
- [x] `private-authserver-deploy-notes.md` 공개 대상에 없음
- [x] `clientSecret` 실제 값 검색
- [x] `sharedSecret` 실제 값 검색
- [x] `accessToken` 실제 값 검색
- [x] `refreshToken` 실제 값 검색
- [x] release zip에 실제 config/token/log 파일이 없는지 확인
- [x] release zip에 example config와 public docs만 포함되는지 확인

## 14. Release

- [x] `.\gradlew.bat clean build releaseBundle --no-daemon --console=plain '-PreleaseVersion=0.1.0'`
- [x] `plugin/build/libs/McStreamApi-0.1.0.jar` 존재 확인
- [x] `auth-server/build/libs/McStreamApi-AuthServer-0.1.0.jar` 존재 확인
- [x] `build/release/McStreamApi-0.1.0-release.zip` 존재 확인
- [x] Plugin jar `plugin.yml` version 확인
- [x] AuthServer `/health` version 확인
- [x] GitHub Release 본문 확인
- [x] CHANGELOG 확인
