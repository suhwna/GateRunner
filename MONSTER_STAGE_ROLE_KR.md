# 몬스터/역할군 스테이지 정리 (한글)

이 문서는 현재 코드 기준으로, 스테이지별로 어떤 역할 몬스터가 나오고 어떤 규칙으로 스폰되는지 정리한 문서다.

## 1. 역할군 의미
- `RUSHER`: 기본 근접 돌진형
- `SKIRMISHER`: 회피/지그재그 성향 근접형
- `BRUISER`: 체력 높은 중장 근접형
- `THROWER_SPEAR`: 창 투척 원거리형
- `THROWER_AXE`: 도끼 투척 원거리형
- `THROWER_ARROW`: 화살 투척 원거리형
- `CASTER`: 마법 원거리형 (`MAGIC_BALL`)
- `ELITE_MINI`: 엘리트 몬스터(강화 패턴/고체력)

## 2. 스테이지별 근접 역할 확률
기준 함수: `MonsterBalance.pickRoleForStage(stage, rng)`

- 스테이지 1(숲, `stage=0`)
  - `RUSHER` 20%
  - `SKIRMISHER` 20%
  - `BRUISER` 60%
- 스테이지 2(늪, `stage=1`)
  - `RUSHER` 20%
  - `SKIRMISHER` 20%
  - `BRUISER` 60%
- 스테이지 3(화산, `stage=2`)
  - `RUSHER` 20%
  - `SKIRMISHER` 20%
  - `BRUISER` 60%

## 3. 스테이지별 원거리 역할 확률
기준 함수: `MonsterBalance.pickRangedRoleForStage(stage, rng)`

- 스테이지 1(숲)
  - `THROWER_AXE` 40%
  - `THROWER_ARROW` 40%
  - `CASTER` 20%
- 스테이지 2(늪)
  - `THROWER_ARROW` 55%
  - `CASTER` 45%
- 스테이지 3(화산)
  - `THROWER_SPEAR` 20%
  - `CASTER` 80%

## 4. 난이도에 따른 추가 규칙
기준 함수: `spawnNormalSegmentContent(...)` (`MainActivity.kt`)

- 난이도 `6+`: 각 스폰 시도에서 `12%` 확률로 `ELITE_MINI` 강제 치환
- 난이도 `9+`: `ELITE_MINI`를 원거리 취급 가능 (투사체는 `MAGIC_BALL` 고정)
- 스테이지별 최소/최대 원거리 몹 수 제한
  - 최소: `stage0=2`, `stage1=4`, `stage2=6` + 난이도 보정
  - 최대: `stage0=3`, `stage1=6`, `stage2=9` + 난이도 보정

## 5. 역할별 투사체 종류
기준 함수: `MonsterBalance.shotKindForRole(role)`

- `THROWER_AXE` -> `AXE`
- `CASTER` -> `MAGIC_BALL`
- `THROWER_SPEAR` -> `SPEAR`
- `THROWER_ARROW` -> `ARROW`

## 6. 역할별 기본 HP 공식
기준 함수: `MonsterBalance.hpForRole(role, stage, hpMult)`

- `RUSHER`: `34 + stage*20`
- `SKIRMISHER`: `30 + stage*18`
- `BRUISER`: `62 + stage*32`
- `THROWER_SPEAR`: `((42 + stage*24) * 0.85)`
- `THROWER_AXE`: `((46 + stage*26) * 0.9)`
- `THROWER_ARROW`: `((40 + stage*22) * 0.8)`
- `CASTER`: `((40 + stage*22) * 0.8)`
- `ELITE_MINI`: `(86 + stage*42) * 2`
- 최종 HP: 위 값에 `hpMult`(루프 배수) 적용

## 7. 스테이지별 스프라이트(라벨) 매핑 위치
기준 함수: `MonsterBalance.pickMonsterRawSpriteIndex(...)`

- 스테이지 1 라벨 풀
  - `RUSHER`: `A4`
  - `SKIRMISHER`: `A3`
  - `BRUISER`: `B1`
  - `THROWER_AXE`: `A5`
  - `THROWER_ARROW`: `A6`
  - `CASTER`: `A2`
  - `ELITE_MINI(근접)`: `B2`
  - `ELITE_MINI(원거리)`: `A7`
- 스테이지 2 라벨 풀
  - `RUSHER` : `E1`
  - `SKIRMISHER`: `E6`
  - `BRUISER`: `F3`
  - `THROWER_ARROW`: `E2`
  - `CASTER`: `E3`
  - `ELITE_MINI(근접)`: `E4`
  - `ELITE_MINI(원거리)`: `F4`
- 스테이지 3 라벨 풀
  - `RUSHER` : `I1`
  - `SKIRMISHER`: `I4`
  - `BRUISER`: `I3`
  - `THROWER_SPEAR`: `J1`
  - `CASTER`: `K2`
  - `ELITE_MINI(근접)`: `L2`
  - `ELITE_MINI(원거리)`: `M3`

## 8. 보스 라벨 고정값
기준 함수: `MonsterBalance.bossLabelForStage(stage)`

- 숲 보스: `B3`
- 늪 보스: `D2`
- 화산 보스: `L1`

## 9. 핵심 파일 위치
- 역할/HP/라벨 규칙: `app/src/main/java/com/suhwan/gaterunner/MonsterSystem.kt`
- 실제 스폰/난이도 적용: `app/src/main/java/com/suhwan/gaterunner/MainActivity.kt`
