# McStreamApi - Project Specification

## 개요
McStreamApi는 치지직/SOOP 등의 공식 OAuth API를 사용하여 스트리머 계정을 Minecraft 플레이어와 연결하고,
후원 이벤트 발생 시 Minecraft 서버 내 보상을 실행하는 Spigot 플러그인이다.

## 프로젝트 구성

### McStreamApi (Plugin)
- Pairing Code 생성
- OAuth 연결 요청
- 토큰 수신 및 암호화 저장
- 후원 이벤트 처리
- 보상 실행
- Config 관리

### McStreamApi-AuthServer
- OAuth Redirect 처리
- Authorization Code → Token 교환
- Pairing Code 매칭
- 토큰 1회 전달
- 전달 후 즉시 삭제

※ Auth Server는 토큰을 장기 저장하지 않는다.

---

## 지원 플랫폼

우선 구현:
- SOOP

다음 구현:
- Chzzk

향후:
- YouTube
- Twitch
- Kick

---

## Minecraft 지원 버전

초기:
- Minecraft 1.21.11
- Spigot 기반 제작
- Paper 호환

향후:
- 이후 버전

버전 의존 기능은 Adapter 구조로 분리.

---

## 명령어

### 연결

/mca connect <chzzk|soop>

동작:
1. Pairing Code 생성
2. 승인 URL 생성
3. OAuth 승인
4. Token 수신
5. 암호화 저장

### 리로드

/mca reload

### 테스트

/mca apply <player> <amount>

예:
/mca apply Meeor 1000

특징:
- 실제 후원 이벤트 파이프라인 사용
- 연결된 플레이어만 자동완성
- amount는 config 등록 금액 자동완성

---

## 제거된 명령어

제공하지 않음:

- /mca list
- /mca remove

토큰 관리는 서버 파일 접근 권한자만 가능.

---

## 토큰 저장

plugins/McStreamApi/
- config.yml
- Api.yml
- random.yml
- custom-item.yml
- secret.key
- tokens/

예:
- chzzk_Meeor.json.enc
- soop_Meeor.json.enc

파일명:
<platform>_<player>.json.enc

토큰은 반드시 암호화 저장.

---

## 플레이어 매칭

/mca connect 를 실행한 플레이어와 스트리머 계정을 연결.

예:
Meeor → /mca connect soop

후원 발생 시 기본 대상:
Meeor

---

## 플레이스홀더

{player}
{player_uuid}

{platform}

{streamer}
{streamerId}

{donator}
{donatorId}

{amount}
{message}

---

## 액션 타입

- cmd
- give
- chat
- broadcast
- title

---

## CMD

기본 실행 주체:
Console

특수 처리:

@s → 연결된 플레이어

예:

give @s diamond 1

→

give Meeor diamond 1

---

## CHAT

특정 플레이어 메시지

target 기본값:
@s

---

## BROADCAST

전체 서버 메시지

---

## TITLE

타이틀 출력

target 기본값:
@s

---

## GIVE

간단한 즉석 지급용:

- name
- lore

복잡한 ItemMeta는 `custom-item.yml`에 정의하고 `custom-give`로 참조한다.

## CUSTOM-GIVE

`Api.yml`에서는 아래처럼 짧게 참조한다.

```yml
- type: "custom-give"
  target: "@s"
  item: "{item.donation_diamond}"
```

`custom-item.yml` 지원:

- material
- amount
- name
- lore
- customModelData
- unbreakable
- enchantments
- glow/glint
- itemFlags
- persistentData / pdc / itemTag
- playerHead
- customPotionEffects
- book title/author/pages
- attributes

추후:

- rawNbt

---

## Reward

Reward 하나는 여러 Action 보유 가능.

실행 순서:
위 → 아래

Reward는 선택 확률 가중치 `chance`를 가질 수 있다.

`chance`가 없으면 기본값은 `100`이다.

---

## Reward Amount 규칙

amount 사용.

지원 문법:

1000
= 정확히 1000원

1000-5000
= 1000원 ~ 5000원

1000+
= 1000원 이상

후원 1회당 Reward는 1개만 실행.

우선순위:

1. Exact Match
2. Range Match
3. Plus Match

예:

amount: 1000
amount: 1000-5000
amount: 1000+

후원 1000:
→ Exact 실행

후원 3000:
→ Range 실행

후원 10000:
→ Plus 실행

---

## Reward Chance 규칙

동일 amount 우선순위에서 여러 Reward가 매칭될 수 있다.
이때 `chance` 값을 가중치로 사용해 실행할 Reward 1개를 선택한다.

예:

```yml
rewards:
  chzzk:
    - id: "monster_normal"
      amount: "5000+"
      chance: 70
      actions:
        - type: "cmd"
          command: "summon zombie {player}"

    - id: "monster_rare"
      amount: "5000+"
      chance: 30
      actions:
        - type: "cmd"
          command: "summon creeper {player}"
```

규칙:

- `chance`가 없으면 `100`으로 처리한다.
- `chance`는 절대 퍼센트가 아니라 가중치이다.
- 합계가 100일 필요는 없다.
- 합계가 100보다 작아도 자동 정규화한다.
- 합계가 100보다 커도 자동 정규화한다.
- `chance <= 0`인 Reward는 선택 후보에서 제외한다.
- 선택 후보가 1개면 해당 Reward가 실행된다.
- 선택 가능한 Reward가 없으면 Reward를 실행하지 않고 콘솔에 경고를 남긴다.

정규화 예:

```text
70 + 30 = 100
=> 70%, 30%

7 + 3 = 10
=> 70%, 30%

200 + 100 = 300
=> 66.666%, 33.333%

50 + 50 + 50 = 150
=> 33.333%, 33.333%, 33.333%
```

아무것도 실행되지 않는 확률을 만들고 싶다면 명시적으로 비어 있는 Reward를 둘 수 있다.

예:

```yml
- id: "nothing"
  amount: "1000"
  chance: 50
  actions: []
```

---

## Random System

파일:
random.yml

예:

```yml
monster_random:
  - "zombie"
  - "skeleton"
  - "creeper"
```

확률 가중치 예:

```yml
monster_random:
  - value: "zombie"
    chance: 50
  - value: "skeleton"
    chance: 30
  - value: "creeper"
    chance: 20
```

---

## Random Placeholder

### Event Scope

{random.monster_random}

후원 이벤트 1회 동안 동일 값 유지.

### Random Once

{random_once.monster_random}

사용 시마다 새 랜덤.

---

## 기본 Random

- monster_random
- animal_random
- buff_random
- debuff_random
- item_random
- food_random
- ore_random

---

## OAuth 흐름

Pairing Code는 비밀번호가 아니라 연결 요청 접수번호이다.

단일 사용자만 처리하는 프로그램이라면 Pairing Code 없이도 OAuth callback 결과를 바로 저장할 수 있다.
하지만 McStreamApi는 여러 Minecraft 플레이어가 동시에 `/mca connect`를 실행할 수 있다.
Chzzk/SOOP OAuth callback에는 Minecraft 플레이어 정보가 기본으로 들어있지 않기 때문에,
AuthServer는 callback으로 받은 토큰이 어느 플레이어의 연결 요청인지 구분해야 한다.

예:

```text
Meeor -> /mca connect soop -> pairingCode A7K29Q
Steve -> /mca connect soop -> pairingCode M8P4XZ

OAuth callback -> state 검증 -> pairingCode A7K29Q 확인
AuthServer: 이 토큰은 Meeor 연결 요청의 결과
Plugin: A7K29Q polling 후 Meeor 토큰으로 저장
```

역할 정리:

```text
pairingCode   = 여러 연결 요청 중 어느 요청인지 구분하는 접수번호
state         = OAuth callback 위조 방지용 임시 검증값
sharedSecret  = Plugin과 AuthServer 사이의 서버 간 비밀번호
```

플러그인
→ Pairing Code 생성

플레이어
→ 승인 URL 접속

OAuth 승인

Auth Server
→ Token 발급

플러그인
→ Token 수령

Token 저장

Auth Server
→ 임시 데이터 삭제

---

## Nginx

Reverse Proxy 전용.

예:

location /mca/ {
    proxy_pass http://127.0.0.1:18080/;
}

예상 Redirect URI:

https://auth.example.com/mca/chzzk/callback
https://auth.example.com/mca/soop/callback

---

## 추가 개발 지침

- Kotlin
- Java 21
- Spigot 기반
- Paper 호환
- Token 자동 Refresh 필수
- 비동기 이벤트 처리
- Config Reload 지원
- 상세 오류 로그 제공
- 자체 Placeholder 시스템 구현
- Provider 구조 설계

### 공식 API 조사 의무

Chzzk 및 SOOP API 세션, OAuth, 후원 이벤트 처리 코드를 작성하기 전에는 반드시 인터넷 검색으로 공식 API 문서를 확인한다.

규칙:

- 공식 문서 또는 공식 개발자 센터를 우선한다.
- OAuth authorize endpoint, token endpoint, refresh endpoint를 문서 기준으로 확인한다.
- 후원 이벤트 API가 WebSocket, polling, webhook 중 어떤 방식인지 공식 문서 기준으로 확인한다.
- 필요한 scope, rate limit, 에러 응답 형식을 확인한다.
- 확인한 endpoint/scope/제약은 구현 전 문서나 코드 주석에 출처와 함께 남긴다.
- 비공식 블로그/예제 코드는 공식 문서와 교차 검증하기 전까지 구현 기준으로 사용하지 않는다.

공식 API 확인 없이 임의 endpoint를 추측해서 Provider 코드를 작성하지 않는다.

---

## 목표

공식 OAuth 기반의 안전한 후원 연동 플랫폼 제공.
Config와 Random 시스템만으로 다양한 이벤트 서버 구축 가능하도록 설계.

---

## 제품 범위 정의

McStreamApi의 1차 목표는 "스트리머 계정과 Minecraft 플레이어를 안전하게 연결하고, 공식 후원 이벤트를 서버 보상으로 변환하는 것"이다.

1차 MVP에 포함:

- SOOP OAuth 연결
- SOOP 후원 이벤트 수신
- Player 단위 토큰 암호화 저장
- Config 기반 Reward 매칭
- Action 실행 파이프라인
- `/mca connect soop`
- `/mca reload`
- `/mca apply <player> <amount>`
- Random placeholder
- Token refresh
- Chzzk OAuth/후원 이벤트 연동은 SOOP MVP 이후 구현

1차 MVP에서 제외:

- 웹 대시보드
- 인게임 토큰 삭제 명령
- 여러 Minecraft 서버 간 토큰 공유
- 토큰 클라우드 저장
- Reward GUI 편집기

2차 확장:

- Provider별 후원 이벤트 Adapter
- 이후 Minecraft 버전 호환 Adapter
- 더 많은 Action 타입
- Redis 등 외부 상태 저장 연동

---

## 핵심 책임 분리

### Plugin 책임

- Pairing Code 생성
- AuthServer에 Pairing 등록
- Player에게 승인 URL 안내
- AuthServer polling
- Token 암호화 저장
- Token refresh
- 플랫폼 후원 이벤트 구독/조회
- Reward 매칭
- Action 실행
- Config reload
- Placeholder 치환
- Minecraft 버전별 API 차이 흡수

### AuthServer 책임

- OAuth Redirect 수신
- Authorization Code를 Token으로 교환
- Pairing Code와 Token 매칭
- Token 1회 전달
- 임시 Token 즉시 삭제

### AuthServer가 하지 않는 일

- Minecraft 명령 실행
- 후원 Reward 계산
- Token 영구 저장
- Config 관리
- Random 처리
- 인게임 Player 권한 판단

---

## 권장 패키지 구조

```text
kr.meeor.mcstreamapi
  McStreamApiPlugin

  command
    McaCommand
    McaTabCompleter

  config
    PluginConfig
    ConfigManager
    RewardConfig
    RandomConfig

  auth
    AuthClient
    PairingCodeGenerator
    PairingPoller
    TokenStore
    TokenCrypto
    SharedSecretValidator

  provider
    Platform
    DonationProvider
    DonationEvent
    ChzzkDonationProvider
    SoopDonationProvider

  reward
    Reward
    RewardMatcher
    RewardExecutor
    AmountRule

  action
    Action
    ActionType
    CommandAction
    GiveAction
    ChatAction
    BroadcastAction
    TitleAction

  placeholder
    PlaceholderContext
    PlaceholderResolver
    RandomResolver

  scheduler
    AsyncExecutor
    MainThreadDispatcher

  version
    MinecraftAdapter
    Spigot_1_20_1_Adapter
```

---

## Config 예시

McStreamApi는 설정을 역할별 YAML 파일로 나눈다.

```text
plugins/McStreamApi/config.yml
plugins/McStreamApi/Api.yml
plugins/McStreamApi/random.yml
plugins/McStreamApi/custom-item.yml
```

공개 Git 저장소에는 실제 운영 파일 대신 아래 샘플만 포함한다.

```text
config.example.yml
Api.example.yml
random.example.yml
```

실제 서버 운영자는 샘플 파일을 복사해 직접 값을 채운다.

```text
config.example.yml -> plugins/McStreamApi/config.yml
Api.example.yml    -> plugins/McStreamApi/Api.yml
random.example.yml -> plugins/McStreamApi/random.yml
custom-item.example.yml -> plugins/McStreamApi/custom-item.yml
```

### 최초 실행 설정 생성 정책

플러그인은 최초 1회 시작 시 `plugins/McStreamApi/` 폴더와 기본 설정 파일을 생성한다.

생성 대상:

```text
plugins/McStreamApi/config.yml
plugins/McStreamApi/Api.yml
plugins/McStreamApi/random.yml
plugins/McStreamApi/custom-item.yml
plugins/McStreamApi/secret.key
plugins/McStreamApi/tokens/
```

최초 생성 후 동작:

1. 기본 설정 파일을 생성한다.
2. 콘솔에 설정 필요 메시지를 출력한다.
3. 서버 운영자에게 `config.yml`, `Api.yml`, `random.yml`, `custom-item.yml` 수정이 필요하다고 안내한다.
4. 플러그인을 활성 상태로 유지하지 않고 비활성화한다.

콘솔 메시지 예:

```text
[McStreamApi] config.yml, Api.yml, random.yml, custom-item.yml 파일이 없어 기본값으로 새로 만들었습니다.
[McStreamApi] OAuth clientId/clientSecret, sharedSecret, AuthServer 주소를 설정한 뒤 서버를 다시 시작해주세요.
[McStreamApi] 설정이 완료되지 않아 플러그인을 비활성화합니다.
```

Player에게는 최초 설정 오류를 노출하지 않는다.
서버 콘솔에서만 안내한다.

### 플랫폼 설정 검증 정책

`config.yml`에서 특정 플랫폼이 `enabled: true`여도 필수 OAuth 설정이 없으면 해당 플랫폼은 강제로 비활성화한다.

필수 값:

```text
platforms.<platform>.clientId
platforms.<platform>.clientSecret
auth.serverBaseUrl
auth.sharedSecret
```

처리:

- `clientId` 또는 `clientSecret`이 비어 있거나 example placeholder이면 해당 플랫폼을 비활성화한다.
- `auth.serverBaseUrl` 또는 `auth.sharedSecret`이 비어 있거나 example placeholder이면 OAuth 연결 기능 전체를 비활성화한다.
- 설정 파일을 자동으로 덮어써서 `enabled: false`로 저장하지는 않는다.
- 런타임 내부 상태에서만 비활성화하고 콘솔에 안내한다.

콘솔 메시지 예:

```text
[McStreamApi] Chzzk API 설정이 완료되지 않아 chzzk API가 비활성화되었습니다.
[McStreamApi] SOOP API 설정이 완료되지 않아 soop API가 비활성화되었습니다.
[McStreamApi] auth.sharedSecret이 기본값입니다. python generate_shared_secret.py 로 값을 생성해 설정해주세요.
```

Player 메시지 예:

```text
[McStreamApi] 현재 Chzzk 연결 기능을 사용할 수 없습니다. 관리자에게 문의해주세요.
[McStreamApi] 현재 SOOP 연결 기능을 사용할 수 없습니다. 관리자에게 문의해주세요.
```

### config.yml

역할:

- AuthServer 주소
- 플랫폼 OAuth clientId/clientSecret
- polling interval
- token refresh 정책
- 로그 설정
- 민감 정보 또는 서버별 값

이 파일은 Git에 커밋하지 않는다.

예시:

```yml
auth:
  serverBaseUrl: "https://auth.example.com/mca"
  sharedSecret: "CHANGE_ME_RANDOM_LONG_SECRET"
  pollingIntervalSeconds: 3
  pairingTimeoutSeconds: 600

platforms:
  chzzk:
    enabled: true
    clientId: "CHZZK_CLIENT_ID"
    clientSecret: "CHZZK_CLIENT_SECRET"
    eventPollingIntervalSeconds: 5
    tokenRefreshBeforeSeconds: 300

  soop:
    enabled: true
    clientId: "SOOP_CLIENT_ID"
    clientSecret: "SOOP_CLIENT_SECRET"
    eventPollingIntervalSeconds: 5
    tokenRefreshBeforeSeconds: 300

logging:
  debug: false
  maskSensitiveValues: true
```

### Api.yml

역할:

- 금액 조건별 Reward 정의
- Reward 내부 Action 목록 정의
- Placeholder 사용 위치 정의
- Reward 선택 가중치 정의

이 파일은 서버 운영자가 수정하는 공개 가능한 설정일 수 있으나, 서버별 운영 전략이 들어갈 수 있으므로 기본 `.gitignore`에는 포함한다.

Reward 선택 확률:

- Reward에는 선택 가중치 `chance`를 둘 수 있다.
- `chance`가 없으면 `100`으로 처리한다.
- 같은 amount 우선순위에서 여러 Reward가 매칭되면 `chance` 가중치로 1개를 선택한다.
- `chance` 합계가 100이 아니어도 자동 정규화한다.

Action별 필수 필드는 반드시 검증한다.
필수 필드가 빠진 Action은 reload 시 비활성화하고 콘솔에 오류를 출력한다.

필수 필드:

```text
cmd:
  type
  command

give:
  type
  target
  material
  amount

chat:
  type
  target
  message

broadcast:
  type
  message

title:
  type
  target
  title
```

선택 필드:

```text
give:
  name
  lore
  customModelData
  unbreakable
  enchantments
  glow
  itemFlags
  persistentData
  pdc
  itemTag
  playerHead
  customPotionEffects
  book
  attributes

title:
  subtitle
  fadeInTicks
  stayTicks
  fadeOutTicks
```

`@s`는 연결된 온라인 플레이어로 치환한다.
대상 플레이어가 오프라인이면 Action은 실행하지 않고 실패 로그만 남긴다.

예시:

```yml
rewards:
  chzzk:
    - id: "chzzk_1000"
      amount: "1000"
      chance: 100
      actions:
        - type: "broadcast"
          message: "&a{donator}님이 {streamer}에게 {amount}원을 후원했습니다."
        - type: "give"
          target: "@s"
          material: "DIAMOND"
          amount: 1
          name: "&b후원 다이아몬드"
          lore:
            - "&7후원자: {donator}"
            - "&7금액: {amount}"

    - id: "chzzk_monster"
      amount: "5000+"
      chance: 70
      actions:
        - type: "cmd"
          command: "summon {random.monster_random} {player}"

    - id: "chzzk_rare_bonus"
      amount: "5000+"
      chance: 30
      actions:
        - type: "give"
          target: "@s"
          material: "EMERALD"
          amount: 3

  soop:
    - id: "soop_1000"
      amount: "1000"
      chance: 100
      actions:
        - type: "broadcast"
          message: "&b{donator}님이 SOOP에서 {amount}원을 후원했습니다."
```

### random.yml

역할:

- `{random.key}`와 `{random_once.key}`에서 사용할 랜덤 후보 목록 정의
- 랜덤 후보별 선택 가중치 정의

### custom-item.yml

역할:

- `custom-give`에서 참조할 복잡한 아이템 정의
- PDC, enchantment, glow, item flags, player head, potion, book, attribute 같은 ItemMeta 정의

예시:

```yml
monster_random:
  - value: "zombie"
    chance: 50
  - value: "skeleton"
    chance: 30
  - value: "creeper"
    chance: 20

animal_random:
  - "cow"
  - "pig"
  - "chicken"

buff_random:
  - "speed"
  - "haste"
  - "strength"
```

---

## 파일 구조 상세

```text
plugins/McStreamApi/
  config.yml
  Api.yml
  random.yml
  secret.key
  tokens/
    chzzk_Meeor.json.enc
```

### config.yml

- 서버 운영자가 직접 작성하는 실제 설정 파일.
- OAuth clientId/clientSecret, AuthServer 주소, sharedSecret 등을 포함한다.
- 공개 Git 저장소에는 `config.example.yml`만 포함하고 실제 `config.yml`은 제외한다.

### sharedSecret 생성

sharedSecret은 사람이 직접 생각해서 만드는 값이 아니라, 저장소의 생성 스크립트로 만든 랜덤 문자열을 사용한다.

```text
python generate_shared_secret.py
```

출력 예:

```text
mca_GENERATED_RANDOM_SECRET_HERE
```

생성한 값을 Plugin과 AuthServer 양쪽 설정에 동일하게 넣는다.

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

금지:

- `CHANGE_ME_RANDOM_LONG_SECRET` 기본값 그대로 사용
- 닉네임, 서버 이름, 도메인 사용
- 짧은 단어 사용
- 방송 화면이나 로그에 노출

### Api.yml

- 금액별 Reward와 Action 설정 파일.
- `/mca reload` 시 다시 읽는다.
- YAML 문법 오류 또는 잘못된 Reward는 reload 시 명확히 보고한다.

### random.yml

- Random placeholder 후보 목록.
- `/mca reload` 시 다시 읽는다.

### secret.key

- Token 파일 암호화에 사용.
- 최초 실행 시 자동 생성.
- 분실 시 기존 Token 복호화 불가.
- 재생성 시 기존 `tokens/*.enc`는 사용할 수 없다.

### tokens

Token 저장 원문 구조:

```json
{
  "platform": "chzzk",
  "minecraftPlayerName": "Meeor",
  "minecraftUuid": "uuid-string",
  "streamerId": "channel-id",
  "streamerName": "channel-name",
  "accessToken": "access-token",
  "refreshToken": "refresh-token",
  "expiresAt": 1780000000000,
  "scope": "donation.read"
}
```

저장 시 위 JSON 전체를 암호화한다.

---

## Token 암호화 요구사항

권장:

- AES-GCM
- 256-bit key
- 파일별 random nonce
- 인증 태그 검증 실패 시 Token 파일 사용 금지

저장 포맷 예:

```json
{
  "version": 1,
  "algorithm": "AES/GCM/NoPadding",
  "nonce": "base64",
  "cipherText": "base64"
}
```

로그 금지:

- accessToken
- refreshToken
- secret.key
- config.yml의 clientSecret/sharedSecret
- 복호화된 Token JSON

---

## 연결 흐름 상세

```text
Player: /mca connect soop
  ↓
Plugin: Pairing Code 생성
  ↓
Plugin: POST /api/pairing
  ↓
AuthServer: authorizeUrl 반환
  ↓
Plugin: Player에게 URL 출력
  ↓
Player: 브라우저에서 OAuth 승인
  ↓
AuthServer: Token 임시 보관
  ↓
Plugin: GET /api/pairing/{code} polling
  ↓
Plugin: Token 수신
  ↓
Plugin: minecraftUuid 검증
  ↓
Plugin: Token 암호화 저장
  ↓
Plugin: Provider 구독 시작
```

실패 처리:

- Pairing 만료: Player에게 재시도 안내.
- AuthServer 오류: 콘솔에 오류 코드 출력, Token 값 출력 금지.
- 플랫폼 불일치: 연결 실패 처리.
- UUID 불일치: 연결 실패 처리.
- Token 저장 실패: Token 폐기 후 재시도 안내.

---

## 후원 이벤트 모델

Plugin 내부 공통 이벤트 모델:

```kotlin
data class DonationEvent(
    val platform: Platform,
    val streamerId: String,
    val streamerName: String,
    val minecraftPlayerName: String,
    val minecraftUuid: UUID,
    val donatorId: String?,
    val donatorName: String,
    val amount: Long,
    val message: String?,
    val eventId: String,
    val receivedAtMillis: Long
)
```

Provider는 플랫폼 원본 이벤트를 위 모델로 변환한다.

중복 처리:

- `eventId` 기준으로 최근 처리 이벤트를 일정 시간 캐싱한다.
- 같은 후원 이벤트는 Reward를 1회만 실행한다.
- 서버 재시작 후 중복 가능성이 있는 플랫폼은 Provider에서 가능한 범위로 마지막 커서 또는 timestamp를 관리한다.

---

## API 세션 정책

Chzzk/SOOP 후원 이벤트 처리는 가능한 경우 플랫폼 공식 WebSocket 세션을 사용한다.

목표:

- 후원 이벤트를 실시간 처리한다.
- polling보다 중복 가능성을 줄인다.
- 플랫폼에서 제공하는 event id, cursor, sequence, timestamp를 최대한 활용한다.

세션 유지 조건:

- Minecraft 플레이어가 온라인 상태일 때만 Provider 세션을 유지한다.
- `/mca connect`로 연결된 플레이어가 서버에서 나가면 해당 플레이어의 API 세션을 즉시 종료한다.
- 오프라인 플레이어에게는 후원 이벤트 Reward를 실행하지 않는다.
- 오프라인 상태에서 발생한 이벤트를 나중에 보상으로 적립하지 않는다.

재접속 처리:

- 플레이어가 다시 서버에 접속하면 저장된 Token을 복호화하고 Provider 세션을 다시 시작한다.
- Token refresh가 필요하면 세션 시작 전에 refresh를 시도한다.
- refresh 실패 정책은 Token 실패 정책을 따른다.

중복 방지:

- WebSocket 이벤트라도 `eventId` 또는 플랫폼 equivalent id를 기준으로 중복 처리 캐시를 유지한다.
- event id가 없는 플랫폼은 timestamp + donatorId + amount + message hash 조합으로 best-effort 중복 방지를 수행한다.
- 중복 방지는 완전 보장보다 "동일 이벤트 중복 보상 지급 최소화"를 목표로 한다.

---

## Reward 매칭 세부 규칙

후원 1회당 Reward는 1개만 실행한다.

매칭 우선순위:

1. Exact Match
2. Range Match
3. Plus Match

동일 우선순위에서 여러 개가 매칭되면 `chance` 가중치로 Reward 1개를 선택한다.

잘못된 amount 문법:

- Plugin enable 또는 reload 시 오류로 보고한다.
- 해당 Reward는 비활성화한다.
- 나머지 정상 Reward는 계속 사용한다.

권장 amount parser:

```text
1000      exact
1000-5000 range inclusive
1000+     lower-bound inclusive
```

금액은 정수만 허용한다.

Reward 선택:

- amount 우선순위로 후보 Reward를 먼저 고른다.
- 같은 우선순위 후보 안에서 `chance` 가중치로 1개를 선택한다.
- `chance`가 없는 Reward는 `chance: 100`으로 본다.
- `chance <= 0`인 Reward는 선택 후보에서 제외한다.
- 후보들의 chance 총합이 100일 필요는 없다.
- 내부적으로 `chance / 총합`으로 정규화한다.
- 선택된 Reward의 Action 목록만 실행한다.

---

## Action 실행 규칙

모든 Action은 선언 순서대로 실행한다.

실패 처리:

- 한 Action이 실패해도 다음 Action은 계속 실행한다.
- Reward 단위로 실패 로그를 남긴다.
- 치명적인 config 오류는 reload 시점에 최대한 사전 검출한다.
- 실패한 Action은 Player에게 직접 노출하지 않고 콘솔에 기록한다.
- 온라인 플레이어가 필요한 Action에서 대상이 오프라인이면 해당 Action만 실패 처리한다.

Thread 규칙:

- 플랫폼 API 통신, Token refresh, AuthServer polling은 비동기 실행.
- Bukkit API 접근, 명령 실행, 아이템 지급, 채팅/타이틀 출력은 메인 스레드에서 실행.

권한:

- `/mca connect`: 모든 플레이어 사용 가능
- `/mca reload`: 기본 사용 금지, `mcstreamapi.reload` 권한 필요
- `/mca apply`: 기본 사용 금지, `mcstreamapi.apply` 권한 필요
- `/mca status`: 기본 사용 금지, `mcstreamapi.status` 권한 필요

관리 명령은 op 여부만으로 허용하지 않고 permission node로만 허용한다.

---

## Token 실패 정책

Token refresh, Provider session start, WebSocket reconnect 중 토큰 관련 실패가 발생하면 최대 3회 재시도한다.

재시도 규칙:

```text
maxRetries: 3
retryDelaySeconds: 3
```

처리:

1. 실패 발생 시 Console에만 알림.
2. 3초 대기 후 재시도.
3. 최대 3회까지 반복.
4. 3회 실패 후 해당 Provider 세션 종료.
5. 최종 실패 시에만 Player에게 재연결을 요청.

Player 메시지:

```text
[McStreamApi] API 연결에 실패하였습니다. 다시 한번 /mca connect <platform> 명령어를 통해 연결해주시기 바랍니다.
```

Console 로그:

```text
Token refresh failed. platform=<platform> player=<player> retry=1/3 error=<errorCode>
Token refresh permanently failed. platform=<platform> player=<player>
```

로그 금지:

- accessToken
- refreshToken
- authorization code
- clientSecret
- sharedSecret

---

## Placeholder 처리 규칙

치환 순서:

1. Event 기본 값
2. Player/Streamer 값
3. Random event-scope 값
4. Random once 값
5. 미해결 placeholder 유지 또는 빈 값 처리

권장 기본값:

- `message`가 없으면 빈 문자열
- `donatorId`가 없으면 빈 문자열
- 알 수 없는 placeholder는 원문 유지

Random event-scope:

- 동일 DonationEvent 처리 중 `{random.key}`는 항상 같은 값.
- Reward 안의 모든 Action에서 공유.
- random 후보가 가중치를 가지면 정규화된 가중치로 선택한다.

Random once:

- `{random_once.key}`는 호출 시마다 새 값.
- random 후보가 가중치를 가지면 호출할 때마다 정규화된 가중치로 다시 선택한다.

Random 가중치 규칙:

- 단순 문자열 항목은 `chance: 100`으로 처리한다.
- `{ value: "...", chance: n }` 형태를 지원한다.
- chance 합계가 100일 필요는 없다.
- `chance <= 0`인 항목은 선택 후보에서 제외한다.
- 선택 가능한 항목이 없으면 placeholder 원문을 유지하고 콘솔에 경고를 남긴다.

---

## Reload 정책

`/mca reload` 수행 항목:

- config.yml 재로드
- Api.yml 재로드
- random.yml 재로드
- custom-item.yml 재로드
- Reward 파싱/검증
- Provider 활성화 설정 반영
- 새 설정으로 polling interval 반영

`/mca reload`에서 하지 않는 항목:

- secret.key 재생성
- 기존 Token 삭제
- 진행 중인 OAuth Pairing 강제 성공 처리

---

## 오류와 로그 정책

콘솔 로그에는 다음 정보를 남긴다.

출력 가능:

- 플랫폼 이름
- Pairing Code
- Player 이름
- Reward id
- Action type
- 오류 코드
- 마스킹된 streamerId

출력 금지:

- accessToken
- refreshToken
- authorization code
- secret.key
- clientSecret
- sharedSecret

Player 메시지는 짧고 명확하게 유지한다.

예:

```text
[McStreamApi] Chzzk 연결을 시작했습니다. 아래 URL에서 인증하세요.
[McStreamApi] 인증 시간이 만료되었습니다. 다시 시도하세요.
[McStreamApi] 연결이 완료되었습니다.
```

### 사용자 표시 오류 메시지

Plugin은 내부 예외를 그대로 Player에게 보여주지 않는다.
Player에게는 짧은 한국어 메시지를 보여주고, 콘솔에는 오류 코드와 원인을 남긴다.

권장 메시지:

```text
AUTH_SERVER_UNREACHABLE
Player: 인증서버가 응답하지 않습니다. 관리자에게 문의해주세요.
Console: AuthServer unreachable. url=<serverBaseUrl> cause=<exception>

AUTH_SERVER_TIMEOUT
Player: 인증서버 응답 시간이 초과되었습니다. 잠시 후 다시 시도해주세요.
Console: AuthServer request timed out. endpoint=<endpoint>

AUTH_SHARED_SECRET_INVALID
Player: 인증서버와의 통신 중 에러가 발생했습니다. 관리자에게 문의해주세요. (시크릿 코드 오류)
Console: AuthServer rejected sharedSecret. Check Plugin config.yml auth.sharedSecret and AuthServer config.yml security.sharedSecret.

AUTH_SERVER_BAD_RESPONSE
Player: 인증서버 응답을 처리할 수 없습니다. 관리자에게 문의해주세요.
Console: Invalid AuthServer response. status=<httpStatus> body=<maskedBody>

PAIRING_EXPIRED
Player: 인증 시간이 만료되었습니다. 다시 연결을 시도해주세요.
Console: Pairing expired. code=<pairingCode> player=<player>

PAIRING_FAILED
Player: 인증 처리 중 오류가 발생했습니다. 다시 시도해주세요.
Console: Pairing failed. code=<pairingCode> error=<errorCode>

TOKEN_SAVE_FAILED
Player: 인증 정보 저장에 실패했습니다. 관리자에게 문의해주세요.
Console: Failed to save encrypted token. player=<player> platform=<platform> cause=<exception>

CONFIG_SECRET_DEFAULT
Player: McStreamApi 설정이 완료되지 않았습니다. 관리자에게 문의해주세요.
Console: sharedSecret still uses default placeholder. Generate one with python generate_shared_secret.py.

CONFIG_CREATED_AND_DISABLED
Player: 표시하지 않음.
Console: config.yml, Api.yml, random.yml, custom-item.yml were created with default values. Configure them and restart the server.

PLATFORM_CONFIG_MISSING
Player: 현재 <platform> 연결 기능을 사용할 수 없습니다. 관리자에게 문의해주세요.
Console: <platform> API 설정이 완료되지 않아 <platform> API가 비활성화되었습니다.
```

민감정보 마스킹:

- `<maskedBody>`에는 accessToken, refreshToken, clientSecret, sharedSecret 값을 포함하지 않는다.
- sharedSecret 불일치 시 실제 입력값을 로그에 출력하지 않는다.
- HTTP 오류 body에 토큰이 포함될 가능성이 있으면 body 전체를 생략한다.

---

## 테스트 기준

단위 테스트:

- AmountRule parser
- RewardMatcher
- PlaceholderResolver
- RandomResolver
- TokenCrypto encrypt/decrypt
- PairingCodeGenerator

통합 테스트:

- AuthServer mock으로 Pairing 등록/조회
- AUTHORIZED 응답 수신 후 Token 저장
- Token 1회 수신 계약 검증
- `/mca apply`가 실제 Reward pipeline을 사용하는지 검증

수동 QA:

- Paper/Spigot 1.21.11 서버 기동
- `/mca connect soop`
- Pairing timeout
- 잘못된 AuthServer URL
- Token refresh 실패
- Config reload 후 Reward 변경 반영
- Paper 서버에서 호환성 확인

---

## 구현 우선순위

Phase 1: 프로젝트 기반

- Gradle Kotlin 프로젝트 생성
- Spigot/Paper API 설정
- 기본 plugin.yml
- `/mca` 명령 뼈대
- ConfigManager

Phase 2: Reward 엔진

- AmountRule
- RewardMatcher
- PlaceholderResolver
- Action executor
- `/mca apply`

Phase 3: Auth 연동

- secret.key 생성
- AuthClient
- Pairing 등록/polling
- TokenCrypto
- TokenStore

Phase 4: Chzzk Provider

- Token refresh
- 채널 정보 관리
- 후원 이벤트 수신
- DonationEvent 변환

Phase 5: 안정화

- reload 안정화
- 로그 마스킹
- 중복 이벤트 방지
- Paper 호환성 확인
- 배포 문서 작성
