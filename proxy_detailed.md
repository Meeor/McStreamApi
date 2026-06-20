# McStreamApi Proxy Bridge System (Future Feature)

> 본 문서는 McStreamApi의 향후 Proxy Bridge 지원 설계 문서이다.
> 현재 버전에서는 구현하지 않으며, SOOP/Chzzk 연동 안정화 이후 개발한다.

# 목표

McStreamApi는 앞으로도 단일 서버 플러그인을 기본으로 유지한다.

```yaml
proxy:
  enabled: false
```

일 경우 현재와 동일하게 동작해야 한다.

```text
SOOP / Chzzk
      ↓
 McStreamApi
      ↓
   보상 실행
```

기존 사용자는 업데이트 후에도 설정 변경 없이 그대로 사용 가능해야 한다.

---

# Proxy 모드

```yaml
proxy:
  enabled: true
```

활성화 시 Proxy Bridge 기능 사용.

```text
SOOP / Chzzk
        ↓
 McStreamApi MASTER
        ↓
 McStreamApi-client.jar
        ↓
 Lobby / Survival / Dungeon
```

---

# MASTER 역할

MASTER만 수행

- OAuth 인증
- 토큰 저장
- 토큰 갱신
- API 연결
- WebSocket 연결
- 후원 이벤트 수신
- Reward 선택
- Amount 판정
- Chance 계산
- Weight 계산
- Random 계산
- Action Packet 생성

---

# ClientBridge 자동 생성

Proxy 활성화 후 플러그인 시작 시

```text
plugins/
└─ McStreamApi/
   ├─ config.yml
   ├─ tokens/
   ├─ secret.key
   └─ gen/
      ├─ McStreamApi-client.jar
      ├─ bridge.key
      └─ ReadMe.txt
```

자동 생성.

---

# ClientBridge 특징

별도 설정파일 없음.

운영자는

- 주소 입력
- 포트 입력
- Secret 입력

등을 하지 않는다.

```text
plugins/
 ├─ McStreamApi-client.jar
 └─ bridge.key
```

두 파일만 함께 설치하면 된다.

---

# 내부 세션 구조

ClientBridge 생성 시 MASTER 연결 정보가 포함된 전용 `bridge.key` 생성.

운영자는 생성된 `McStreamApi-client.jar`와 `bridge.key`를 함께 CLIENT 서버에 설치한다.

예시

- Bridge UUID
- Master UUID
- Session Token
- Protocol Version
- MASTER 주소
- MASTER 포트
- Master Public Key

MASTER가 생성한 Bridge만 접속 가능.

JAR 내부에 인증정보를 직접 넣는 방식보다 별도 `bridge.key`를 우선 검토한다.
키가 유출되거나 복제된 경우 MASTER에서 Bridge를 재생성하여 기존 키를 무효화할 수 있다.

---

# Bridge 네트워크

CLIENT가 MASTER에 WebSocket으로 연결한다.

MASTER의 기본 Bridge 포트는 `26656`으로 한다.

예시

```yaml
proxy:
  enabled: true
  bridge:
    public-host: "mc.example.com"
    port: 26656
```

`public-host`와 `port`는 MASTER 설정에서 관리하며 생성되는 `bridge.key`에 반영한다.
CLIENT 운영자는 주소와 포트를 별도로 입력하지 않는다.

다른 컴퓨터 또는 외부 네트워크의 CLIENT가 접속하려면 MASTER 측 방화벽 허용과 NAT 포트포워딩이 필요할 수 있다.

---

# 다른 서버 / 다른 컴퓨터 지원

```text
Machine A
 └ MASTER

Machine B
 └ Survival

Machine C
 └ Dungeon
```

동일 컴퓨터가 아니어도 사용 가능.

---

# 플레이어 인증

중요:

CLIENT 서버에서도 인증 가능.

예시

```text
Lobby 서버 접속
↓
/mca connect soop
↓
ClientBridge
↓
MASTER
↓
OAuth URL 생성
↓
플레이어에게 전달
```

또는

```text
Dungeon 서버 접속
↓
/mca connect chzzk
↓
MASTER 요청
↓
OAuth URL 생성
```

플레이어는 어느 서버에서든 인증 가능.

---

# 플레이어 서버 추적

플레이어 식별은 닉네임이 아닌 UUID를 기준으로 한다.

MASTER는 각 CLIENT가 보고한 접속 상태를 바탕으로 플레이어의 현재 접속 서버를 관리한다.
후원 Action은 플레이어가 현재 접속한 서버의 ClientBridge로 전송한다.

서버 이동 중에는 즉시 오프라인으로 확정하지 않고 짧은 지연 시간을 둔다.
이동이 완료되어 새 서버 접속이 확인되면 이동된 서버에서 Action을 처리한다.

접속 상태 갱신 순서가 뒤바뀌는 문제를 막기 위해 접속 상태에 세대 번호 또는 동일한 역할의 순서 값을 사용한다.
이전 서버에서 늦게 도착한 퇴장 정보가 새 서버의 접속 정보를 덮어쓰면 안 된다.

지연 시간이 지난 뒤에도 어느 서버에도 접속하지 않은 경우 오프라인으로 처리한다.
오프라인 플레이어의 후원 이벤트 세션은 연결을 종료하고 해당 후원 이벤트는 폐기한다.

---

# 토큰 저장

항상 MASTER만 수행.

```text
tokens/
 ├─ soop_ras.dat
 ├─ soop_player.dat
 └─ chzzk_player.dat
```

CLIENT는 토큰을 저장하지 않는다.

---

# 후원 이벤트 처리

후원 발생

```text
SOOP
↓
MASTER
↓
Reward 선택
↓
Chance 계산
↓
Weight 계산
↓
Random 계산
↓
Action 생성
↓
ClientBridge 전송
↓
실행
```

Action 일부만 성공한 경우 성공한 Action을 되돌리지 않는다.
CLIENT는 Action별 실행 결과와 실패 원인을 MASTER에 전송한다.
MASTER는 자신이 실행 중인 서버의 콘솔 로그에 실패 내용을 기록하고 해당 이벤트 처리를 종료한다.

---

# 기존 Reward 엔진 재사용

Proxy 모드에서도 기존 로직을 그대로 사용.

현재

```text
후원
↓
Reward 선택
↓
Chance 계산
↓
Random 계산
↓
Action 생성
↓
실행
```

Proxy

```text
후원
↓
Reward 선택
↓
Chance 계산
↓
Random 계산
↓
Action 생성
↓
전송
↓
실행
```

즉

Reward Engine
Random Engine
Chance Engine
Weight Engine

전부 MASTER에서 그대로 사용.

---

# CLIENT 금지 사항

CLIENT는 절대 수행하지 않음.

- Reward 선택
- Amount 판정
- Chance 계산
- Weight 계산
- Random 계산

CLIENT는 Action 실행만 담당.

---

# Action Packet 예시

```json
{
  "eventId":"abc123",
  "playerUuid":"00000000-0000-0000-0000-000000000000",
  "playerName":"Ras",
  "targetServer":"survival",
  "rewardId":"soop_1000",
  "actions":[
    {
      "type":"cmd",
      "command":"코인 적립 Ras 1"
    }
  ]
}
```

---

# 중복 지급 방지

모든 이벤트는 eventId 보유.

CLIENT는 최근 처리한 eventId 캐시 유지.

동일 eventId 재수신 시 무시.

CLIENT는 실행 완료 후 Action별 성공 여부와 실패 원인을 포함한 결과 패킷을 MASTER에 반환한다.

---

# ReadMe.txt

```text
McStreamApi ClientBridge

1. 해당 파일은 복사하여 사용할 것.
2. bridge.key 파일을 ClientBridge.jar와 함께 설치할 것.
3. 이동 또는 잘라내기 하지 말 것.
4. 여러 서버에서 동시에 사용 가능.
5. ClientBridge.jar와 bridge.key는 이 MASTER 서버와만 연결됩니다.
6. 재생성(generate) 시 기존 Bridge는 무효화될 수 있습니다.
```

---

# Proxy 명령어

Proxy 모드 활성 시 추가.

/mca proxy generate

기능

- Session 재생성
- McStreamApi-client.jar 재생성
- bridge.key 재생성
- ReadMe.txt 재생성

/mca proxy revoke

기능

- 현재 Session 폐기
- 기존 ClientBridge 전부 무효화
- 재접속 차단

---

# 장점

- 토큰 중앙 관리
- 재인증 불필요
- 서버 이동 대응
- 중복 지급 방지
- 설정 일원화
- 운영 편의성 향상
- 다중 서버 대응
- 대규모 콘텐츠 대응

---

# 개발 우선순위

현재

1. SOOP 실연결 검증
2. Chzzk 안정화
3. 보상 시스템 확장
4. 버그 수정

이후

5. Proxy Bridge System

---

# 구현 착수 시 결정할 사항

아래 항목은 실제 사용처가 생겨 Proxy Bridge 구현을 시작할 때 확정한다.

- 서버 이동 처리 지연 시간과 설정 단위
- MASTER 재시작 중 발생한 이벤트 처리 정책
- WebSocket 재접속 간격, 최대 대기 시간, heartbeat 정책
- TLS 사용 방식과 인증서 설정
- Bridge별 개별 폐기와 전체 키 교체 명령 구조
- `bridge.key` 암호화 및 파일 권한 처리
- Action Packet 및 결과 Packet의 최종 스키마
- eventId 캐시의 보존 개수, 만료 시간, 영속 저장 여부
- ACK 유실 시 재전송 여부와 최대 재시도 횟수
- 이벤트 순서 보장 범위
- 서버 이동 지연 중 여러 이벤트가 도착했을 때의 처리 순서
- MASTER에 연결된 CLIENT 상태 조회 명령과 로그 형식

---

# 결론

McStreamApi는 기본적으로 단일 서버 플러그인으로 유지한다.

Proxy 기능은 선택 기능이다.

Proxy 모드에서도 기존 Reward 시스템을 그대로 사용하며,
MASTER는 판정 및 이벤트 처리를 담당하고,
ClientBridge는 실행만 담당한다.
