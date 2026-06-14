# custom-item.yml

`custom-item.yml`은 복잡한 아이템 메타를 별도 파일로 분리합니다. `Api.yml`에서는 `custom-give`로 이름만 참조합니다.

```yml
# Api.yml
- type: "custom-give"
  target: "@s"
  item: "{item.donation_diamond}"
```

`custom-give.item`은 `{item.donation_diamond}`처럼 쓰는 것을 권장합니다.

## 기본 구조

```yml
items:
  donation_diamond:
    material: "DIAMOND"
    amount: 1
    name: "&b후원 다이아몬드"
    lore:
      - "&7후원자: {donator}"
      - "&7금액: {amount}"
    glow: true
    persistentData:
      - key: "mcstreamapi:item_id"
        type: "string"
        value: "donation_diamond"
```

각 아이템 이름(`donation_diamond`)은 `custom-give.item`에서 참조하는 키입니다.

## 공통 입력 규칙

- 문자열에는 `{player}`, `{donator}`, `{amount}`, `{random.key}` 같은 placeholder를 사용할 수 있습니다.
- 색상 코드는 `&a`, `&b`, `&7` 같은 Minecraft 색상 코드 형식을 사용합니다.
- `material`, enchantment, potion effect, attribute는 Bukkit/Paper 레지스트리 이름을 사용합니다.
- 레지스트리 이름은 보통 `minecraft:diamond`, `minecraft:unbreaking`처럼 쓰거나, `DIAMOND`, `UNBREAKING`처럼 짧게 쓸 수 있습니다.
- 잘못된 enchantment/effect/attribute/itemFlag 값은 적용되지 않습니다. 아이템 지급 자체는 계속 시도됩니다.

## 지원 필드 전체

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `material` | string | 아이템 종류. 필수 |
| `amount` | number/string | 지급 개수. 생략 시 `custom-give` 또는 기본 `give` 규칙에 따라 1 |
| `name` | string | 아이템 표시 이름 |
| `lore` | string list | 아이템 설명 줄 목록 |
| `customModelData` | int | 리소스팩 커스텀 모델 데이터 |
| `unbreakable` | boolean | 내구도 소모 방지 |
| `glow` | boolean | 실제 인챈트 없이 반짝임 표시 |
| `glint` | boolean | `glow` 별칭 |
| `itemFlags` | string list | 툴팁 숨김 플래그 |
| `item-flags` | string list | `itemFlags` 별칭 |
| `enchantments` | map/list | 인챈트 목록 |
| `persistentData` | list/map | PDC 커스텀 태그 |
| `pdc` | list/map | `persistentData` 별칭 |
| `itemTag` | list/map | `persistentData` 별칭 |
| `playerHead` | string | `PLAYER_HEAD`의 머리 주인 이름 |
| `skullOwner` | string | `playerHead` 별칭 |
| `playerName` | string | `playerHead` 별칭 |
| `customPotionEffects` | list | 커스텀 포션 효과 |
| `potionEffects` | list | `customPotionEffects` 별칭 |
| `book` | object | 책 제목/작성자/페이지 |
| `attributes` | list | Attribute modifier 목록 |

## material

`material`은 Bukkit `Material` 이름입니다.

```yml
material: "DIAMOND"
material: "minecraft:diamond"
material: "PLAYER_HEAD"
material: "WRITTEN_BOOK"
material: "POTION"
```

가능한 값은 서버 버전의 Bukkit/Paper `Material` 목록을 따릅니다. 너무 많기 때문에 이 문서에는 전부 나열하지 않습니다. 실무에서는 Minecraft 아이템 ID에서 `minecraft:`를 붙이거나, Bukkit enum처럼 대문자와 `_`를 쓰면 됩니다.

예:

- `minecraft:diamond` 또는 `DIAMOND`
- `minecraft:netherite_sword` 또는 `NETHERITE_SWORD`
- `minecraft:player_head` 또는 `PLAYER_HEAD`
- `minecraft:written_book` 또는 `WRITTEN_BOOK`
- `minecraft:splash_potion` 또는 `SPLASH_POTION`

## amount

고정 숫자 또는 랜덤 범위를 사용할 수 있습니다.

```yml
amount: 1
amount: "<1..3>"
amount: "<..100>"
```

- `1`: 항상 1개
- `<1..3>`: 1~3 중 랜덤
- `<..100>`: 1~100 중 랜덤

`custom-give`에 `amount`를 적으면 `custom-item.yml`의 `amount`를 덮어씁니다.

## item placeholder

`custom-item.yml`에 등록된 아이템은 메시지에서 이름과 수량을 꺼내 쓸 수 있습니다.

```yml
# custom-item.yml
items:
  rare_emerald:
    material: "EMERALD"
    amount: "<1..3>"
    name: "&a희귀 에메랄드"
```

```yml
# Api.yml
- type: "broadcast"
  message: "{item.rare_emerald.name} {item.rare_emerald.amount}개 지급"
- type: "custom-give"
  target: "@s"
  item: "{item.rare_emerald}"
```

`amount`가 범위이면 같은 이벤트 안에서 한 번만 뽑아 `{item.rare_emerald.amount}`와 실제 `custom-give` 지급 수량이 같게 유지됩니다. 단, `custom-give.amount`로 수량을 덮어쓴 경우에는 `{item.key.amount}`가 `custom-item.yml`의 기본 수량을 의미하므로 같이 쓰지 않는 것을 권장합니다.

## random.yml로 커스텀 아이템 선택

`custom-give.item`에는 `{random.key}` 또는 `{random_once.key}`를 사용할 수 있습니다. 이때 `random.yml`의 `value`는 `{item.아이템이름}` 형식을 사용합니다.

```yml
# random.yml
donation_item_random:
  - value: "{item.donation_diamond}"
    chance: 70
  - value: "{item.rare_emerald}"
    chance: 30
```

```yml
# Api.yml
- type: "broadcast"
  message: "{random.donation_item_random.item.name} {random.donation_item_random.item.amount}개 지급"
- type: "custom-give"
  target: "@s"
  item: "{random.donation_item_random}"
```

선택된 커스텀 아이템의 `amount`, `name`, `lore`, meta가 그대로 적용됩니다. 메시지에서는 `{random.key.item.name}`과 `{random.key.item.amount}`로 선택된 커스텀 아이템의 이름과 수량을 표시할 수 있습니다. `amount`가 범위이면 같은 이벤트 안에서 한 번만 뽑아 메시지와 실제 지급 수량이 같게 유지됩니다.

`random.yml`에서 커스텀 아이템을 고를 때는 반드시 `- "{item.rare_emerald}"` 또는 `value: "{item.rare_emerald}"`처럼 적어야 합니다. `- "rare_emerald"` 또는 `value: "rare_emerald"`처럼 bare 이름만 적는 형식은 일반 아이템 랜덤과 구분되지 않으므로 허용하지 않습니다. `random.yml` 항목의 `amount`는 summon 전용이므로 `custom-give` 수량에는 적용되지 않습니다.

## name / lore

```yml
name: "&b후원 검"
lore:
  - "&7후원자: {donator}"
  - "&7금액: {amount}"
```

`name`과 `lore`는 색상 코드와 placeholder를 지원합니다.

## customModelData

리소스팩에서 커스텀 모델을 연결할 때 쓰는 정수입니다.

```yml
customModelData: 1001
```

## unbreakable

```yml
unbreakable: true
```

`true`면 내구도가 닳지 않습니다. 툴팁 표시를 숨기려면 `itemFlags`에 `HIDE_UNBREAKABLE`을 같이 넣습니다.

## glow / glint

```yml
glow: true
```

실제 인챈트 없이 아이템이 인챈트된 것처럼 반짝이게 합니다. `glint`도 같은 의미로 사용할 수 있습니다.

## itemFlags

툴팁에서 일부 정보를 숨깁니다.

```yml
itemFlags:
  - "HIDE_ENCHANTS"
  - "HIDE_ATTRIBUTES"
```

지원 값:

- `HIDE_ENCHANTS`
- `HIDE_ATTRIBUTES`
- `HIDE_UNBREAKABLE`
- `HIDE_DESTROYS`
- `HIDE_PLACED_ON`
- `HIDE_ADDITIONAL_TOOLTIP`
- `HIDE_DYE`
- `HIDE_ARMOR_TRIM`
- `HIDE_STORED_ENCHANTS`

`hide-enchants`, `hide enchants`처럼 써도 내부에서 `HIDE_ENCHANTS` 형식으로 정규화합니다.

## enchantments

간단한 map 형식:

```yml
enchantments:
  minecraft:unbreaking: 3
  minecraft:sharpness: 5
```

상세 list 형식:

```yml
enchantments:
  - enchantment: "minecraft:unbreaking"
    level: 3
    ignoreLevelRestriction: true
  - enchantment: "minecraft:sharpness"
    level: 5
```

필드:

- `enchantment`: 인챈트 ID. `type`, `name`도 별칭으로 사용 가능
- `level`: 레벨. 생략 시 list 형식에서는 1
- `ignoreLevelRestriction`: Bukkit 레벨 제한 무시 여부. 생략 시 `true`

대표 enchantment:

- `minecraft:unbreaking`
- `minecraft:mending`
- `minecraft:sharpness`
- `minecraft:efficiency`
- `minecraft:fortune`
- `minecraft:silk_touch`
- `minecraft:protection`
- `minecraft:power`
- `minecraft:infinity`

가능한 값은 서버 버전의 Bukkit/Paper enchantment 레지스트리를 따릅니다.

## persistentData / pdc / itemTag

PDC는 외부 플러그인이 이 아이템을 식별하거나 커스텀 정보를 저장할 때 쓰는 태그입니다.

list 형식:

```yml
persistentData:
  - key: "mcstreamapi:item_id"
    type: "string"
    value: "donation_diamond"
  - key: "mcstreamapi:donation_amount"
    type: "long"
    value: 1000
  - key: "mcstreamapi:special"
    type: "boolean"
    value: true
```

map 형식:

```yml
pdc:
  mcstreamapi:item_id:
    type: "string"
    value: "donation_diamond"
  mcstreamapi:level:
    type: "int"
    value: 3
```

단순 map 형식도 가능합니다. 이 경우 타입은 값 형태로 추정합니다.

```yml
itemTag:
  mcstreamapi:item_id: "donation_diamond"
  mcstreamapi:level: 3
  mcstreamapi:special: true
```

지원 타입:

- `string`: 문자열
- `int`: 32-bit 정수
- `long`: 64-bit 정수
- `double`: 소수
- `boolean`: true/false. 내부 저장은 byte 1/0

주의:

- PDC의 `string` 값은 placeholder를 사용할 수 있습니다.
- `int`, `long`, `double`, `boolean` 값은 파일 로딩 시점에 타입 변환되므로 숫자/boolean 리터럴을 직접 적어야 합니다.
- 예를 들어 `type: "long"`, `value: "{amount}"`는 사용할 수 없습니다. 후원 금액을 PDC에 남기고 싶으면 `type: "string"`으로 저장하세요.

`key` 규칙:

- `namespace:key` 형식을 권장합니다.
- 예: `mcstreamapi:item_id`, `myplugin:special_item`
- namespace가 없으면 플러그인 namespace 기준으로 처리됩니다.

namespace를 생략한 예:

```yml
persistentData:
  - key: "shop"
    type: "string"
    value: "coin"
```

위 설정은 내부적으로 아래처럼 저장됩니다.

```text
mcstreamapi:shop = "coin"
```

즉 NBT처럼 단순히 `{shop:coin}`으로 들어가는 것이 아니라, Bukkit PDC의 `PublicBukkitValues` 쪽에 `mcstreamapi:shop` 키와 `"coin"` 문자열 값으로 들어가는 형태입니다.

외부 플러그인에서 고정 namespace로 읽어야 한다면 namespace를 직접 적는 것을 권장합니다.

```yml
persistentData:
  - key: "myshop:shop"
    type: "string"
    value: "coin"
```

이 경우 외부 플러그인은 `myshop:shop` 키로 값을 읽으면 됩니다.

## playerHead / skullOwner / playerName

`material: "PLAYER_HEAD"`일 때 머리 주인을 설정합니다.

```yml
material: "PLAYER_HEAD"
playerHead: "{donator}"
```

별칭:

- `playerHead`
- `skullOwner`
- `playerName`

## customPotionEffects / potionEffects

`POTION`, `SPLASH_POTION`, `LINGERING_POTION`, `TIPPED_ARROW` 등에 커스텀 효과를 추가합니다.

```yml
material: "POTION"
customPotionEffects:
  - effect: "minecraft:speed"
    durationTicks: 600
    amplifier: 1
    ambient: false
    particles: true
    icon: true
```

필드:

- `effect`: 포션 효과 ID. `type`도 별칭으로 사용 가능
- `durationTicks`: 지속 시간 tick. 20 ticks = 1초
- `duration`: `durationTicks` 별칭
- `amplifier`: 효과 증폭값. 0이 레벨 1, 1이 레벨 2
- `ambient`: 주변 효과 여부. 생략 시 `false`
- `particles`: 파티클 표시 여부. 생략 시 `true`
- `icon`: 인벤토리 효과 아이콘 표시 여부. 생략 시 `true`

대표 potion effect:

- `minecraft:speed`
- `minecraft:slowness`
- `minecraft:haste`
- `minecraft:mining_fatigue`
- `minecraft:strength`
- `minecraft:instant_health`
- `minecraft:instant_damage`
- `minecraft:jump_boost`
- `minecraft:nausea`
- `minecraft:regeneration`
- `minecraft:resistance`
- `minecraft:fire_resistance`
- `minecraft:water_breathing`
- `minecraft:invisibility`
- `minecraft:blindness`
- `minecraft:night_vision`
- `minecraft:hunger`
- `minecraft:weakness`
- `minecraft:poison`
- `minecraft:wither`
- `minecraft:health_boost`
- `minecraft:absorption`
- `minecraft:saturation`
- `minecraft:glowing`
- `minecraft:levitation`
- `minecraft:luck`
- `minecraft:slow_falling`
- `minecraft:conduit_power`
- `minecraft:dolphins_grace`
- `minecraft:bad_omen`
- `minecraft:hero_of_the_village`
- `minecraft:darkness`

가능한 값은 서버 버전의 Bukkit/Paper mob effect 레지스트리를 따릅니다.

## book

`WRITTEN_BOOK`에 제목, 작성자, 페이지를 설정합니다.

```yml
material: "WRITTEN_BOOK"
book:
  title: "후원 기록"
  author: "McStreamApi"
  pages:
    - "{donator}님이 {amount}원을 후원했습니다."
    - "두 번째 페이지"
```

필드:

- `title`: 책 제목
- `author`: 작성자
- `pages`: 페이지 문자열 목록

호환 별칭:

```yml
title: "후원 기록"
author: "McStreamApi"
pages:
  - "본문"
```

또는:

```yml
bookTitle: "후원 기록"
bookAuthor: "McStreamApi"
pages:
  - "본문"
```

## attributes

아이템에 attribute modifier를 붙입니다.

```yml
attributes:
  - attribute: "minecraft:generic.attack_damage"
    amount: 2.0
    operation: "ADD_NUMBER"
    slot: "mainhand"
```

필드:

- `attribute`: attribute ID. `type`도 별칭으로 사용 가능
- `amount`: 수치. 필수
- `operation`: 계산 방식. 생략 시 `ADD_NUMBER`
- `slot`: 적용 장비 슬롯 그룹. 생략 시 `any`
- `name`: 현재 구현에서는 읽지만 modifier key 생성에는 사용하지 않습니다.

지원 operation:

- `ADD_NUMBER`: 기존 값에 숫자를 더함
- `ADD_SCALAR`: 기존 값에 비율을 더함
- `MULTIPLY_SCALAR_1`: 최종 값에 비율을 곱함

지원 slot:

- `any`
- `mainhand`
- `offhand`
- `hand`
- `feet`
- `legs`
- `chest`
- `head`
- `armor`
- `body`
- `saddle`

대표 attribute:

- `minecraft:generic.max_health`
- `minecraft:generic.follow_range`
- `minecraft:generic.knockback_resistance`
- `minecraft:generic.movement_speed`
- `minecraft:generic.flying_speed`
- `minecraft:generic.attack_damage`
- `minecraft:generic.attack_knockback`
- `minecraft:generic.attack_speed`
- `minecraft:generic.armor`
- `minecraft:generic.armor_toughness`
- `minecraft:generic.luck`
- `minecraft:generic.jump_strength`
- `minecraft:generic.scale`
- `minecraft:generic.step_height`
- `minecraft:generic.safe_fall_distance`
- `minecraft:generic.fall_damage_multiplier`
- `minecraft:generic.gravity`
- `minecraft:generic.block_interaction_range`
- `minecraft:generic.entity_interaction_range`

가능한 값은 서버 버전의 Bukkit/Paper attribute 레지스트리를 따릅니다.

## 전체 예시

```yml
items:
  donor_sword:
    material: "DIAMOND_SWORD"
    amount: 1
    name: "&b{donator}의 후원 검"
    lore:
      - "&7금액: {amount}"
      - "&7보상 ID: donor_sword"
    customModelData: 2001
    unbreakable: true
    glow: true
    itemFlags:
      - "HIDE_ENCHANTS"
      - "HIDE_ATTRIBUTES"
      - "HIDE_UNBREAKABLE"
    enchantments:
      minecraft:sharpness: 5
      minecraft:unbreaking: 3
    persistentData:
      - key: "mcstreamapi:item_id"
        type: "string"
        value: "donor_sword"
      - key: "mcstreamapi:amount"
        type: "string"
        value: "{amount}"
    attributes:
      - attribute: "minecraft:generic.attack_damage"
        amount: 3.0
        operation: "ADD_NUMBER"
        slot: "mainhand"

  donor_head:
    material: "PLAYER_HEAD"
    name: "&e{donator}님의 머리"
    playerHead: "{donator}"

  speed_potion:
    material: "POTION"
    name: "&b후원 속도 포션"
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
```
