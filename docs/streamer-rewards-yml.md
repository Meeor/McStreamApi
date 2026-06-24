# streamer-rewards.yml

`streamer-rewards.yml`은 Minecraft 플레이어 UUID별 전용 보상을 정의합니다.

먼저 `config.yml`에서 기능을 활성화해야 합니다.

```yml
streamerRewards:
  enabled: true
```

`false`이면 `streamer-rewards.yml`을 생성하거나 읽거나 조회하지 않고 항상 `Api.yml`만 사용합니다. 기본값은 `false`입니다. `true`로 변경한 뒤 서버를 시작하면 파일이 없을 때 자동 생성됩니다.

```yml
streamers:
  "00000000-0000-0000-0000-000000000001":
    soop:
      - id: "custom_soop_1000"
        amount: "1000"
        allowDuplicate: true
        actions:
          - type: "broadcast"
            message: "&a전용 SOOP 메시지"

    chzzk: []
```

UUID와 플랫폼 이름은 대소문자를 구분하지 않습니다. 보상 내부 문법은 `Api.yml`과 동일합니다.

## 선택 및 폴백 규칙

보상 이벤트가 들어오면 전용 보상과 `Api.yml` 공용 보상을 함께 비교해 다음 순서로 선택합니다.

1. 전용 고정
2. 공용 고정
3. 전용 범위
4. 공용 범위
5. 전용 플러스
6. 공용 플러스

예를 들어 전용 설정과 공용 설정에 모두 `100+`가 있으면 전용 보상을 사용합니다. 전용 설정에 `100+`가 있어도 공용 설정에 정확한 `10000`이 있으면 공용 고정보상을 사용합니다. 기본값에서는 선택된 보상 하나만 실행되므로 두 보상이 중복 지급되지 않습니다.

전용 보상에 `allowDuplicate: true`를 넣으면 해당 전용 보상이 매칭된 뒤 공용 보상 매칭도 계속 진행합니다. 이 옵션은 “보상 지급은 공용으로 유지하고, 스트리머별 메시지만 별도로 출력”하는 용도입니다.

```yml
streamers:
  "00000000-0000-0000-0000-000000000001":
    soop:
      - id: "meeor_message_100_plus"
        amount: "100+"
        allowDuplicate: true
        actions:
          - type: "broadcast"
            message: "&aMeeor 전용 메시지"
```

위 설정에서 `Api.yml`에도 `soop`의 `100+` 공용 보상이 있으면 전용 메시지를 출력한 뒤 공용 보상을 이어서 실행합니다. `allowDuplicate` 기본값은 `false`입니다.

플랫폼 키가 없거나 `[]`로 비어 있으면 해당 플랫폼은 `Api.yml`로 폴백합니다.

보상을 선택한 뒤 액션 파싱이나 실행이 실패해도 그 실패 때문에 다음 순위 보상을 새로 찾아 실행하지 않습니다. 단, 이미 `allowDuplicate: true`로 함께 선택된 공용 보상은 같은 적용 묶음 안에서 실행 대상에 포함됩니다.

## 전용 보상 테스트

```text
/mca apply-streamer <player> <amount> [chzzk|soop]
```

이 명령은 전용 보상만 실행하며, 매칭되는 전용 보상이 없어도 `Api.yml`로 폴백하지 않습니다. 저장된 UUID는 플러그인 시작 및 `/mca reload` 때 Mojang 프로필 서버에서 비동기로 이름을 조회해 자동완성에 사용합니다. 조회가 끝나기 전이거나 실패하면 UUID로 입력할 수 있습니다.

플레이어의 전용 플랫폼이 하나면 플랫폼을 생략할 수 있고, 여러 개면 마지막 인자로 플랫폼을 지정해야 합니다. 권한은 기존 수동 적용과 동일한 `mcstreamapi.apply`입니다.
