# random.yml

`random.yml`은 Action 문자열에서 사용할 랜덤 치환값을 정의합니다.

## 기본 구조

```yml
monster_random:
  - value: "zombie"
    chance: 50
    amount: 1
    display: "좀비"
  - value: "skeleton"
    chance: 30
    amount: 2
    display: "스켈레톤"
  - value: "creeper"
    chance: 20
    amount: "<1..3>"
    display: "크리퍼"
```

사용:

```yml
- type: "cmd"
  command: "summon {random.monster_random} {player}"
```

## 단순 목록

문자열 목록으로 적으면 각 항목의 chance는 `100`입니다.

```yml
animal_random:
  - "cow"
  - "pig"
  - "chicken"
```

## 가중치 목록

`value`와 `chance`를 함께 적으면 chance 가중치로 선택됩니다.

```yml
item_random:
  - value: "diamond"
    chance: 10
  - value: "emerald"
    chance: 30
  - value: "gold_ingot"
    chance: 60
```

chance 합계가 100이 아니어도 됩니다. 내부에서는 전체 chance 합계를 기준으로 가중 선택합니다.

## amount

`amount`는 랜덤 항목이 `summon` 액션의 `entity`에서 선택될 때만 소환 수량으로 사용됩니다.

```yml
monster_random:
  - value: "zombie"
    chance: 50
    amount: 1
    display: "좀비"
  - value: "skeleton"
    chance: 30
    amount: 2
    display: "스켈레톤"
  - value: "creeper"
    chance: 20
    amount: "<1..3>"
    display: "크리퍼"
```

```yml
- type: "broadcast"
  message: "{random.monster_random.display} {random.monster_random.amount}마리 등장"
- type: "summon"
  entity: "{random.monster_random}"
```

랜덤 항목에 `amount`가 없으면 `1`로 처리합니다. `amount: "<1..3>"`처럼 범위를 쓰면 같은 이벤트 안에서 한 번만 수량을 뽑아 `{random.key.amount}`와 실제 `summon` 수량이 같게 유지됩니다. `amount`는 `item_random`처럼 아이템 이름을 뽑아 `give`에 사용하는 경우에는 적용되지 않습니다.

## display

`display`는 메시지에 보여줄 이름입니다. 값이 없으면 `{random.key.display}`는 실제 `value`를 표시합니다.

```yml
monster_random:
  - value: "zombie"
    display: "좀비"
  - value: "creeper"
    display: "크리퍼"
```

```yml
- type: "broadcast"
  message: "{random.monster_random.display} 등장"
```

랜덤으로 커스텀 아이템을 고르는 경우에는 `custom-item.yml`의 값을 기준으로 `{random.key.item.name}`과 `{random.key.item.amount}`를 사용할 수 있습니다. 자세한 예시는 [custom-item-yml.md](custom-item-yml.md)를 참고하세요.

## 기본값과 제외

- `chance`를 생략하면 `100`
- `amount`를 생략하면 summon에서 `1`
- `chance <= 0`이면 후보에서 제외
- 없는 key는 치환하지 않고 원문 placeholder를 유지

## random 과 random_once

`{random.key}`는 같은 후원 이벤트 안에서 같은 값을 유지합니다.

```yml
- type: "broadcast"
  message: "{random.monster_random} 등장"
- type: "cmd"
  command: "summon {random.monster_random} {player}"
```

위 예시에서 두 placeholder는 같은 이벤트 안에서는 같은 몬스터로 치환됩니다.

`{random_once.key}`는 호출마다 새로 뽑습니다.

```yml
- type: "broadcast"
  message: "{random_once.item_random}, {random_once.item_random}, {random_once.item_random}"
```

같은 이벤트 안에서도 각 placeholder가 별도로 선택됩니다.
