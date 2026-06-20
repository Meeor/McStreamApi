# McStreamApi

McStreamApi는 Chzzk/SOOP OAuth 계정 연결을 통해 Minecraft 플레이어와 스트리머 계정을 연결하고, 후원 이벤트가 들어오면 Paper/Spigot 서버에서 보상을 실행하는 플러그인입니다.

현재 Chzzk와 SOOP OAuth 연결, 후원 이벤트 세션, 보상 매칭/실행이 구현되어 있습니다.

## 구성

```text
McStreamApi/
  plugin/       Minecraft Paper/Spigot 플러그인
  auth-server/  OAuth redirect/token 교환용 독립 jar 서버
  docs/         로컬/배포/운영 문서
```

AuthServer는 외부 HTTPS URL을 받고, 내부에서는 `127.0.0.1:<port>`로만 실행하는 구성을 권장합니다. Minecraft 플러그인은 AuthServer와 `sharedSecret`으로 통신합니다.

## 지원 상태

| 플랫폼 | OAuth 연결 | 후원 이벤트 | 비고 |
| --- | --- | --- | --- |
| Chzzk | 구현됨 | 구현됨 | 치즈/실시간 후원 이벤트 수신 |
| SOOP | 구현됨 | 구현됨 | 별풍선 후원 이벤트 수신 |

## 빌드

Java 21이 필요합니다.

```bash
./gradlew build
```

Windows:

```powershell
.\gradlew.bat build --no-daemon --console=plain
```

산출물:

```text
plugin/build/libs/McStreamApi-<version>.jar
auth-server/build/libs/McStreamApi-AuthServer-<version>.jar
```

릴리즈 zip 생성 방법은 [docs/release.md](docs/release.md)를 참고하세요.

## Plugin 설치

1. `plugin/build/libs/McStreamApi-<version>.jar`를 Paper/Spigot 서버의 `plugins/` 폴더에 넣습니다.
2. 서버를 한 번 실행합니다.
3. `plugins/McStreamApi/`에 기본 설정 파일이 생성되면 서버를 종료합니다.
4. `config.yml`, `Api.yml`, `random.yml`, `custom-item.yml`을 수정합니다. `streamer-rewards.yml`은 `streamerRewards.enabled: true`일 때 생성됩니다.
5. 서버를 다시 실행합니다.

최초 실행 때는 기본 설정만 생성하고 런타임 기능은 비활성화됩니다. 실제 OAuth 값과 `sharedSecret`을 설정한 뒤 다시 시작해야 합니다.

## AuthServer 실행

AuthServer는 Minecraft 서버와 같은 머신이 아니어도 됩니다. 플러그인에서 접근 가능한 HTTPS 주소와 동일한 `sharedSecret`만 맞으면 됩니다.

권장 서버 구조:

```text
/opt/mcstreamapi-authserver/
  McStreamApi-AuthServer.jar
  config.yml
  ASstart.sh
  ASstop.sh
  logs/
```

실행:

```bash
cd /opt/mcstreamapi-authserver
chmod +x ASstart.sh ASstop.sh
./ASstart.sh
```

중지:

```bash
./ASstop.sh
```

직접 실행:

```bash
java -jar McStreamApi-AuthServer.jar --config ./config.yml
```

설정만 검증:

```bash
java -jar McStreamApi-AuthServer.jar --config ./config.yml --check-config
```

## sharedSecret 생성

Plugin과 AuthServer에 같은 값을 넣어야 합니다.

```bash
python generate_shared_secret.py
```

설정 위치:

```yml
# Plugin config.yml
auth:
  sharedSecret: "mca_GENERATED_RANDOM_SECRET_HERE"
```

```yml
# AuthServer config.yml
security:
  sharedSecret: "mca_GENERATED_RANDOM_SECRET_HERE"
```

`sharedSecret`, `clientSecret`, OAuth token 파일은 공개 저장소에 올리지 마세요.

## Chzzk 개발자 설정

Chzzk 개발자센터에서 앱을 만들고 Redirect URL을 AuthServer 외부 URL과 맞춥니다.

```text
https://auth.example.com/mca/oauth/chzzk/callback
```

권장 scope:

```yml
scopes:
  - "유저 조회"
  - "후원 조회"
  - "구독 조회"
```

현재 AuthServer는 Chzzk 인증 URL에 scope 파라미터를 직접 붙이지 않습니다. 실제 권한은 개발자센터에서 선택한 API scope 기준으로 결정됩니다. `config.yml`의 `scopes`는 토큰 응답에 scope가 없을 때 기록용 fallback으로 사용됩니다.

## SOOP 개발자 설정

SOOP OAuth 설정은 `auth-server.config.example.yml`의 endpoint를 기준으로 합니다.

```text
https://auth.example.com/mca/oauth/soop/callback
```

SOOP 후원 이벤트는 별풍선 개수 기준으로 보상 매칭됩니다. 예를 들어 별풍선 1개는 `amount: "1"`입니다.

```yml
platforms:
  soop:
    enabled: true
```

## Nginx 예시

AuthServer 내부 포트가 `18080`인 경우:

```nginx
location = /mca {
    return 301 /mca/;
}

location /mca/ {
    proxy_pass http://127.0.0.1:18080/;
    proxy_http_version 1.1;

    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;

    proxy_connect_timeout 10s;
    proxy_send_timeout 30s;
    proxy_read_timeout 30s;
}
```

`proxy_pass` 끝의 `/`가 중요합니다. `/mca/health`가 AuthServer 내부 `/health`로 전달됩니다.

확인:

```bash
curl http://127.0.0.1:18080/health
curl https://auth.example.com/mca/health
curl https://auth.example.com/mca/ready
```

## 명령어

```text
/mca connect <chzzk|soop>
/mca reload
/mca apply <player> <amount> [chzzk|soop]
/mca apply-streamer <player> <amount> [chzzk|soop]
/mca status
```

`/mca connect`는 플레이어만 사용할 수 있습니다. 인증 URL은 클릭 가능한 `[연결하러 가기]` 텍스트로 표시되고, pairing code는 콘솔 로그에만 남습니다.
`/mca apply`는 수동 테스트용 보상 적용 명령어입니다. 대상 플레이어가 한 플랫폼에만 연결되어 있으면 플랫폼을 생략할 수 있고, 연결 토큰이 없거나 Chzzk/SOOP 둘 다 연결되어 있으면 `chzzk` 또는 `soop`을 직접 지정해야 합니다.
`/mca apply-streamer`는 `streamer-rewards.yml`의 전용 보상만 테스트합니다. 저장된 UUID는 Mojang 프로필 조회 결과로 플레이어 이름 자동완성에 표시되며, 조회 전이나 실패 시 UUID가 표시됩니다. 전용 플랫폼이 하나면 플랫폼을 생략할 수 있습니다.

권한:

| 권한 | 설명 |
| --- | --- |
| `mcstreamapi.reload` | `/mca reload` |
| `mcstreamapi.apply` | `/mca apply`, `/mca apply-streamer` |
| `mcstreamapi.status` | `/mca status` |

콘솔은 관리 명령어 권한 검사 없이 사용할 수 있습니다.

## Api.yml 보상 스키마

```yml
rewards:
  chzzk:
    - id: "chzzk_1000"
      amount: "1000"
      chance: 100
      actions:
        - type: "broadcast"
          message: "&a{donator}님이 {streamer}에게 {amount}원을 후원했습니다."
```

플레이어 UUID별 전용 보상은 `config.yml`의 `streamerRewards.enabled`를 `true`로 설정하면 자동 생성되는 `streamer-rewards.yml`에 정의할 수 있습니다. `false`이면 파일을 생성하거나 읽지 않습니다. 전용 보상이 실제로 매칭되지 않으면 `Api.yml` 기본 보상으로 폴백합니다. 자세한 형식은 [docs/streamer-rewards-yml.md](docs/streamer-rewards-yml.md)를 참고하세요.

`amount` 형식:

| 형식 | 의미 |
| --- | --- |
| `"1000"` | 정확히 1000 |
| `"1000-5000"` | 1000 이상 5000 이하 |
| `"5000+"` | 5000 이상 |

`chzzk`의 `amount`는 원 단위입니다. `soop`의 별풍 이벤트 `amount`는 별풍선 개수입니다.

`chance`는 생략하면 `100`으로 처리됩니다. 같은 우선순위의 reward가 여러 개면 chance 가중치로 하나만 선택됩니다.

Action 타입:

| type | 필드 |
| --- | --- |
| `cmd` | `command` |
| `at_player_cmd` | `command` |
| `summon` | `target`, `entity` |
| `sound` | `target`, `sound`, `source`, `volume`, `pitch` |
| `give` | `target`, `material`, `amount`, `name`, `lore` |
| `custom-give` | `target`, `item`, `amount` |
| `chat` | `message` - 인증 플레이어에게만 보이는 개인 메시지 |
| `broadcast` | `message` |
| `title` | `target`, `title`, `subtitle`, `fadeInTicks`, `stayTicks`, `fadeOutTicks` |

`target: "@s"`는 인증된 Minecraft 플레이어로 치환됩니다.
`give`에서 `target`을 생략하면 `@s`, `amount`를 생략하면 `1`로 처리됩니다.
`summon`에는 수량 인자가 없습니다. `{random.key}` 또는 `{random_once.key}`로 선택된 `random.yml` 항목의 `amount`만 소환 수량으로 사용하며, 없으면 1마리로 처리됩니다.
`give.amount`와 `random.yml`의 랜덤 항목 `amount`는 `<5..15>`, `<..100>` 같은 랜덤 범위를 사용할 수 있습니다. `<..100>`은 `1~100`입니다.
복잡한 아이템 메타는 `custom-item.yml`에 정의하고 `custom-give`로 참조합니다. 자세한 형식은 [docs/custom-item-yml.md](docs/custom-item-yml.md)를 참고하세요.

Placeholder:

```text
{player}
{player_uuid}
{streamer}
{platform}
{donator}
{amount}
{unit_count}
{message}
{random.key}
{random_once.key}
```

메시지 계열 액션은 `&a` 같은 레거시 색상 코드와 `&#12ABEF` 형식의 HEX 색상 코드를 지원합니다.

`random.yml` 형식과 `{random.key}` / `{random_once.key}` 차이는 [docs/random-yml.md](docs/random-yml.md)를 참고하세요.
`unitAmount`를 지정하면 `{unit_count}`에 후원 수량을 기준 단위로 나눈 정수 몫이 들어갑니다. 자세한 형식은 [docs/api-yml.md](docs/api-yml.md)를 참고하세요.
`bonusAmount`와 `bonusCount`를 함께 지정하면 보너스 구간마다 추가 수량을 누적할 수 있습니다.
액션 수량에는 `{unit_count+5}` / `{unit_count-1}`처럼 고정 보너스 연산도 사용할 수 있습니다.

## Troubleshooting

AuthServer가 시작되지 않음:

- Java 21로 실행했는지 확인합니다.
- `--check-config`로 설정 오류를 먼저 확인합니다.
- `sharedSecret`이 `CHANGE_ME_RANDOM_LONG_SECRET` 그대로면 실패합니다.
- `publicBaseUrl`은 운영 환경에서 HTTPS여야 합니다.

`/mca connect`가 실패함:

- Plugin `auth.serverBaseUrl`이 외부 AuthServer URL과 맞는지 확인합니다.
- Plugin `auth.sharedSecret`과 AuthServer `security.sharedSecret`이 완전히 같은지 확인합니다.
- AuthServer `/health`, `/ready`가 외부 URL로 열리는지 확인합니다.

Chzzk callback 실패:

- 개발자센터 Redirect URL과 AuthServer `redirectUri`가 정확히 같은지 확인합니다.
- `www` 포함 여부, `/mca` 경로, HTTPS를 섞지 마세요.

후원 이벤트가 들어오지 않음:

- Chzzk 앱에 `후원 조회` 권한이 있는지 확인합니다.
- 해당 계정이 실제 후원 이벤트를 받을 수 있는 스트리머 계정인지 확인합니다.
- `/mca reload` 후 `debug: true` 상태에서 콘솔에 `CHZZK WebSocket payload 수신`, `CHZZK 후원 감지`, `SOOP 패킷 수신`, `SOOP 별풍 감지` 로그가 나오는지 확인합니다.
- Chzzk는 Session API 인증 URL 생성, WebSocket 연결, donation subscribe 성공 로그가 순서대로 나와야 합니다.
- SOOP는 WebSocket 연결, 로그인 성공, 채팅방 join 성공 로그가 순서대로 나와야 합니다.
- 보상은 Chzzk `amount`가 원 단위, SOOP `amount`가 별풍선 개수 기준입니다.

## 보안

- 실제 `config.yml`, `Api.yml`, `streamer-rewards.yml`, `random.yml`, `custom-item.yml`, `secret.key`, `tokens/`는 Git에 올리지 않습니다.
- AuthServer 포트는 외부에 직접 열지 말고 Nginx/HTTPS 뒤에 둡니다.
- 로그와 오류 응답은 token/clientSecret/sharedSecret을 직접 출력하지 않도록 테스트되어 있습니다.
- 공개 전 `rg "clientSecret|sharedSecret|accessToken|refreshToken"`로 한 번 더 확인하세요.
