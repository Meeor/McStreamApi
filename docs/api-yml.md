# Api.yml

`Api.yml`은 후원 금액과 실행할 보상을 정의합니다.

## 기본 구조

```yml
rewards:
  chzzk:
    - id: "chzzk_1000"
      amount: "1000"
      chance: 100
      actions:
        - type: "broadcast"
          message: "&a{donator}님이 {amount}원을 후원했습니다."
```

## amount

- `"1000"`: 정확히 1000
- `"1000-5000"`: 1000 이상 5000 이하
- `"5000+"`: 5000 이상

`chzzk`의 `amount`는 원 단위입니다. `soop`의 별풍 이벤트 `amount`는 별풍선 개수입니다.

정확한 금액 조건이 range보다 우선하고, range가 plus보다 우선합니다.
여러 plus 조건이 동시에 맞으면 기준값이 가장 큰 조건만 선택합니다. 예를 들어 `100+`와 `1000+`가 있으면 1000 이상에서는 `1000+`만 후보가 됩니다.

플레이어 전용 보상을 활성화한 경우 전체 선택 순서는 `전용 고정 > 공용 고정 > 전용 범위 > 공용 범위 > 전용 플러스 > 공용 플러스`입니다. 후원 이벤트 하나에는 이 순서로 선택된 보상 하나만 실행됩니다.

## unitAmount

`unitAmount`를 지정하면 선택된 reward에서 `{unit_count}`를 `amount / unitAmount`의 정수 몫으로 계산합니다.

```yml
- id: "soop_per_100"
  amount: "100+"
  unitAmount: 100
  actions:
    - type: "give"
      target: "@s"
      material: "DIAMOND"
      amount: "{unit_count}"
    - type: "broadcast"
      message: "{donator}님 후원 보상 {unit_count}개 지급"
```

- 100개: `{unit_count}` = 1
- 250개: `{unit_count}` = 2
- 300개: `{unit_count}` = 3
- 나머지는 버립니다.
- `unitAmount`는 1 이상의 정수여야 합니다.
- `unitAmount`를 생략하면 `{unit_count}`는 1입니다.

## bonusAmount / bonusCount

기준 수량에 구간별 보너스를 누적하려면 `bonusAmount`와 `bonusCount`를 함께 지정합니다.

```yml
- id: "soop_coin"
  amount: "100+"
  unitAmount: 100
  bonusAmount: 1000
  bonusCount: 1
  actions:
    - type: "give"
      material: "GOLD_NUGGET"
      amount: "{unit_count}"
```

계산식은 `(amount / unitAmount) + (amount / bonusAmount * bonusCount)`이며 각 나눗셈의 나머지는 버립니다.

- 100개: 1개
- 300개: 3개
- 500개: 5개
- 1000개: 11개
- 5000개: 55개
- 10000개: 110개

`bonusAmount`와 `bonusCount`는 반드시 함께 지정해야 하며 모두 1 이상의 정수여야 합니다. 보너스 설정에는 `unitAmount`도 필요합니다.

액션 수량에서는 `{unit_count+N}` 또는 `{unit_count-N}`으로 고정 보너스를 더하거나 뺄 수 있습니다.

```yml
- id: "soop_5000"
  amount: "5000"
  unitAmount: 100
  actions:
    - type: "give"
      material: "GOLD_NUGGET"
      amount: "{unit_count+5}" # 5000 / 100 + 5 = 55
```

계산 결과는 1 이상의 정수여야 합니다.

예를 들어 `amount: "1000"`인 고정보상이 별도로 있으면 정확한 금액 조건이 우선하므로, 1000개에는 `amount: "100+"` 보상이 중복 실행되지 않습니다.

## chance

`chance`는 생략하면 `100`입니다.

```yml
- id: "always"
  amount: "1000"
  actions:
    - type: "broadcast"
      message: "항상 실행"
```

동일 조건의 reward가 여러 개면 chance 가중치로 하나만 선택됩니다. `chance <= 0`인 reward는 후보에서 제외됩니다.

## actions

메시지, 타이틀, 아이템 이름과 lore에는 `&a`, `&l`, `&r` 같은 레거시 색상 코드와 `&#12ABEF` 형식의 HEX 색상 코드를 사용할 수 있습니다. `&l` 굵게는 뒤에서 색상 코드가 바뀌어도 유지되며 `&r`에서 해제됩니다.

`chat`, `broadcast`, `title` 안의 랜덤 아이템·몹·버프 값은 Minecraft 번역 키로 전송되어 각 플레이어의 클라이언트 언어로 표시됩니다. 명령어, 소환, 지급에 쓰는 내부 ID는 번역하지 않습니다. `random.yml` 항목에 `display`를 직접 작성하면 해당 문구가 우선합니다.

### cmd

```yml
- type: "cmd"
  command: "give {player} diamond 1"
```

콘솔 기준으로 그대로 실행됩니다. 위치 기준 명령은 `at_player_cmd` 또는 `summon`을 사용하세요.

### at_player_cmd

```yml
- type: "at_player_cmd"
  command: "summon minecraft:chicken ~ ~ ~"
```

인증된 Minecraft 플레이어 위치에서 명령을 실행합니다. 내부적으로 `execute at {player} run ...` 형태로 실행됩니다.

### summon

```yml
- type: "summon"
  target: "@s"
  entity: "minecraft:zombie"
```

`target` 위치 기준으로 엔티티를 소환합니다. `target`은 생략 가능하며 기본값은 `@s`입니다. `summon` 액션에는 수량 인자가 없고, `{random.key}` 또는 `{random_once.key}`로 선택된 `random.yml` 항목의 `amount`만 소환 수량으로 사용합니다. 랜덤 항목에 `amount`가 없으면 1마리만 소환합니다.

### sound

```yml
- type: "sound"
  target: "@s"
  sound: "minecraft:entity.player.levelup"
  source: "master"
  volume: 1.0
  pitch: 1.0
```

`target` 위치에서 대상 플레이어에게 사운드를 재생합니다. `target`, `source`, `volume`, `pitch`는 생략 가능하며 기본값은 각각 `@s`, `master`, `1.0`, `1.0`입니다.

### give

간단한 즉석 아이템 지급용입니다. 복잡한 item meta가 필요하면 `custom-item.yml`에 아이템을 정의하고 `custom-give`를 사용하세요.

```yml
- type: "give"
  target: "@s"
  material: "DIAMOND"
  amount: "<1..5>"
  name: "&b후원 다이아몬드"
  lore:
    - "&7후원자: {donator}"
```

`target`을 생략하면 `@s`로 처리되어 인증된 Minecraft 플레이어에게 지급됩니다. `amount`를 생략하면 `1`입니다.

### custom-give

복잡한 아이템은 `custom-item.yml`에 정의하고, `Api.yml`에서는 이름만 참조합니다.

```yml
- type: "custom-give"
  target: "@s"
  item: "{item.donation_diamond}"
```

`item`은 `{item.donation_diamond}`처럼 쓰는 것을 권장합니다. `target`, `amount`를 `custom-give`에 적으면 `custom-item.yml`의 값을 덮어씁니다.

`custom-item.yml`:

```yml
items:
  donation_diamond:
    material: "DIAMOND"
    amount: 1
    name: "&b후원 다이아몬드"
    lore:
      - "&7후원자: {donator}"
      - "&7금액: {amount}"
    customModelData: 1001
    glow: true
    unbreakable: true
    itemFlags:
      - "HIDE_ENCHANTS"
      - "HIDE_ATTRIBUTES"
    enchantments:
      minecraft:unbreaking: 3
    persistentData:
      - key: "mcstreamapi:item_id"
        type: "string"
        value: "donation_diamond"
      - key: "mcstreamapi:amount"
        type: "long"
        value: 1000
```

`custom-item.yml`에서 지원하는 주요 item meta:

- `name`, `lore`, `customModelData`, `unbreakable`
- `glow` 또는 `glint`: 실제 인챈트 효과 없이 반짝임 표시
- `itemFlags` 또는 `item-flags`: Bukkit `ItemFlag` 이름
- `enchantments`: `minecraft:unbreaking: 3` 같은 map 또는 `{ enchantment, level }` 목록
- `persistentData`, `pdc`, `itemTag`: PDC/custom tag. 타입은 `string`, `int`, `long`, `double`, `boolean`
- `playerHead`: `PLAYER_HEAD`일 때 머리 주인 플레이어 이름
- `customPotionEffects`: 포션 효과 목록
- `book`: 책 제목, 작성자, 페이지
- `attributes`: attribute modifier 목록

포션/책/속성 예:

```yml
items:
  speed_potion:
    material: "POTION"
  customPotionEffects:
    - effect: "minecraft:speed"
      durationTicks: 600
      amplifier: 1

  donation_book:
    material: "WRITTEN_BOOK"
    book:
      title: "후원 기록"
      author: "McStreamApi"
      pages:
        - "{donator} / {amount}"

  strong_sword:
    material: "DIAMOND_SWORD"
    attributes:
      - attribute: "minecraft:generic.attack_damage"
        amount: 2.0
        operation: "ADD_NUMBER"
        slot: "mainhand"
```

`give.amount`와 `random.yml`의 랜덤 항목 `amount`는 고정 숫자 또는 랜덤 범위를 사용할 수 있습니다.

```yml
amount: 3        # 항상 3개
amount: "<5..7>" # 5~7 중 랜덤
amount: "<..100>" # 1~100 중 랜덤
```

`{5..15}` 형태도 인식하지만 YAML 문법과 헷갈릴 수 있으므로 `<5..15>` 형태를 권장합니다.

### chat

```yml
- type: "chat"
  message: "&a고마워요!"
```

인증된 Minecraft 플레이어에게만 보이는 개인 메시지를 보냅니다. 플레이어가 직접 채팅을 친 것처럼 전체 채팅에 발화하지 않습니다.

### broadcast

```yml
- type: "broadcast"
  message: "&a{donator}님이 {amount}원을 후원했습니다."
```

### title

```yml
- type: "title"
  target: "@s"
  title: "&e후원 도착"
  subtitle: "&f{donator} / {amount}"
  fadeInTicks: 10
  stayTicks: 70
  fadeOutTicks: 20
```

## placeholders

- `{player}`: Minecraft 플레이어
- `{player_uuid}`: Minecraft 플레이어 UUID (`{uuid}`도 동일)
- `{streamer}`: 스트리머 이름
- `{platform}`: 플랫폼
- `{donator}`: 후원자 이름
- `{amount}`: 후원 금액
- `{unit_count}`: `amount / unitAmount`로 계산한 보상 수량
- `{message}`: 후원 메시지
- `{random.key}`: 같은 이벤트 안에서 같은 랜덤값 유지
- `{random_once.key}`: 호출마다 새 랜덤값 선택

랜덤 테이블 작성법은 [random-yml.md](random-yml.md)를 참고하세요.
