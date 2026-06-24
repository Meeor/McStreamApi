# McStreamApi Plan

## Phase Overview

- [x] Phase 00: 기획/문서 정리
- [x] Phase 01: 저장소 구조/빌드 전략 확정
- [x] Phase 02: 공통 개발 규칙/보안 기준 확정
- [x] Phase 03: AuthServer 프로젝트 스캐폴딩
- [x] Phase 04: AuthServer 설정/CLI/기동 검증
- [x] Phase 05: AuthServer 공통 HTTP 계층
- [x] Phase 06: AuthServer Pairing 저장소/상태 머신
- [x] Phase 07: AuthServer Pairing API
- [x] Phase 08: AuthServer OAuth State/API 흐름
- [x] Phase 09: AuthServer SOOP Provider
- [x] Phase 10: AuthServer Chzzk Provider
- [x] Phase 11: AuthServer 운영 안정화/패키징
- [x] Phase 12: Plugin 프로젝트 스캐폴딩
- [x] Phase 13: Plugin 설정 파일 생성/검증
- [x] Phase 14: Plugin 명령어/권한 기반
- [x] Phase 15: Plugin Reward/Amount 엔진
- [x] Phase 16: Plugin Action 엔진
- [x] Phase 17: Plugin Placeholder/Random 엔진
- [x] Phase 18: Plugin Token 암호화 저장소
- [x] Phase 19: Plugin AuthServer 연동
- [ ] Phase 20: Plugin SOOP 세션 Provider
- [x] Phase 21: Plugin Chzzk 세션 Provider
- [x] Phase 22: Plugin 온라인 세션/이벤트 중복 방지
- [ ] Phase 23: 로컬 통합 테스트
- [x] Phase 24: Nginx/Ubuntu 배포 검증
- [x] Phase 25: 보안/민감정보 감사
- [x] Phase 26: 실패 케이스/회귀 테스트
- [x] Phase 27: 공개 README/운영 문서 완성
- [x] Phase 28: 릴리즈 패키징
- [ ] Phase 29: 최종 릴리즈 검증

---

## Phase 00: 기획/문서 정리

목표:

- 플러그인과 AuthServer의 책임을 분리한다.
- 설정 파일 구조를 확정한다.
- 공개 저장소에 올릴 파일과 제외할 파일을 구분한다.

작업:

- [x] `info.md` 작성
- [x] `auth-server.md` 작성
- [x] `README.md` 임시 작성
- [x] `config.example.yml` 작성
- [x] `Api.example.yml` 작성
- [x] `random.example.yml` 작성
- [x] `auth-server.config.example.yml` 작성
- [x] `generate_shared_secret.py` 작성
- [x] 개인 배포 메모 작성
- [x] `.gitignore` 작성
- [x] `plan.md` 작성

완료 기준:

- 플러그인 구현자가 `info.md`만 보고 기본 구조를 만들 수 있다.
- AuthServer 구현자가 `auth-server.md`만 보고 jar 서버를 만들 수 있다.
- 실제 secret/config 파일이 공개 저장소에 올라가지 않도록 제외되어 있다.

---

## Phase 01: 저장소 구조/빌드 전략 확정

목표:

- Plugin과 AuthServer를 같은 저장소 안에서 독립적으로 개발/빌드할 수 있게 만든다.

권장 구조:

```text
McStreamApi/
  plugin/
  auth-server/
  docs/
  config.example.yml
  Api.example.yml
  random.example.yml
  auth-server.config.example.yml
```

작업:

- [x] Gradle multi-project 사용 여부 결정
- [x] Java 21 기준 설정
- [x] Kotlin 버전 결정
- [x] group/package naming 결정
- [x] Plugin artifact 이름 결정
- [x] AuthServer artifact 이름 결정
- [x] 루트 build 명령 결정
- [x] 모듈별 build 명령 결정
- [x] Gradle Wrapper 생성

완료 기준:

- Plugin과 AuthServer를 독립적으로 build 할 수 있는 구조가 정해진다.
- 루트에서 전체 build를 실행할 수 있는 기준이 정해진다.
- example 설정 파일과 실제 설정 파일의 위치가 명확하다.

---

## Phase 02: 공통 개발 규칙/보안 기준 확정

목표:

- 구현 전 품질과 보안 기준을 확정한다.

작업:

- [x] formatter/lint 기준 결정
- [x] 테스트 프레임워크 결정
- [x] 로그 마스킹 규칙 확정
- [x] token/clientSecret/sharedSecret 로그 금지 규칙 확정
- [x] 오류 코드 naming 규칙 확정
- [x] 사용자 메시지/콘솔 로그 분리 기준 확정
- [x] 공식 API 문서 확인 의무 재확인
- [x] GitHub Actions 사용 여부 결정

완료 기준:

- 민감정보가 로그와 공개 파일에 남지 않는 기준이 있다.
- 공식 API 확인 없이 Provider 코드를 작성하지 않는 원칙이 명확하다.

---

## Phase 03: AuthServer 프로젝트 스캐폴딩

목표:

- Ktor 기반 AuthServer 프로젝트를 생성한다.

작업:

- [x] `auth-server/` 모듈 생성
- [x] Ktor Netty 의존성 추가
- [x] Kotlin serialization 또는 Jackson 선택
- [x] YAML 로더 의존성 추가
- [x] logback 설정
- [x] Ktor buildFatJar 설정
- [x] `Application.kt` 엔트리포인트 작성
- [x] 기본 `/health` 임시 route 작성

완료 기준:

- `java -jar McStreamApi-AuthServer.jar` 실행 가능한 jar가 생성된다.
- 기본 health route가 응답한다.

---

## Phase 04: AuthServer 설정/CLI/기동 검증

목표:

- AuthServer jar 실행에 필요한 config/CLI 정책을 구현한다.

작업:

- [x] `--config <path>` 구현
- [x] `--check-config` 구현
- [x] `--version` 구현
- [x] `config.yml` 없을 때 기본 파일 생성 후 종료
- [x] `config.example.yml` 리소스 포함
- [x] sharedSecret 기본값 검증
- [x] sharedSecret 길이 검증
- [x] publicBaseUrl HTTPS 검증
- [x] 플랫폼별 clientId/clientSecret/redirectUri 검증
- [x] 모든 플랫폼 비활성화 시 기동 실패

완료 기준:

- 잘못된 설정에서 HTTP 서버가 시작되지 않는다.
- 설정 문제는 콘솔에 명확히 출력된다.
- 실제 secret 값은 출력되지 않는다.

---

## Phase 05: AuthServer 공통 HTTP 계층

목표:

- 모든 API가 공통 응답/오류/로그 정책을 따른다.

작업:

- [x] 공통 성공 응답 구조 구현
- [x] 공통 ErrorResponse 구현
- [x] requestId 생성/전파
- [x] `X-Request-Id` 검증
- [x] LogMasker 구현
- [x] SharedSecretValidator 구현
- [x] RateLimiter 구현
- [x] `/health` 구현
- [x] `/ready` 구현
- [x] 공통 exception handler 구현

완료 기준:

- 오류 응답에 requestId가 포함된다.
- token/clientSecret/sharedSecret이 응답이나 로그에 노출되지 않는다.
- `/health`, `/ready`가 동작한다.

---

## Phase 06: AuthServer Pairing 저장소/상태 머신

목표:

- PairingSession의 생명주기를 정확히 관리한다.

작업:

- [x] PairingSession 모델 구현
- [x] PairingStatus enum 구현
- [x] OAuthToken 모델 구현
- [x] ChannelInfo 모델 구현
- [x] InMemoryPairingStore 구현
- [x] PairingService 구현
- [x] PENDING -> AUTHORIZED 상태 전이
- [x] AUTHORIZED -> CONSUMED 상태 전이
- [x] PENDING/AUTHORIZED -> EXPIRED 상태 전이
- [x] FAILED 상태 전이
- [x] Token 필드 즉시 삭제 규칙 구현

완료 기준:

- 금지된 상태 전이가 발생하지 않는다.
- Token은 AUTHORIZED 상태에서만 존재한다.
- CONSUMED/EXPIRED/FAILED 상태에는 Token이 남지 않는다.

---

## Phase 07: AuthServer Pairing API

목표:

- Plugin이 Pairing을 등록하고 상태를 조회할 수 있게 한다.

작업:

- [x] `POST /api/pairing` 구현
- [x] `GET /api/pairing/{pairingCode}` 구현
- [x] `DELETE /api/pairing/{pairingCode}` 구현
- [x] pairingCode 형식 검증
- [x] minecraftPlayerName 검증
- [x] minecraftUuid 검증
- [x] platform enabled 검증
- [x] sharedSecret 누락/오류 처리
- [x] AUTHORIZED 응답 후 Token 1회 consume 구현
- [x] CONSUMED 재조회 응답 구현

완료 기준:

- Plugin 계약대로 Token을 1회만 받을 수 있다.
- 잘못된 요청은 명확한 오류 코드로 실패한다.

---

## Phase 08: AuthServer OAuth State/API 흐름

목표:

- OAuth start/callback 흐름을 플랫폼 Provider와 연결한다.

작업:

- [x] OAuthState 모델 구현
- [x] StateStore 구현
- [x] state 생성/만료/삭제 구현
- [x] `/oauth/{platform}/start` 구현
- [x] `/oauth/{platform}/callback` 구현
- [x] state 검증 실패 처리
- [x] code 누락 처리
- [x] 성공 HTML 페이지 구현
- [x] 실패 HTML 페이지 구현
- [x] callback 성공 후 Pairing AUTHORIZED 전환
- [x] callback 실패 후 Pairing FAILED 전환

완료 기준:

- state 재사용이 불가능하다.
- callback 실패 시 Token이 남지 않는다.
- 사용자는 성공/실패 페이지를 볼 수 있다.

---

## Phase 09: AuthServer SOOP Provider

목표:

- 공식 SOOP OAuth API에 맞춰 Provider를 구현한다.

작업 전 필수:

- [x] SOOP 공식 API 문서 확인
- [x] authorize endpoint 확인
- [x] token endpoint 확인
- [x] refresh endpoint 확인
- [x] channel info endpoint 확인
- [x] 필요한 scope 확인
- [x] rate limit 확인
- [x] 오류 응답 형식 확인

진행 메모:

- 2026-06-10 공식 SOOP OpenAPI iframe 문서 확인 기준: API base는 `https://openapi.sooplive.com`, authorize endpoint는 `GET /auth/code`, token/refresh endpoint는 `POST /auth/token`, 채널/스테이션 정보 조회는 `POST /user/stationinfo`이다.
- SOOP `GET /auth/code`는 문서상 `state` 파라미터가 없으므로 AuthServer는 브라우저 쿠키로 pending OAuth를 구분하고, 쿠키가 없으면 Minecraft 인증 코드 입력 fallback으로 매칭한다.
- `POST /user/stationinfo` 응답에는 Chzzk의 `channelId` 같은 고유 채널 ID가 문서상 없어서 `user_nick`을 `ChannelInfo.channelId`와 `channelName`의 기준값으로 사용한다.
- 공개 SOOP 문서에서 endpoint별 rate limit 정량값은 확인하지 못했다. 오류 형식은 여러 API가 `result=1` 성공, 음수 `result` 오류와 `msg` 메시지를 사용한다.

작업:

- [x] SoopOAuthProvider 구현
- [x] authorize URL 생성
- [x] code -> token 교환
- [x] channel info 조회
- [x] SOOP 오류 응답 매핑
- [x] token/clientSecret 로그 마스킹 검증

완료 기준:

- SOOP OAuth 승인 후 Pairing이 AUTHORIZED가 된다.
- SOOP API 오류는 표준 오류 코드로 변환된다.

---

## Phase 10: AuthServer Chzzk Provider

목표:

- 공식 Chzzk OAuth API에 맞춰 Provider를 구현한다.

작업 전 필수:

- [x] Chzzk 공식 API 문서 확인
- [x] authorize endpoint 확인
- [x] token endpoint 확인
- [x] refresh endpoint 확인
- [x] channel info endpoint 확인
- [x] 필요한 scope 확인
- [x] rate limit 확인
- [x] 오류 응답 형식 확인

진행 메모:

- 2026-06-10 공식 CHZZK GitBook 확인 기준: authorize endpoint는 `https://chzzk.naver.com/account-interlock`, Open API base는 `https://openapi.chzzk.naver.com`, token/refresh endpoint는 `POST /auth/v1/token`, 로그인 유저 채널 조회는 `GET /open/v1/users/me`이다.
- `GET /open/v1/users/me` 사용 scope는 공식 문서상 유저 정보 조회이며, 구현 config에는 내부 추적용으로 `user:read`를 둔다. 실제 개발자 센터에서 표시되는 scope 이름과 다르면 config 값을 공식 앱 설정에 맞춰 조정한다.
- 공식 공통 오류 표에는 `429 TOO_MANY_REQUESTS`가 Quota 제한 초과로 정의되어 있지만, 공개 문서에서 endpoint별 정량 한도는 확인하지 못했다.

작업:

- [x] ChzzkOAuthProvider 구현
- [x] authorize URL 생성
- [x] code -> token 교환
- [x] channel info 조회
- [x] Chzzk 오류 응답 매핑
- [x] token/clientSecret 로그 마스킹 검증

완료 기준:

- Chzzk OAuth 승인 후 Pairing이 AUTHORIZED가 된다.
- Chzzk API 오류는 표준 오류 코드로 변환된다.

---

## Phase 11: AuthServer 운영 안정화/패키징

목표:

- AuthServer jar를 실제 서버에서 안정적으로 실행할 수 있게 한다.

작업:

- [x] background cleanup job 구현
- [x] expired session 정리
- [x] consumed session 정리
- [x] failed session 정리
- [x] graceful shutdown 구현
- [x] SIGTERM/SIGINT 처리
- [x] request timeout 적용
- [x] shutdown timeout 적용
- [x] buildFatJar 산출물 이름 고정
- [x] Linux 실행 테스트
- [x] Windows 실행 테스트

완료 기준:

- jar 하나로 실행 가능하다.
- 종료 시 Token 값이 메모리에 남지 않는다.
- 장시간 실행 시 만료 session이 누적되지 않는다.

---

## Phase 12: Plugin 프로젝트 스캐폴딩

목표:

- Spigot/Paper에서 로드되는 플러그인 기본 구조를 만든다.

작업:

- [x] `plugin/` 모듈 생성
- [x] Spigot/Paper API 의존성 설정
- [x] Kotlin 플러그인 설정
- [x] plugin.yml 작성
- [x] 메인 클래스 구현
- [x] enable/disable lifecycle 구현
- [x] 기본 logger wrapper 구현
- [x] build 산출 jar 이름 고정
- [x] Minecraft 1.21.11 테스트 서버 준비

완료 기준:

- Minecraft 1.21.11 Paper/Spigot 서버에서 플러그인이 로드된다.
- enable/disable 로그가 정상 출력된다.

---

## Phase 13: Plugin 설정 파일 생성/검증

목표:

- 최초 실행과 설정 검증 정책을 구현한다.

작업:

- [x] `plugins/McStreamApi/` 폴더 생성
- [x] config.yml 없을 때 생성
- [x] Api.yml 없을 때 생성
- [x] random.yml 없을 때 생성
- [x] secret.key 없을 때 생성
- [x] tokens/ 폴더 생성
- [x] 최초 생성 후 플러그인 비활성화
- [x] sharedSecret 기본값 검증
- [x] AuthServer URL 검증
- [x] 플랫폼 clientId/clientSecret 검증
- [x] 설정 누락 플랫폼 런타임 비활성화

완료 기준:

- 최초 실행 시 기본 파일을 만들고 플러그인을 비활성화한다.
- 설정이 부족한 플랫폼은 강제 비활성화되고 콘솔에 안내된다.

---

## Phase 14: Plugin 명령어/권한 기반

목표:

- `/mca` 명령 체계와 permission node를 구현한다.

작업:

- [x] `/mca connect <platform>` 구현
- [x] `/mca reload` 구현
- [x] `/mca apply <player> <amount>` 구현
- [x] `/mca status` 구현
- [x] TabCompleter 구현
- [x] connect는 모든 플레이어 허용
- [x] reload는 `mcstreamapi.reload` 필요
- [x] apply는 `mcstreamapi.apply` 필요
- [x] status는 `mcstreamapi.status` 필요
- [x] 콘솔 실행 가능/불가 명령 분리

완료 기준:

- 권한 없는 플레이어는 관리 명령을 사용할 수 없다.
- `/mca connect`는 일반 플레이어가 사용할 수 있다.

---

## Phase 15: Plugin Reward/Amount 엔진

목표:

- 금액 조건에 따라 Reward를 하나만 선택한다.

작업:

- [x] AmountRule exact parser 구현
- [x] AmountRule range parser 구현
- [x] AmountRule plus parser 구현
- [x] 잘못된 amount 문법 검증
- [x] Reward 모델 구현
- [x] RewardMatcher 구현
- [x] Reward chance 기본값 100 처리
- [x] Reward chance 가중치 정규화 구현
- [x] `chance <= 0` Reward 후보 제외 처리
- [x] exact 우선순위 구현
- [x] range 우선순위 구현
- [x] plus 우선순위 구현
- [x] 동일 우선순위 chance 가중치 선택 구현
- [x] 단위 테스트 작성

완료 기준:

- 후원 1회당 Reward 하나만 선택된다.
- chance 합계가 100이 아니어도 Reward 선택이 정상 동작한다.
- 잘못된 Reward는 비활성화되고 나머지는 정상 동작한다.

---

## Phase 16: Plugin Action 엔진

목표:

- Reward 안의 Action들을 순서대로 실행한다.

작업:

- [x] Action 공통 모델 구현
- [x] Action 필수 필드 검증
- [x] cmd Action 구현
- [x] give Action 구현
- [x] give ItemMeta 확장 구현
- [x] chat Action 구현
- [x] broadcast Action 구현
- [x] title Action 구현
- [x] `@s` 온라인 플레이어 치환
- [x] 오프라인 대상 Action 실패 처리
- [x] Action 실패 후 다음 Action 계속 실행
- [x] Bukkit API 메인 스레드 실행 보장

완료 기준:

- Action 하나가 실패해도 이후 Action이 실행된다.
- 오프라인 플레이어 대상 Action은 실행되지 않는다.
- 콘솔에는 실패 원인이 남고 민감정보는 남지 않는다.

---

## Phase 17: Plugin Placeholder/Random 엔진

목표:

- 후원 이벤트 값을 Action 문자열에 안전하게 치환한다.

작업:

- [x] PlaceholderContext 구현
- [x] 기본 placeholder 구현
- [x] Player/Streamer placeholder 구현
- [x] Donation placeholder 구현
- [x] RandomResolver 구현
- [x] `{random.key}` event-scope 캐시 구현
- [x] `{random_once.key}` 매 호출 랜덤 구현
- [x] random 단순 문자열 항목 `chance: 100` 처리
- [x] random weighted value 항목 구현
- [x] random chance 가중치 정규화 구현
- [x] `chance <= 0` random 후보 제외 처리
- [x] 알 수 없는 placeholder 처리
- [x] random.yml reload 반영
- [x] 단위 테스트 작성

완료 기준:

- 같은 이벤트 안에서 `{random.key}`는 같은 값을 유지한다.
- `{random_once.key}`는 호출마다 새 값을 반환한다.
- random chance 합계가 100이 아니어도 선택이 정상 동작한다.

---

## Phase 18: Plugin Token 암호화 저장소

목표:

- OAuth Token을 안전하게 암호화 저장한다.

작업:

- [x] secret.key 생성
- [x] AES-GCM 암호화 구현
- [x] 파일별 nonce 생성
- [x] Token JSON 모델 구현
- [x] TokenStore 저장 구현
- [x] TokenStore 로드 구현
- [x] 인증 태그 실패 처리
- [x] token/clientSecret/sharedSecret 로그 금지 검증
- [x] 단위 테스트 작성

완료 기준:

- Token 파일은 평문으로 저장되지 않는다.
- secret.key 분실/손상 시 명확한 오류가 난다.
- 복호화 실패 Token은 사용하지 않는다.

---

## Phase 19: Plugin AuthServer 연동

목표:

- `/mca connect`가 AuthServer Pairing 흐름을 수행한다.

작업:

- [x] PairingCodeGenerator 구현
- [x] AuthClient 구현
- [x] sharedSecret 헤더 전송
- [x] Pairing 등록 요청 구현
- [x] authorizeUrl 플레이어 안내
- [x] Pairing polling 구현
- [x] polling timeout 구현
- [x] AUTHORIZED 응답 검증
- [x] minecraftUuid 검증
- [x] Token 암호화 저장 연결
- [x] AuthServer 미응답 메시지 구현
- [x] sharedSecret 오류 메시지 구현
- [x] Pairing 만료 메시지 구현

완료 기준:

- `/mca connect soop`로 인증 링크가 생성된다.
- AuthServer에서 받은 Token이 해당 플레이어 Token으로 저장된다.
- 실패 케이스가 사용자/콘솔 메시지 정책대로 처리된다.

---

## Phase 20: Plugin SOOP 세션 Provider

목표:

- SOOP 후원 이벤트를 공식 API 기준으로 수신한다.

작업 전 필수:

- [x] SOOP 후원 이벤트 공식 API 확인
- [ ] WebSocket 가능 여부 확인
- [ ] eventId/cursor/sequence 제공 여부 확인
- [x] token refresh 정책 확인
- [ ] rate limit 확인

진행 메모:

- 2026-06-12 공식 SOOP Chat SDK 문서 확인 기준: Chat SDK는 브라우저 JavaScript 환경용이며, Developer Portal 승인/API Key 발급이 필요하다.
- Chat SDK 이벤트 문서에는 별풍선 선물이 `BALLOON_GIFTED`로 문서화되어 있고, 메시지 필드는 `bjId`, `userId`, `userNickname`, `count`, `fanNumber`, `imageUrl`, `becomesTopFan`, `relaysBroad`, `fromVod`이다.
- 광고 별풍선은 `ADBALLOON_GIFTED`, 비디오 별풍선은 `VIDEOBALLOON_GIFTED`로 별도 이벤트가 있다.
- 공개 문서상 eventId/cursor/sequence, endpoint별 rate limit은 확인하지 못했다. SDK `connect()`는 채팅 서버 연결을 추상화하며 현재 본인 방송 세션만 연결 가능하다고 문서화되어 있다.
- 2026-06-12 공식 SDK 스크립트 `https://static.sooplive.com/asset/app/chat-sdk/sooplive-chat-sdk.js` 확인 기준: SDK는 `POST /broad/access/chatinfo`에 `access_token`을 보내 채팅 서버 정보와 ticket을 받은 뒤 `wss://chat-<chat_ip_hex>.sooplive.com:<chat_port+1>/Websocket/<bjId>`에 `chat` subprotocol로 연결한다.
- SOOP chat packet은 `ESC TAB + serviceCode(4) + bodyLength(6) + retCode(2)` header와 form-feed(`\u000c`) 구분 body를 사용한다. SDK login service는 `16`, join은 `2`, keepalive는 `0`이다.
- 별풍선 reward 대상 이벤트로 `SVC_SENDBALLOON(18)`, `SVC_SENDBALLOONSUB(33)`, `SVC_VODBALLOON(86)`, `SVC_ADCON_EFFECT(87)`, `SVC_VODADCON(103)`, `SVC_VIDEO_BALLOON(105)`, `SVC_STATION_ADCON(107)`를 처리한다.
- 애드벌룬과 영상별풍선은 `platforms.soop.receiveAdBalloons`, `platforms.soop.receiveVideoBalloons` 옵션으로 별도 on/off 한다. 기본값은 둘 다 `false`라서 일반 별풍선만 reward에 적용된다.

작업:

- [x] SoopDonationProvider 구현
- [x] SOOP token refresh 구현
- [x] SOOP WebSocket 또는 공식 이벤트 세션 구현
- [x] SOOP 이벤트 DTO 구현
- [x] DonationEvent 변환
- [x] 오류 응답 매핑
- [x] reconnect 정책 구현
- [x] SOOP 실제 SessionTransport 구현
- [x] `McStreamApiPlugin.createDonationProviders()`에 SOOP provider 등록
- [x] 인증 완료 직후 `platformAuthenticated(..., "soop")`로 SOOP 세션 즉시 시작
- [x] PlayerJoin/PlayerQuit 기준 SOOP 세션 시작/종료 확인
- [x] SOOP WebSocket 연결/끊김/재연결/구독 로그 한국어 출력
- [ ] `/mca status <player>`에 SOOP 토큰 상태와 세션 상태 표시
- [x] SOOP 후원 이벤트 중복 방지 및 Reward pipeline 연결 확인
- [x] SOOP 실패 시 사용자에게 재연결 안내, 콘솔에는 민감정보 없는 원인 로그 출력

Chzzk에서 이미 적용한 런타임 정책을 SOOP에도 동일하게 적용한다.

- Provider는 config의 `clientId`, `clientSecret`, `tokenRefreshBeforeSeconds`를 사용한다.
- token 저장 완료 후 온라인 플레이어라면 다음 접속을 기다리지 않고 즉시 세션을 시작한다.
- 세션 로그는 UUID보다 플레이어 닉네임을 우선 표시하고, `[진행]`, `[성공]`, `[실패]`, `[끊김]`, `[대기]` 상태어를 사용한다.
- accessToken, refreshToken, clientSecret, sharedSecret은 status/로그에 출력하지 않는다.
- `/mca status <player>`는 Chzzk와 SOOP을 같은 형식으로 보여준다.

완료 기준:

- 온라인 플레이어의 SOOP 세션이 유지된다.
- SOOP 후원 이벤트가 Reward pipeline으로 들어간다.
- Chzzk와 동일한 운영 로그, 즉시 세션 시작, status 상세 표시가 SOOP에도 동작한다.
- 실제 SOOP 계정 access token과 방송 중 채팅방으로 실연결 smoke test를 진행해야 운영 검증이 완료된다.

---

## Phase 21: Plugin Chzzk 세션 Provider

목표:

- Chzzk 후원 이벤트를 공식 API 기준으로 수신한다.

작업 전 필수:

- [x] Chzzk 후원 이벤트 공식 API 확인
- [x] WebSocket 가능 여부 확인
- [x] eventId/cursor/sequence 제공 여부 확인
- [x] token refresh 정책 확인
- [x] rate limit 확인

작업:

- [x] ChzzkDonationProvider 구현
- [x] Chzzk token refresh 구현
- [x] Chzzk WebSocket 또는 공식 이벤트 세션 구현
- [x] Chzzk 이벤트 DTO 구현
- [x] DonationEvent 변환
- [x] 오류 응답 매핑
- [x] reconnect 정책 구현

완료 기준:

- 온라인 플레이어의 Chzzk 세션이 유지된다.
- Chzzk 후원 이벤트가 Reward pipeline으로 들어간다.

---

## Phase 22: Plugin 온라인 세션/이벤트 중복 방지

목표:

- 온라인 플레이어만 API 세션을 유지하고 중복 보상을 줄인다.

작업:

- [x] PlayerJoin 세션 시작
- [x] PlayerQuit 세션 즉시 종료
- [x] 오프라인 이벤트 보상 금지
- [x] Token 실패 3회 재시도
- [x] 재시도 중 콘솔에만 안내
- [x] 최종 실패 시 플레이어 재연결 안내
- [x] eventId 중복 캐시 구현
- [x] eventId 없는 플랫폼 best-effort hash 구현
- [x] 캐시 만료 정책 구현

완료 기준:

- 서버에서 나간 플레이어의 Provider 세션이 즉시 종료된다.
- 같은 eventId로 Reward가 중복 실행되지 않는다.

---

## Phase 23: 로컬 통합 테스트

목표:

- AuthServer와 Plugin 전체 흐름을 로컬에서 검증한다.

작업:

- [x] AuthServer local jar 실행
- [x] `/health` 확인
- [x] `/ready` 확인
- [ ] Plugin 테스트 서버 기동
- [ ] `/mca connect` 성공 흐름 테스트
- [ ] Pairing timeout 테스트
- [ ] sharedSecret 오류 테스트
- [ ] Token 1회 consume 테스트
- [ ] `/mca apply` Reward 테스트
- [ ] Token refresh 실패 3회 재시도 테스트
- [ ] 플레이어 퇴장 시 세션 종료 테스트

완료 기준:

- 로컬 환경에서 주요 성공/실패 흐름이 동작한다.
- [x] 재현 가능한 테스트 절차가 문서화된다.

---

## Phase 24: Nginx/Ubuntu 배포 검증

목표:

- 실제 Ubuntu + Nginx reverse proxy 환경에서 AuthServer를 검증한다.

작업:

- [x] AuthServer jar 서버 배치
- [x] config.yml 서버 배치
- [x] systemd 또는 수동 실행 테스트
- [x] Nginx `location /mca/` 설정
- [x] `sudo nginx -t` 확인
- [x] `sudo systemctl reload nginx` 확인
- [x] 내부 `/health` curl 확인
- [x] 외부 `/mca/health` curl 확인
- [x] Redirect URI 경유 OAuth callback 확인

완료 기준:

- 외부 URL이 AuthServer 내부 route로 정상 전달된다.
- 실제 폴더 경로와 URL route 개념이 운영 문서에 반영된다.

---

## Phase 25: 보안/민감정보 감사

목표:

- 공개 전 민감정보 노출과 토큰 처리 문제를 제거한다.

작업:

- [x] `clientSecret` 검색
- [x] `sharedSecret` 검색
- [x] `accessToken` 검색
- [x] `refreshToken` 검색
- [x] 실제 config 파일 포함 여부 확인
- [x] `.gitignore` 재검토
- [x] 로그 마스킹 테스트
- [x] 오류 응답 민감정보 노출 테스트
- [x] Token 1회 consume 재검증
- [x] AuthServer Token 메모리 삭제 재검증

완료 기준:

- 공개 저장소에 실제 secret/token/config가 없다.
- 로그와 오류 응답에 민감정보가 없다.

---

## Phase 26: 실패 케이스/회귀 테스트

목표:

- 사용 중 자주 발생할 실패 상황을 안정적으로 처리한다.

작업:

- [x] config 누락
- [x] config placeholder
- [x] 잘못된 YAML
- [x] 잘못된 AuthServer URL
- [x] AuthServer 미응답
- [x] sharedSecret 불일치
- [x] Pairing 만료
- [x] OAuth callback 실패
- [x] Token 저장 실패
- [x] Token refresh 실패
- [x] Provider WebSocket 끊김
- [x] Action 필수 필드 누락
- [x] 오프라인 플레이어 Action

완료 기준:

- 모든 실패 케이스가 의도한 메시지와 로그로 처리된다.
- 실패 후 서버가 불안정한 상태로 남지 않는다.

---

## Phase 27: 공개 README/운영 문서 완성

목표:

- 구현 완료 후 실제 사용법 기준으로 공개 문서를 완성한다.

작업:

- [x] README.md 최종 작성
- [x] Plugin 설치 방법 작성
- [x] AuthServer jar 실행 방법 작성
- [x] sharedSecret 생성 방법 작성
- [x] Chzzk 개발자 설정 방법 작성
- [x] SOOP 개발자 설정 방법 작성
- [x] Nginx 설정 예시 작성
- [x] permission node 문서 작성
- [x] Api.yml Action 스키마 문서 작성
- [x] random.yml 문서 작성
- [x] Troubleshooting 작성
- [x] 개인 서버 정보 제거 확인

완료 기준:

- 신규 사용자가 README만 보고 설치 흐름을 이해할 수 있다.
- 민감한 개인 서버 정보는 공개 문서에 포함되지 않는다.

---

## Phase 28: 릴리즈 패키징

목표:

- Plugin jar와 AuthServer jar를 배포 가능한 형태로 만든다.

작업:

- [x] Plugin jar 파일명 확정
- [x] AuthServer jar 파일명 확정
- [x] 버전 표기 방식 확정
- [x] build metadata 포함
- [x] GitHub Releases 초안 작성
- [x] 릴리즈 zip 필요 여부 결정
- [x] example config 포함 여부 결정
- [x] changelog 작성

완료 기준:

- 사용자가 Plugin jar와 AuthServer jar를 혼동하지 않는다.
- 릴리즈 산출물이 재현 가능하다.

---

## Phase 29: 최종 릴리즈 검증

목표:

- 첫 공개 릴리즈 직전 최종 품질을 확인한다.

작업:

- [x] 현재 작업트리에서 clean build
- [ ] Plugin jar 로드 테스트
- [x] AuthServer jar 실행 테스트
- [x] config 생성 테스트
- [ ] `/mca connect` smoke test
- [ ] `/mca apply` smoke test
- [x] AuthServer `/health` smoke test
- [x] GitHub 공개 파일 목록 검토
- [x] 민감정보 최종 검색
- [x] 릴리즈 노트 최종 검토

완료 기준:

- clean checkout에서 재현 가능한 build가 된다.
- 공개 저장소와 릴리즈 산출물에 민감정보가 없다.
- 첫 공개 릴리즈를 올릴 수 있다.
