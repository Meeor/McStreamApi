# McStreamApi AuthServer - Project Specification

## 개요

McStreamApi AuthServer는 McStreamApi Spigot 플러그인의 OAuth 인증을 보조하는 별도 서버이다.

이 서버는 치지직/SOOP 등의 OAuth Redirect URI를 수신하고, Authorization Code를 Access Token/Refresh Token으로 교환한 뒤,
Pairing Code를 통해 해당 토큰을 McStreamApi 플러그인에게 1회 전달한다.

중요 원칙:

- AuthServer는 토큰을 영구 저장하지 않는다.
- AuthServer는 OAuth 인증 중계만 담당한다.
- 최종 토큰 저장은 McStreamApi 플러그인 데이터 폴더에서 수행한다.
- Token 전달 후 AuthServer의 임시 토큰 데이터는 즉시 삭제한다.
- 모든 통신은 HTTPS 뒤에서 동작해야 한다.
- Nginx는 Reverse Proxy 역할만 수행한다.

---

## 전체 구조

```text
Streamer Browser
  ↓
Platform OAuth Page
  ↓
AuthServer Callback
  ↓
Pairing Code Match
  ↓
McStreamApi Plugin Polling
  ↓
Plugin receives token once
  ↓
AuthServer deletes temporary token
```

---

## 프로젝트 분리

권장 구조:

```text
McStreamApi/
  └─ Spigot Plugin

McStreamApi-AuthServer/
  └─ OAuth Relay Server
```

---

## 권장 기술 스택

권장:

- Kotlin + Ktor
- Java 21

대안:

- Java + Spring Boot
- Node.js + Express/Fastify

McStreamApi 플러그인이 Kotlin/Java 기반이므로, 유지보수 편의상 Kotlin + Ktor 권장.

---

## 기본 포트

내부 포트:

```text
18080
```

예:

```text
http://127.0.0.1:18080
```

외부 공개 URL 예:

```text
https://auth.example.com/mca
```

다른 도메인을 쓰는 경우:

```text
https://stream.example.com/mca
```

---

## Nginx 역할

Nginx는 HTTPS, 도메인, Reverse Proxy만 담당한다.

예시:

```nginx
location /mca/ {
    proxy_pass http://127.0.0.1:18080/;
    proxy_http_version 1.1;

    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

플랫폼 OAuth Redirect URI 등록 예:

```text
https://auth.example.com/mca/oauth/chzzk/callback
https://auth.example.com/mca/oauth/soop/callback
```

---

## 주요 개념

## Pairing Code, OAuth State, sharedSecret 차이

세 값은 비슷해 보이지만 역할이 다르다.

```text
pairingCode   = 연결 요청 접수번호
state         = OAuth callback 위조 방지용 임시 검증값
sharedSecret  = Plugin과 AuthServer 사이의 서버 간 비밀번호
```

### pairingCode

여러 Minecraft 플레이어가 동시에 `/mca connect`를 실행할 수 있기 때문에 필요하다.

예:

```text
Meeor  -> /mca connect chzzk -> pairingCode A7K29Q
Steve  -> /mca connect chzzk -> pairingCode M8P4XZ
```

Chzzk/SOOP OAuth callback에는 Minecraft 플레이어 정보가 기본으로 들어있지 않다.
따라서 AuthServer는 OAuth 결과가 어느 Minecraft 플레이어의 연결 요청인지 알아야 한다.
이때 pairingCode가 "이 토큰은 A7K29Q 요청의 결과"라고 매칭하는 접수번호 역할을 한다.

### state

OAuth provider가 callback으로 돌려주는 값이다.
AuthServer는 state를 보고 "이 callback이 우리가 시작한 OAuth 요청의 결과가 맞는지" 검증한다.
state 검증이 없으면 외부에서 callback URL을 조작해 잘못된 code를 밀어 넣을 수 있다.

### sharedSecret

AuthServer API에서 Token을 가져가는 요청이 진짜 Plugin에서 온 것인지 확인한다.
pairingCode는 인증 링크에 포함되어 방송 화면이나 채팅에 노출될 수 있다.
따라서 pairingCode만으로 Token polling이 가능하면 위험하다.

Plugin은 `/api/pairing/{pairingCode}`를 호출할 때 `X-McStreamApi-Secret` 헤더에 sharedSecret을 넣는다.
AuthServer는 이 값이 설정과 일치할 때만 Token을 1회 전달한다.

---

## Pairing Code

Pairing Code는 플러그인이 생성하는 연결 코드이다.

예:

```text
A7K29Q
```

권장 규칙:

- 6자리 이상
- 대문자 영문 + 숫자
- 혼동 문자 제외 권장: O, 0, I, 1
- 만료 시간: 10분
- 1회성 사용
- 사용 완료 후 삭제

---

## Shared Secret

토큰 탈취 방지를 위해 플러그인과 AuthServer 사이에는 sharedSecret을 사용한다.

필요한 이유:

- Pairing Code는 플레이어에게 보여주는 값이다.
- Pairing Code만으로 Token polling이 가능하면, 코드를 훔쳐 본 사람이 토큰을 먼저 가져갈 수 있다.
- sharedSecret은 "이 요청이 실제 Minecraft 플러그인에서 온 요청인지" 확인하는 서버 간 비밀값이다.
- OAuth accessToken/refreshToken을 전달하는 API에는 Pairing Code 외의 인증 수단이 반드시 필요하다.

예:

```text
mca_GENERATED_RANDOM_SECRET_HERE
```

AuthServer API 호출 시 Header로 전달:

```http
X-McStreamApi-Secret: <shared-secret>
```

용도:

- Pairing Code 등록자 검증
- Token Polling 요청 검증
- 아무나 Pairing Code만 알고 토큰을 가져가는 문제 방지

주의:

- sharedSecret은 외부에 노출되면 안 된다.
- Config나 로그에 출력하지 않는다.
- 토큰과 마찬가지로 민감 정보로 취급한다.
- 공개 Git 저장소에는 실제 값 대신 example 값만 둔다.

---

## Pairing 상태

Pairing은 다음 상태를 가진다.

```text
PENDING
AUTHORIZED
CONSUMED
EXPIRED
FAILED
```

### PENDING

플러그인이 Pairing Code를 등록했지만, 스트리머가 아직 OAuth 승인을 완료하지 않은 상태.

### AUTHORIZED

OAuth 승인이 완료되어 AuthServer가 토큰을 임시 보관 중인 상태.

### CONSUMED

플러그인이 토큰을 수령했고, AuthServer가 임시 토큰을 삭제한 상태.

### EXPIRED

만료 시간이 지나 더 이상 사용할 수 없는 상태.

### FAILED

OAuth 처리 실패 또는 플랫폼 API 오류 발생 상태.

---

## 저장 방식

초기 구현:

```text
In-Memory Map
```

예:

```kotlin
Map<PairingCode, PairingSession>
```

이유:

- AuthServer는 토큰을 장기 저장하지 않음
- 단순 구현 가능
- 재시작 시 Pairing이 초기화되어도 큰 문제 없음

추후 확장:

```text
Redis
SQLite
```

단, 영구 토큰 저장은 하지 않는다.

---

## API 명세

# 1. Pairing 등록

플러그인이 연결 요청을 생성할 때 호출한다.

```http
POST /api/pairing
```

Headers:

```http
X-McStreamApi-Secret: <shared-secret>
Content-Type: application/json
```

Request:

```json
{
  "pairingCode": "A7K29Q",
  "platform": "chzzk",
  "minecraftPlayerName": "Meeor",
  "minecraftUuid": "uuid-string",
  "callbackBaseUrl": "https://auth.example.com/mca"
}
```

Response:

```json
{
  "success": true,
  "pairingCode": "A7K29Q",
  "expiresInSeconds": 600,
  "authorizeUrl": "https://auth.example.com/mca/oauth/chzzk/start?pairingCode=A7K29Q"
}
```

실패 Response:

```json
{
  "success": false,
  "error": "PAIRING_ALREADY_EXISTS",
  "message": "Pairing code already exists."
}
```

---

# 2. OAuth 시작

스트리머가 브라우저로 접속하는 URL.

```http
GET /oauth/{platform}/start?pairingCode=A7K29Q
```

예:

```text
GET /oauth/chzzk/start?pairingCode=A7K29Q
```

동작:

1. Pairing Code 존재 여부 확인
2. 만료 여부 확인
3. OAuth Authorization URL 생성
4. state 생성
5. Platform OAuth 페이지로 Redirect

state에는 다음 정보가 포함되어야 한다.

```text
pairingCode
nonce
platform
expiresAt
```

state는 위변조 방지를 위해 서버 내부에 저장하거나 서명한다.

권장:

```text
state = random opaque id
```

그리고 서버 메모리에:

```text
stateId → pairingCode
```

매핑 저장.

---

# 3. OAuth Callback

플랫폼 OAuth Redirect URI.

```http
GET /oauth/{platform}/callback?code=...&state=...
```

예:

```text
GET /oauth/chzzk/callback?code=abc&state=xyz
```

동작:

1. state 검증
2. Pairing Code 조회
3. Authorization Code를 Token으로 교환
4. 플랫폼에서 채널/스트리머 정보 조회
5. Pairing 상태를 AUTHORIZED로 변경
6. 토큰 임시 저장
7. 성공 페이지 표시

성공 페이지 예:

```html
<h1>McStreamApi 연결 완료</h1>
<p>이 창을 닫고 Minecraft 서버로 돌아가세요.</p>
```

실패 페이지 예:

```html
<h1>McStreamApi 연결 실패</h1>
<p>인증 시간이 만료되었거나 잘못된 요청입니다.</p>
```

---

# 4. Pairing 상태 조회

플러그인이 주기적으로 호출한다.

```http
GET /api/pairing/{pairingCode}
```

Headers:

```http
X-McStreamApi-Secret: <shared-secret>
```

PENDING Response:

```json
{
  "success": true,
  "status": "PENDING"
}
```

AUTHORIZED Response:

```json
{
  "success": true,
  "status": "AUTHORIZED",
  "platform": "chzzk",
  "minecraftPlayerName": "Meeor",
  "minecraftUuid": "uuid-string",
  "channelId": "channel-id",
  "channelName": "channel-name",
  "accessToken": "access-token",
  "refreshToken": "refresh-token",
  "expiresAt": 1780000000000,
  "scope": "donation.read"
}
```

EXPIRED Response:

```json
{
  "success": false,
  "status": "EXPIRED",
  "error": "PAIRING_EXPIRED"
}
```

인증 실패 Response:

```json
{
  "success": false,
  "error": "INVALID_SHARED_SECRET"
}
```

중요:

AUTHORIZED 응답으로 토큰을 전달한 뒤, 해당 Pairing은 즉시 CONSUMED 처리하고 토큰 데이터를 삭제한다.

즉, 같은 Pairing Code로 다시 조회해도 토큰을 다시 받을 수 없어야 한다.

---

# 5. Pairing 취소

선택 기능.

```http
DELETE /api/pairing/{pairingCode}
```

Headers:

```http
X-McStreamApi-Secret: <shared-secret>
```

Response:

```json
{
  "success": true
}
```

플러그인에서 연결 시간 초과 시 호출할 수 있다.

---

## 플러그인 Polling 규칙

플러그인은 `/mca connect <platform>` 실행 후 Pairing을 등록하고, 일정 시간 동안 Polling한다.

권장:

```text
interval: 3초
timeout: 10분
```

흐름:

```text
POST /api/pairing
→ authorizeUrl 플레이어에게 출력
→ 3초마다 GET /api/pairing/{code}
→ AUTHORIZED 수신 시 토큰 저장
→ polling 종료
```

---

## Token Response 처리

플러그인이 토큰을 수신하면:

1. 응답 검증
2. platform 확인
3. minecraftUuid 확인
4. token 암호화
5. tokens/<platform>_<player>.json.enc 저장
6. 이벤트 구독 시작 또는 다음 reload 시 구독

AuthServer는 토큰 전달 후 즉시 삭제.

---

## 보안 요구사항

필수:

- HTTPS 사용
- Token 로그 출력 금지
- Authorization Code 로그 출력 금지
- Refresh Token 로그 출력 금지
- sharedSecret 로그 출력 금지
- Pairing Code 만료 처리
- Pairing Code 1회성 처리
- State 검증
- CSRF 방지
- 잘못된 플랫폼 값 차단
- Rate Limit 적용

권장 Rate Limit:

```text
POST /api/pairing: sharedSecret 기준 10회/분
GET /api/pairing/{code}: sharedSecret 기준 30회/분
GET /oauth/*/start: IP 기준 30회/분
GET /oauth/*/callback: IP 기준 30회/분
```

---

## 플랫폼 Provider 구조

AuthServer 내부 Provider 인터페이스 예:

```kotlin
interface OAuthProvider {
    val platform: String

    fun buildAuthorizeUrl(pairingCode: String, state: String): String

    suspend fun exchangeCodeForToken(code: String, state: String): OAuthToken

    suspend fun fetchChannelInfo(accessToken: String): ChannelInfo
}
```

구현:

```text
ChzzkOAuthProvider
SoopOAuthProvider
```

---

## AuthServer 설정 파일 예시

```yml
server:
  port: 18080
  publicBaseUrl: "https://auth.example.com/mca"

security:
  pairingExpireSeconds: 600
  stateExpireSeconds: 600
  enableRateLimit: true

platforms:
  chzzk:
    enabled: true
    clientId: "CHZZK_CLIENT_ID"
    clientSecret: "CHZZK_CLIENT_SECRET"
    redirectUri: "https://auth.example.com/mca/oauth/chzzk/callback"
    scopes:
      - "user:read"
    oauth:
      authorizeEndpoint: "https://chzzk.naver.com/account-interlock"
      tokenEndpoint: "https://openapi.chzzk.naver.com/auth/v1/token"
      refreshEndpoint: "https://openapi.chzzk.naver.com/auth/v1/token"
      channelInfoEndpoint: "https://openapi.chzzk.naver.com/open/v1/users/me"

  soop:
    enabled: false
    clientId: "SOOP_CLIENT_ID"
    clientSecret: "SOOP_CLIENT_SECRET"
    redirectUri: "https://auth.example.com/mca/oauth/soop/callback"
    scopes:
      - "api"
    oauth:
      authorizeEndpoint: "https://openapi.sooplive.com/auth/code"
      tokenEndpoint: "https://openapi.sooplive.com/auth/token"
      refreshEndpoint: "https://openapi.sooplive.com/auth/token"
      channelInfoEndpoint: "https://openapi.sooplive.com/user/stationinfo"
```

주의:

- clientSecret은 Git에 올리지 않는다.
- 환경변수 지원 권장.
- SOOP `GET /auth/code`는 `state`를 받지 않으므로 AuthServer는 브라우저 쿠키로 pending OAuth를 구분한다.
- 쿠키를 사용할 수 없는 환경에서는 callback 완료 페이지에서 Minecraft 인증 코드를 다시 입력받아 pending OAuth를 매칭한다.

---

## 환경변수 지원

권장:

```text
MCA_CHZZK_CLIENT_ID
MCA_CHZZK_CLIENT_SECRET

MCA_SOOP_CLIENT_ID
MCA_SOOP_CLIENT_SECRET

MCA_PUBLIC_BASE_URL
MCA_SERVER_PORT
```

우선순위:

1. 환경변수
2. config.yml

---

## 로그 정책

출력 가능:

- Pairing Code 생성
- Pairing 상태 변경
- OAuth 성공/실패
- 플랫폼 이름
- 채널 ID 일부 마스킹
- 오류 코드

출력 금지:

- accessToken
- refreshToken
- authorization code
- clientSecret
- sharedSecret

마스킹 예:

```text
channelId: abc****xyz
```

---

## 오류 코드

권장 오류 코드:

```text
INVALID_PLATFORM
PAIRING_NOT_FOUND
PAIRING_ALREADY_EXISTS
PAIRING_EXPIRED
PAIRING_CONSUMED
INVALID_STATE
OAUTH_CODE_MISSING
OAUTH_EXCHANGE_FAILED
CHANNEL_INFO_FAILED
INVALID_SHARED_SECRET
RATE_LIMITED
INTERNAL_ERROR
```

---

## 성공/실패 HTML 페이지

AuthServer는 OAuth 완료 후 간단한 HTML 페이지를 반환한다.

성공:

```text
McStreamApi 연결 완료
Minecraft 서버로 돌아가세요.
```

실패:

```text
McStreamApi 연결 실패
오류 코드와 메시지를 표시.
```

디자인은 단순하게 유지.

---

## 개발 순서 권장

Phase 1: 프로젝트 기반

1. Gradle Kotlin 프로젝트 생성
2. Ktor Netty 서버 설정
3. Ktor buildFatJar 설정
4. `--version`, `--config`, `--check-config` CLI 옵션 구현
5. config.yml 로딩
6. config.yml 없을 때 기본 파일 생성 후 종료
7. 설정 검증 및 플랫폼별 활성/비활성 판정

Phase 2: 서버 공통 기능

1. JSON 직렬화 설정
2. 공통 ErrorResponse
3. requestId middleware
4. LogMasker
5. SharedSecretValidator
6. RateLimiter
7. `/health`, `/ready`
8. graceful shutdown

Phase 3: Pairing API

1. PairingSession 모델
2. InMemoryPairingStore
3. PairingService 상태 전이
4. `POST /api/pairing`
5. `GET /api/pairing/{code}`
6. `DELETE /api/pairing/{code}`
7. Token 1회 consume 보장

Phase 4: OAuth 공통

1. StateStore
2. OAuthProvider 인터페이스
3. `/oauth/{platform}/start`
4. `/oauth/{platform}/callback`
5. 성공/실패 HTML 페이지
6. OAuth 실패 시 Pairing FAILED 전환

Phase 5: 플랫폼 Provider

1. Chzzk 공식 API 문서 확인
2. ChzzkOAuthProvider 구현
3. SOOP 공식 API 문서 확인
4. SoopOAuthProvider 구현
5. 플랫폼별 token/channel DTO를 공통 모델로 변환

Phase 6: 운영 안정화

1. 만료 Pairing/State 정리 작업
2. 로그 마스킹 검증
3. rate limit 검증
4. Nginx reverse proxy 테스트
5. Windows/Linux 실행 문서
6. 배포 jar 검증

---

## McStreamApi Plugin과의 계약

Plugin은 AuthServer에 다음 기능만 기대한다.

1. Pairing 등록
2. OAuth 승인 URL 제공
3. Pairing 상태 조회
4. Token 1회 수령

AuthServer는 다음 기능을 하지 않는다.

- 후원 이벤트 수신
- Minecraft 서버 명령 실행
- Token 영구 저장
- Reward 처리
- Random 처리

---

## 최종 목표

McStreamApi AuthServer는 OAuth 인증만 안전하게 중계하는 경량 서버이다.
복잡한 후원 처리와 보상 로직은 McStreamApi Plugin이 담당하며,
AuthServer는 공식 플랫폼 인증 과정에서 필요한 Redirect/Token 교환/Pairing 매칭만 수행한다.

---

## MVP 범위

1차 MVP 포함:

- Ktor 기반 HTTP 서버
- YAML 설정 로딩
- 환경변수 override
- In-Memory Pairing 저장소
- In-Memory OAuth state 저장소
- sharedSecret 인증
- Pairing 등록 API
- Pairing 상태 조회 API
- OAuth start/callback route
- Chzzk OAuth Provider
- SOOP OAuth Provider
- 성공/실패 HTML 페이지
- 민감정보 로그 마스킹
- 기본 Rate Limit

1차 MVP 제외:

- Redis 저장소
- 관리자 웹 페이지
- Prometheus metrics
- 여러 Minecraft 서버를 구분하는 멀티테넌트 관리 UI
- AuthServer가 직접 후원 이벤트를 수신하는 기능

---

## 권장 프로젝트 구조

```text
McStreamApi-AuthServer/
  build.gradle.kts
  settings.gradle.kts

  src/main/kotlin/kr/meeor/mcstreamapi/authserver/
    Application.kt

    config/
      AppConfig.kt
      ConfigLoader.kt
      PlatformConfig.kt

    security/
      SharedSecretValidator.kt
      StateStore.kt
      RateLimiter.kt
      LogMasker.kt

    pairing/
      PairingSession.kt
      PairingStatus.kt
      PairingStore.kt
      InMemoryPairingStore.kt
      PairingService.kt

    provider/
      OAuthProvider.kt
      OAuthToken.kt
      ChannelInfo.kt
      ChzzkOAuthProvider.kt
      SoopOAuthProvider.kt

    route/
      PairingRoutes.kt
      OAuthRoutes.kt
      HtmlPages.kt

    dto/
      PairingRegisterRequest.kt
      PairingRegisterResponse.kt
      PairingStatusResponse.kt
      ErrorResponse.kt

  src/main/resources/
    config.example.yml
    logback.xml
```

---

## 서버 실행 방식

권장:

```text
java -jar McStreamApi-AuthServer.jar
```

지원 실행 모드:

```text
java -jar McStreamApi-AuthServer.jar
java -jar McStreamApi-AuthServer.jar --config ./config.yml
java -jar McStreamApi-AuthServer.jar --check-config
java -jar McStreamApi-AuthServer.jar --version
```

실행 모드 설명:

```text
기본 실행:
  config.yml을 읽고 HTTP 서버를 시작한다.

--config <path>:
  지정한 설정 파일을 사용한다.

--check-config:
  설정 파일을 검증한 뒤 서버를 시작하지 않고 종료한다.
  CI, 배포 전 점검, 운영 서버 설정 확인에 사용한다.

--version:
  버전, 빌드 시간, Git commit 정보를 출력하고 종료한다.
```

기본 바인딩:

```text
host: 127.0.0.1
port: 18080
```

외부 공개는 Nginx HTTPS reverse proxy를 통해서만 수행한다.

운영 환경에서 권장하지 않음:

- AuthServer를 직접 `0.0.0.0:18080`으로 공개
- HTTP 공개 URL을 OAuth Redirect URI로 등록
- accessToken/refreshToken을 stdout에 출력

---

## Jar 배포 목표

AuthServer는 단일 실행 jar로 배포한다.

배포 산출물:

```text
McStreamApi-AuthServer.jar
config.example.yml
README 또는 운영 문서
LICENSE
```

권장 jar 특성:

- Java 21에서 실행 가능
- 외부 WAS 없이 자체 HTTP 서버로 동작
- `java -jar`만으로 실행 가능
- fat jar 또는 shadow jar 형태
- 빌드 결과물에 실제 `config.yml` 포함 금지
- `config.example.yml`에는 placeholder 값만 포함

빌드 명령 예:

```text
./gradlew :auth-server:buildFatJar
```

Windows:

```text
gradlew.bat :auth-server:buildFatJar
```

산출 위치 예:

```text
build/libs/McStreamApi-AuthServer-<version>.jar
```

---

## 최초 실행 정책

AuthServer가 실행될 때 설정 파일이 없으면 `config.example.yml` 기반으로 `config.yml`을 생성하고 즉시 종료한다.

동작:

1. `config.yml` 존재 여부 확인.
2. 없으면 기본 `config.yml` 생성.
3. 콘솔에 설정 필요 메시지 출력.
4. HTTP 서버를 시작하지 않고 종료.

콘솔 메시지 예:

```text
[McStreamApi-AuthServer] config.yml 파일이 없어 기본값으로 새로 만들었습니다.
[McStreamApi-AuthServer] publicBaseUrl, sharedSecret, clientId, clientSecret, redirectUri를 설정해주세요.
[McStreamApi-AuthServer] 설정 완료 후 서버를 다시 실행해주세요.
```

`--check-config`에서도 config가 없으면 같은 방식으로 생성하고 실패 종료한다.

---

## 설정 파일 전체 예시

AuthServer도 공개 저장소에는 `config.example.yml`만 포함한다.

실제 운영 파일:

```text
McStreamApi-AuthServer/config.yml
```

Git 포함 파일:

```text
McStreamApi-AuthServer/config.example.yml
```

현재 기획 루트에서는 AuthServer 샘플을 `auth-server.config.example.yml`로 둔다.
실제 AuthServer 프로젝트를 분리할 때 `McStreamApi-AuthServer/config.example.yml`로 옮긴다.

예시:

```yml
server:
  host: "127.0.0.1"
  port: 18080
  publicBaseUrl: "https://auth.example.com/mca"
  allowInsecureLocalhost: false

security:
  sharedSecret: "CHANGE_ME_RANDOM_LONG_SECRET"
  pairingExpireSeconds: 600
  stateExpireSeconds: 600
  enableRateLimit: true
  trustedProxyHeaders: true

http:
  requestTimeoutSeconds: 15
  shutdownTimeoutSeconds: 10

cleanup:
  intervalSeconds: 60
  expiredSessionRetainSeconds: 300
  consumedSessionRetainSeconds: 60
  failedSessionRetainSeconds: 600

rateLimit:
  apiPairingCreatePerMinute: 10
  apiPairingPollPerMinute: 30
  oauthStartPerMinute: 30
  oauthCallbackPerMinute: 30

platforms:
  chzzk:
    enabled: true
    clientId: "CHZZK_CLIENT_ID"
    clientSecret: "CHZZK_CLIENT_SECRET"
    redirectUri: "https://auth.example.com/mca/oauth/chzzk/callback"
    scopes:
      - "user:read"
    oauth:
      authorizeEndpoint: "https://chzzk.naver.com/account-interlock"
      tokenEndpoint: "https://openapi.chzzk.naver.com/auth/v1/token"
      refreshEndpoint: "https://openapi.chzzk.naver.com/auth/v1/token"
      channelInfoEndpoint: "https://openapi.chzzk.naver.com/open/v1/users/me"

  soop:
    enabled: true
    clientId: "SOOP_CLIENT_ID"
    clientSecret: "SOOP_CLIENT_SECRET"
    redirectUri: "https://auth.example.com/mca/oauth/soop/callback"
    scopes:
      - "api"
    oauth:
      authorizeEndpoint: "https://openapi.sooplive.com/auth/code"
      tokenEndpoint: "https://openapi.sooplive.com/auth/token"
      refreshEndpoint: "https://openapi.sooplive.com/auth/token"
      channelInfoEndpoint: "https://openapi.sooplive.com/user/stationinfo"

logging:
  level: "INFO"
  maskSensitiveValues: true
```

환경변수 우선순위:

1. 명시적 환경변수
2. `${ENV_NAME}` placeholder 치환
3. config.yml 값
4. 코드 기본값

필수 값 누락 시 서버는 기동 실패해야 한다.

---

## 설정 검증 정책

AuthServer는 기동 전에 config.yml을 검증한다.

서버 전체 기동 실패 조건:

```text
server.publicBaseUrl 비어 있음
server.publicBaseUrl 이 http:// 로 시작함 (운영 모드)
security.sharedSecret 비어 있음
security.sharedSecret 이 CHANGE_ME_RANDOM_LONG_SECRET 기본값
security.sharedSecret 길이 32자 미만
server.port 가 1~65535 범위를 벗어남
모든 플랫폼이 비활성화됨
```

플랫폼별 비활성화 조건:

```text
platforms.<platform>.enabled=true 이지만 clientId 없음
platforms.<platform>.enabled=true 이지만 clientSecret 없음
platforms.<platform>.enabled=true 이지만 redirectUri 없음
platforms.<platform>.clientId 가 example placeholder
platforms.<platform>.clientSecret 이 example placeholder
platforms.<platform>.redirectUri 와 server.publicBaseUrl origin/path 정책 불일치
```

처리 방식:

- 특정 플랫폼 설정만 잘못되면 해당 플랫폼만 런타임에서 비활성화한다.
- 비활성화된 플랫폼은 `/oauth/{platform}/start` 요청 시 `INVALID_PLATFORM`을 반환한다.
- 콘솔에는 어떤 플랫폼이 왜 비활성화되었는지 출력한다.
- 실제 config.yml 파일을 자동으로 수정하지 않는다.
- 모든 플랫폼이 비활성화되면 AuthServer 기동 실패.

콘솔 메시지 예:

```text
[McStreamApi-AuthServer] Chzzk 설정이 완료되지 않아 chzzk OAuth가 비활성화되었습니다. reason=CLIENT_ID_MISSING
[McStreamApi-AuthServer] SOOP 설정이 완료되지 않아 soop OAuth가 비활성화되었습니다. reason=CLIENT_SECRET_PLACEHOLDER
[McStreamApi-AuthServer] 활성화 가능한 플랫폼이 없어 서버를 시작할 수 없습니다.
```

개발 모드 예외:

- 로컬 개발에서만 `http://localhost`, `http://127.0.0.1` publicBaseUrl 허용 가능.
- 이 예외는 `server.allowInsecureLocalhost: true` 같은 명시 설정이 있을 때만 허용한다.
- 운영 기본값은 HTTPS 필수이다.

---

## sharedSecret 모델

AuthServer는 한 개 이상의 sharedSecret을 허용할 수 있다.

MVP:

```yml
security:
  sharedSecret: "CHANGE_ME_RANDOM_LONG_SECRET"
```

확장:

```yml
security:
  sharedSecrets:
    - id: "main"
      secret: "CHANGE_ME_MAIN"
    - id: "test"
      secret: "CHANGE_ME_TEST"
```

1차 구현은 단일 sharedSecret으로 충분하다.

sharedSecret 생성:

```text
python generate_shared_secret.py
```

생성한 값을 Plugin과 AuthServer 설정에 동일하게 넣는다.

Plugin:

```yml
auth:
  sharedSecret: "mca_GENERATED_RANDOM_SECRET_HERE"
```

AuthServer:

```yml
security:
  sharedSecret: "mca_GENERATED_RANDOM_SECRET_HERE"
```

비교 방식:

- constant-time compare 권장
- 누락 또는 불일치 시 `INVALID_SHARED_SECRET`
- 실패 로그에는 secret 값 출력 금지

기동 시 검증:

- `sharedSecret`이 비어 있으면 기동 실패.
- `CHANGE_ME_RANDOM_LONG_SECRET` 기본값이면 기동 실패.
- 길이가 32자 미만이면 기동 실패.
- 실패 메시지에는 실제 secret 값을 출력하지 않는다.

---

## PairingSession 데이터 모델

```kotlin
data class PairingSession(
    val pairingCode: String,
    val platform: String,
    val minecraftPlayerName: String,
    val minecraftUuid: String,
    val callbackBaseUrl: String?,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
    var status: PairingStatus,
    var stateId: String? = null,
    var authorizedToken: OAuthToken? = null,
    var channelInfo: ChannelInfo? = null,
    var failureCode: String? = null,
    var failureMessage: String? = null
)
```

Token 관련 필드는 `AUTHORIZED` 상태에서만 존재한다.

`CONSUMED`, `EXPIRED`, `FAILED` 상태에서는 Token 필드가 없어야 한다.

---

## 상태 전이

정상 흐름:

```text
PENDING -> AUTHORIZED -> CONSUMED
```

만료 흐름:

```text
PENDING -> EXPIRED
AUTHORIZED -> EXPIRED
```

실패 흐름:

```text
PENDING -> FAILED
AUTHORIZED -> FAILED
```

금지:

```text
CONSUMED -> AUTHORIZED
EXPIRED -> AUTHORIZED
FAILED -> AUTHORIZED
```

Token 전달 원칙:

- `GET /api/pairing/{code}`가 `AUTHORIZED`를 반환하는 순간 Token을 응답에 포함한다.
- 응답 생성 직후 Session은 `CONSUMED`로 전환한다.
- 메모리의 Token 값은 즉시 null 처리한다.
- 이후 같은 code 조회는 `PAIRING_CONSUMED`를 반환한다.

---

## API 공통 규칙

Content-Type:

```http
application/json
```

성공 응답:

```json
{
  "success": true
}
```

실패 응답:

```json
{
  "success": false,
  "error": "ERROR_CODE",
  "message": "Human-readable message."
}
```

HTTP status 권장:

```text
200 OK                  정상 상태 조회, OAuth 성공 HTML
201 Created             Pairing 생성 성공
400 Bad Request          잘못된 요청 형식
401 Unauthorized         sharedSecret 누락/불일치
404 Not Found            Pairing 없음
409 Conflict             이미 존재하거나 이미 소비됨
410 Gone                 만료됨
429 Too Many Requests    Rate Limit
500 Internal Server Error 내부 오류
502 Bad Gateway          플랫폼 API 오류
```

오류 응답의 `message`에는 민감정보를 포함하지 않는다.

### Plugin 표시 메시지 매핑

AuthServer는 API 오류 코드와 짧은 message를 반환한다.
Plugin은 이 값을 그대로 노출하지 않고 Player용 메시지로 변환한다.

권장 매핑:

```text
INVALID_SHARED_SECRET
Plugin Player Message: 인증서버와의 통신 중 에러가 발생했습니다. 관리자에게 문의해주세요. (시크릿 코드 오류)
Plugin Console Log: AuthServer rejected sharedSecret. Check both config.yml files.

PAIRING_NOT_FOUND
Plugin Player Message: 인증 요청을 찾을 수 없습니다. 다시 연결을 시도해주세요.
Plugin Console Log: Pairing not found. code=<pairingCode>

PAIRING_EXPIRED
Plugin Player Message: 인증 시간이 만료되었습니다. 다시 연결을 시도해주세요.
Plugin Console Log: Pairing expired. code=<pairingCode>

PAIRING_CONSUMED
Plugin Player Message: 이미 처리된 인증 요청입니다. 다시 연결을 시도해주세요.
Plugin Console Log: Pairing already consumed. code=<pairingCode>

INVALID_PLATFORM
Plugin Player Message: 지원하지 않는 플랫폼입니다.
Plugin Console Log: Invalid platform requested. platform=<platform>

RATE_LIMITED
Plugin Player Message: 요청이 너무 많습니다. 잠시 후 다시 시도해주세요.
Plugin Console Log: AuthServer rate limited the request. endpoint=<endpoint>

INTERNAL_ERROR
Plugin Player Message: 인증서버 내부 오류가 발생했습니다. 관리자에게 문의해주세요.
Plugin Console Log: AuthServer internal error. requestId=<requestId>
```

네트워크 레벨 실패:

```text
Connection refused / DNS failed / No route
Plugin Player Message: 인증서버가 응답하지 않습니다. 관리자에게 문의해주세요.
Plugin Console Log: AuthServer unreachable. url=<serverBaseUrl> cause=<exception>

Timeout
Plugin Player Message: 인증서버 응답 시간이 초과되었습니다. 잠시 후 다시 시도해주세요.
Plugin Console Log: AuthServer request timed out. endpoint=<endpoint>

Malformed JSON response
Plugin Player Message: 인증서버 응답을 처리할 수 없습니다. 관리자에게 문의해주세요.
Plugin Console Log: Invalid AuthServer response. status=<httpStatus> body=<maskedBody>
```

---

## 요청 검증 규칙

`pairingCode`:

- 6~16자
- 대문자 영문 + 숫자
- 공백 불가
- 권장 제외 문자: `O`, `0`, `I`, `1`

`platform`:

- 등록된 Provider 중 enabled=true인 값만 허용
- 소문자 canonical 값 사용

`minecraftPlayerName`:

- 3~16자
- Minecraft Java Edition 이름 규칙 기준
- 정규식 예: `^[A-Za-z0-9_]{3,16}$`

`minecraftUuid`:

- UUID 형식 권장
- 하이픈 포함/미포함 입력을 모두 허용할지 Plugin과 합의 필요

`callbackBaseUrl`:

- 선택 필드
- 제공되면 AuthServer 설정의 publicBaseUrl과 같은 origin인지 검증 권장
- MVP에서는 AuthServer 설정의 publicBaseUrl을 우선 사용

---

## API 상세 보강

### GET /health

서버 프로세스가 살아 있는지 확인하는 endpoint.

인증:

```text
없음
```

Response:

```json
{
  "status": "UP",
  "service": "McStreamApi-AuthServer",
  "version": "1.0.0"
}
```

용도:

- systemd, Docker, Nginx upstream health check
- 단순 프로세스 생존 확인

주의:

- 플랫폼 clientId/clientSecret 설정 상태를 상세히 노출하지 않는다.
- token, sharedSecret, 내부 설정 값을 반환하지 않는다.

### GET /ready

서버가 실제 OAuth 요청을 처리할 준비가 되었는지 확인하는 endpoint.

인증:

```text
없음
```

Response:

```json
{
  "status": "READY",
  "enabledPlatforms": [
    "chzzk",
    "soop"
  ]
}
```

준비되지 않은 경우:

```json
{
  "status": "NOT_READY",
  "enabledPlatforms": []
}
```

주의:

- 상세 실패 사유는 콘솔 로그에만 남긴다.
- 외부 응답에는 민감한 설정 상태를 과하게 노출하지 않는다.

### POST /api/pairing

추가 검증:

- sharedSecret 필수
- enabled=false 플랫폼 거부
- 같은 Pairing Code가 active 상태면 거부
- 만료된 같은 code는 정리 후 재사용 허용 가능

성공 응답은 `201 Created` 권장.

```json
{
  "success": true,
  "pairingCode": "A7K29Q",
  "status": "PENDING",
  "expiresInSeconds": 600,
  "authorizeUrl": "https://auth.example.com/mca/oauth/chzzk/start?pairingCode=A7K29Q"
}
```

### GET /api/pairing/{pairingCode}

`PENDING`:

```json
{
  "success": true,
  "status": "PENDING",
  "expiresInSeconds": 542
}
```

`AUTHORIZED`:

```json
{
  "success": true,
  "status": "AUTHORIZED",
  "platform": "chzzk",
  "minecraftPlayerName": "Meeor",
  "minecraftUuid": "uuid-string",
  "channelId": "channel-id",
  "channelName": "channel-name",
  "accessToken": "access-token",
  "refreshToken": "refresh-token",
  "expiresAt": 1780000000000,
  "scope": "donation.read"
}
```

응답 후 내부 상태:

```text
status = CONSUMED
authorizedToken = null
channelInfo = null
```

`CONSUMED`:

```json
{
  "success": false,
  "status": "CONSUMED",
  "error": "PAIRING_CONSUMED",
  "message": "Pairing token was already consumed."
}
```

### DELETE /api/pairing/{pairingCode}

권장 동작:

- `PENDING` 또는 `AUTHORIZED` 상태만 취소 허용
- Token이 있으면 즉시 null 처리
- 삭제 성공은 멱등적으로 처리 가능

---

## OAuth State 관리

권장 모델:

```kotlin
data class OAuthState(
    val stateId: String,
    val pairingCode: String,
    val platform: String,
    val createdAtMillis: Long,
    val expiresAtMillis: Long
)
```

State 생성:

- cryptographically secure random
- URL-safe base64 또는 UUID v4 이상 수준
- Pairing Code를 그대로 state에 넣지 않는다.

검증:

- state 존재 여부
- state 만료 여부
- route의 `{platform}`과 state.platform 일치 여부
- Pairing 상태가 PENDING인지 확인

검증 성공 후:

- state는 즉시 삭제한다.
- 같은 callback 재사용을 막는다.

---

## OAuth Callback 세부 실패 처리

실패 페이지에 표시 가능:

- 오류 코드
- 짧은 설명
- "Minecraft 서버에서 다시 시도하세요."

표시 금지:

- authorization code
- accessToken
- refreshToken
- clientSecret
- 내부 stack trace

OAuth 실패 시 PairingSession:

```text
status = FAILED
failureCode = 오류 코드
failureMessage = 짧은 메시지
authorizedToken = null
```

Plugin이 이후 polling하면 FAILED 상태를 받을 수 있어야 한다.

---

## Provider 계약

```kotlin
interface OAuthProvider {
    val platform: String

    fun buildAuthorizeUrl(state: String): String

    suspend fun exchangeCodeForToken(code: String, state: String): OAuthToken

    suspend fun fetchChannelInfo(accessToken: String): ChannelInfo
}
```

공통 Token 모델:

```kotlin
data class OAuthToken(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long,
    val scope: String?
)
```

공통 채널 모델:

```kotlin
data class ChannelInfo(
    val channelId: String,
    val channelName: String
)
```

Provider 구현 원칙:

- 플랫폼별 DTO는 Provider 내부에 숨긴다.
- 외부 route/service는 공통 모델만 사용한다.
- HTTP timeout을 명시한다.
- 플랫폼 오류 응답은 `OAUTH_EXCHANGE_FAILED` 또는 `CHANNEL_INFO_FAILED`로 변환한다.
- Token 값은 예외 메시지에 포함하지 않는다.

---

## SOOP Provider 구현 메모

2026-06-10 공식 SOOP OpenAPI iframe 문서 기준으로 AuthServer OAuth Provider endpoint를 확정했다.

확정값:

- API base: `https://openapi.sooplive.com`
- authorize endpoint: `GET /auth/code`
- token endpoint: `POST /auth/token`
- refresh endpoint: `POST /auth/token`
- channel info endpoint: `POST /user/stationinfo`
- token expires field: `expires_in`, 예시값 `28800`
- token response: `access_token`, `refresh_token`, `token_type`, `scope`
- station info response: `result`, `msg`, `data.user_nick`, `data.station_name`

주의:

- SOOP `GET /auth/code`는 문서상 `state` 파라미터를 받지 않는다.
- AuthServer는 SOOP OAuth 요청도 여러 건 pending으로 허용하되, state 없이 돌아온 callback은 AuthServer가 발급한 브라우저 쿠키로 매칭한다.
- 쿠키가 없거나 브라우저가 바뀐 경우에는 callback 완료 페이지에서 Minecraft 인증 코드를 입력받아 해당 pending state와 매칭한다.
- `POST /user/stationinfo`에는 고유 채널 ID 필드가 없어 현재 구현은 `user_nick`을 `ChannelInfo.channelId` 기준값으로 사용한다.
- 공개 문서에서 endpoint별 rate limit 정량값은 확인하지 못했다.

## Chzzk Provider 구현 메모

2026-06-10 공식 CHZZK GitBook 기준으로 AuthServer OAuth Provider endpoint를 확정했다.

확정값:

- authorize endpoint: `https://chzzk.naver.com/account-interlock`
- token endpoint: `https://openapi.chzzk.naver.com/auth/v1/token`
- refresh endpoint: `https://openapi.chzzk.naver.com/auth/v1/token`
- channel info endpoint: `https://openapi.chzzk.naver.com/open/v1/users/me`
- Access Token 만료기간: 1일
- Refresh Token 만료기간: 30일
- common error: `INVALID_CLIENT`, `INVALID_TOKEN`, `FORBIDDEN`, `NOT_FOUND`, `TOO_MANY_REQUESTS`

공식 문서상 `GET /open/v1/users/me` Scope는 유저 정보 조회이다. config 예시는 내부 추적용으로 `user:read`를 사용하지만, 개발자 센터에 표시되는 실제 scope 문자열이 다르면 운영 config에서 공식 값으로 맞춘다.

---

## Rate Limit 설계

MVP는 In-Memory fixed-window 또는 token-bucket으로 충분하다.

Key 기준:

```text
POST /api/pairing          sharedSecret hash
GET /api/pairing/{code}    sharedSecret hash
GET /oauth/*/start         client IP
GET /oauth/*/callback      client IP
```

Nginx 뒤에서 IP를 사용할 때:

- `X-Forwarded-For`는 trusted proxy 환경에서만 신뢰한다.
- 직접 공개 서버라면 remote address를 사용한다.

Rate limit 초과 응답:

```json
{
  "success": false,
  "error": "RATE_LIMITED",
  "message": "Too many requests."
}
```

---

## 백그라운드 정리 작업

주기적으로 정리:

- 만료된 PairingSession
- 만료된 OAuthState
- CONSUMED 상태의 오래된 Session
- FAILED 상태의 오래된 Session

권장 보존 시간:

```text
EXPIRED: 5분
CONSUMED: 1분
FAILED: 10분
```

Token 필드는 상태 전환 즉시 삭제하고, 보존 시간 동안 남기지 않는다.

---

## Graceful Shutdown

AuthServer는 종료 신호를 받으면 안전하게 종료한다.

대상 신호:

```text
SIGTERM
SIGINT
```

종료 절차:

1. 새 HTTP 요청 수신 중단.
2. 진행 중인 callback/token 교환 요청은 짧은 timeout 안에서 완료 시도.
3. 정리 작업 coroutine 중지.
4. In-Memory PairingSession의 Token 필드를 즉시 null 처리.
5. HTTP 서버 종료.

주의:

- AuthServer는 Token을 장기 저장하지 않으므로 재시작 시 진행 중이던 Pairing은 사라진다.
- 재시작 후 사용자는 `/mca connect <platform>`을 다시 실행해야 한다.
- 종료 로그에도 token, authorization code, sharedSecret을 출력하지 않는다.

---

## Request ID

모든 API 요청에는 requestId를 부여한다.

규칙:

- 클라이언트가 `X-Request-Id`를 보내면 안전한 길이/문자만 검증 후 사용.
- 없으면 서버에서 새 UUID를 생성.
- 모든 오류 응답에 requestId를 포함.
- 모든 로그에 requestId를 포함.

오류 응답 예:

```json
{
  "success": false,
  "error": "INTERNAL_ERROR",
  "message": "Internal server error.",
  "requestId": "f8a0e93a-1f43-43a4-bd0d-2a65f2c0f4c9"
}
```

목적:

- 운영자가 Player 제보와 서버 로그를 매칭할 수 있게 한다.
- stack trace를 사용자에게 노출하지 않고도 문제를 추적할 수 있게 한다.

---

## 로그 예시

가능:

```text
INFO Pairing created platform=chzzk code=A7K29Q player=Meeor expiresIn=600
INFO OAuth authorized platform=chzzk code=A7K29Q channelId=abc****xyz
INFO Pairing consumed platform=chzzk code=A7K29Q player=Meeor
WARN OAuth failed platform=chzzk code=A7K29Q error=OAUTH_EXCHANGE_FAILED
WARN Invalid shared secret remote=127.0.0.1
```

금지:

```text
accessToken=...
refreshToken=...
code=oauth-authorization-code-value
clientSecret=...
X-McStreamApi-Secret=...
```

---

## 운영 배포 체크리스트

서버:

- Java 21 설치
- AuthServer jar 배치
- config.yml 작성
- 환경변수 설정
- systemd 또는 Windows 서비스 등록
- `127.0.0.1:18080` 바인딩 확인

Nginx:

- HTTPS 인증서 적용
- `/mca/` reverse proxy
- `X-Forwarded-*` 헤더 설정
- request body size 기본값 유지
- access log에 query string/token이 과도하게 남지 않는지 확인

플랫폼 개발자 콘솔:

- Redirect URI 등록
- clientId/clientSecret 발급
- 필요한 scope 승인
- 서비스 URL 검수 조건 확인

Minecraft 서버:

- Plugin `config.yml auth.sharedSecret`과 AuthServer `config.yml security.sharedSecret` 일치
- Plugin `config.yml auth.serverBaseUrl` 확인
- `/mca connect chzzk` 테스트

---

## Linux systemd 예시

```ini
[Unit]
Description=McStreamApi AuthServer
After=network.target

[Service]
WorkingDirectory=/opt/mcstreamapi-authserver
ExecStart=/usr/bin/java -jar /opt/mcstreamapi-authserver/McStreamApi-AuthServer.jar --config /opt/mcstreamapi-authserver/config.yml
Restart=on-failure
RestartSec=5
User=mcstreamapi

[Install]
WantedBy=multi-user.target
```

주의:

- `config.yml` 권한은 운영 계정만 읽을 수 있게 제한한다.
- 로그에 token/clientSecret/sharedSecret이 출력되지 않는지 확인한다.
- 외부 공개는 systemd가 아니라 Nginx HTTPS reverse proxy가 담당한다.

---

## Windows 실행 예시

개발 또는 수동 실행:

```text
java -jar McStreamApi-AuthServer.jar --config .\config.yml
```

PowerShell:

```powershell
java -jar .\McStreamApi-AuthServer.jar --config .\config.yml
```

운영 환경에서는 NSSM, Windows Service Wrapper, 작업 스케줄러 등으로 서비스화할 수 있다.

주의:

- 콘솔 창을 닫으면 프로세스가 종료될 수 있다.
- `config.yml`을 공개 폴더에 두지 않는다.
- 방화벽에서 외부 18080 직접 접근을 열지 않는다.

---

## 로컬 개발 시나리오

권장:

```text
AuthServer: http://127.0.0.1:18080
Public tunnel: ngrok 또는 Cloudflare Tunnel
Redirect URI: tunnel URL + /mca/oauth/chzzk/callback
```

주의:

- 로컬 테스트용 clientSecret을 Git에 커밋하지 않는다.
- tunnel URL 변경 시 AuthServer publicBaseUrl과 플랫폼 Redirect URI를 함께 변경한다.
- 로그 레벨 DEBUG에서도 Token 값을 출력하지 않는다.

---

## 테스트 기준

단위 테스트:

- ConfigLoader
- ConfigValidator
- PairingService 상태 전이
- Pairing 만료 처리
- Token consume 1회성
- StateStore 생성/검증/삭제
- SharedSecretValidator
- LogMasker
- RateLimiter
- RequestId 생성/전파

통합 테스트:

- config.yml 없을 때 기본 config 생성 후 종료
- placeholder sharedSecret이면 기동 실패
- 플랫폼 설정 누락 시 해당 플랫폼 비활성화
- 모든 플랫폼 비활성화 시 기동 실패
- `--check-config` 성공/실패
- `/health`
- `/ready`
- POST /api/pairing 성공
- 중복 Pairing Code 거부
- sharedSecret 누락/오류
- OAuth start가 Provider authorize URL로 redirect
- callback 성공 시 AUTHORIZED 전환
- polling 1회차 Token 반환
- polling 2회차 PAIRING_CONSUMED
- callback 실패 시 FAILED 전환

수동 테스트:

- buildFatJar 산출물 실행
- Nginx reverse proxy 경유
- HTTPS Redirect URI
- Pairing timeout
- 플랫폼 API 오류
- AuthServer 재시작 시 Pairing 초기화
- SIGTERM graceful shutdown

---

## 결정 필요 항목

구현 전 사용자 결정이 필요한 항목:

- 공개 AuthServer 도메인
- Chzzk 개발자 앱 clientId/clientSecret 준비 여부
- 로컬 개발용 터널 도구를 사용할지
- SOOP OAuth/후원 API 세부 endpoint와 scope 확인
- 배포 jar 파일명과 버전 표기 방식
- Linux systemd 배포를 공식 문서에 포함할지
- Windows 서비스 등록 방식까지 공식 지원할지, 수동 실행 예시만 둘지
