# 소주/맥주 카운트 분리 기록 수정 보고서

작성일: 2026-05-24

## 1. 수정 목표

매핑된 술잔과 같은 주종 병이 3초 이상 가까이 붙어 있을 때 `drinkCount +1`이 발생하되, 마신 소주잔 카운트와 마신 맥주잔 카운트가 섞이지 않고 분리 기록되도록 수정했다.

## 2. 발견한 문제 가능성

화면과 통계 조회는 이미 `glassMappings.drinkType`을 기준으로 소주와 맥주를 나눠 합산하고 있었다.

다만 Cloud Function의 카운트 함수에서 `expectedDrinkType`이 있어도 해당 주종 mapping이 없으면 같은 `glassId`의 다른 mapping으로 fallback할 수 있는 구조가 있었다.

예를 들면 맥주병 이벤트인데 `beer` mapping이 없고 같은 glassId의 `soju` mapping만 있으면 잘못된 mapping에 카운트가 들어갈 가능성이 있었다.

## 3. 백엔드 수정

수정 파일:

```text
functions/src/index.ts
```

변경 내용:

- `expectedDrinkType`이 있는 경우 반드시 `glassId + drinkType`으로 mapping을 조회한다.
- 해당 주종 mapping이 없으면 다른 주종 mapping으로 대체하지 않고 카운트를 중단한다.
- mapping 문서에 `sojuDrinkCount` 또는 `beerDrinkCount`를 추가로 증가시킨다.
- `sessions/{sessionId}/drinkCounters/{userId}` 문서를 추가로 기록한다.
- `drinkRecords`에 `sojuCountDelta`, `beerCountDelta`를 기록한다.
- `sessions/{sessionId}`에 `totalSojuDrinkCount`, `totalBeerDrinkCount`를 분리 증가시킨다.
- participant 문서에도 `sojuDrinkCount`, `beerDrinkCount`, `lastDrinkType`, `lastDrinkAt`을 기록한다.

## 4. 분리 저장 구조

카운트 발생 시 아래 네 위치가 함께 갱신된다.

```text
sessions/{sessionId}/glassMappings/{mappingId}
  drinkType: "soju" 또는 "beer"
  drinkCount: 해당 mapping의 총 잔 수
  sojuDrinkCount: 소주 mapping에서 증가
  beerDrinkCount: 맥주 mapping에서 증가

sessions/{sessionId}/drinkCounters/{userId}
  sojuCount: 사용자 소주잔 수
  beerCount: 사용자 맥주잔 수
  totalCount: 사용자 총 잔 수

sessions/{sessionId}/drinkRecords/{recordId}
  drinkType: "soju" 또는 "beer"
  sojuCountDelta: 소주 이벤트면 1
  beerCountDelta: 맥주 이벤트면 1

sessions/{sessionId}
  totalSojuDrinkCount: 세션 전체 소주잔 수
  totalBeerDrinkCount: 세션 전체 맥주잔 수
```

## 5. Android 모델 보강

수정/추가 파일:

```text
app/src/main/java/com/gachon/janjan/domain/session/model/GlassUserMapping.kt
app/src/main/java/com/gachon/janjan/domain/session/model/DrinkRecord.kt
app/src/main/java/com/gachon/janjan/domain/session/model/SessionParticipant.kt
app/src/main/java/com/gachon/janjan/domain/session/model/DrinkCounter.kt
app/src/main/java/com/gachon/janjan/data/model/Session.kt
```

추가 필드:

- `GlassUserMapping.sojuDrinkCount`
- `GlassUserMapping.beerDrinkCount`
- `DrinkRecord.glassId`
- `DrinkRecord.detectionEventId`
- `DrinkRecord.durationMs`
- `DrinkRecord.sojuCountDelta`
- `DrinkRecord.beerCountDelta`
- `SessionParticipant.sojuDrinkCount`
- `SessionParticipant.beerDrinkCount`
- `Session.totalSojuDrinkCount`
- `Session.totalBeerDrinkCount`
- `DrinkCounter` 모델

## 6. Firestore Rules / Indexes

수정 파일:

```text
firestore.rules
firestore.indexes.json
```

변경 내용:

- `sessions/{sessionId}/drinkCounters/{counterId}` 읽기 허용
- `drinkCounters`는 앱에서 직접 create/update/delete 불가
- `drinkCounters` collectionGroup 인덱스 추가

## 7. 문서 갱신

수정 파일:

```text
FIRESTORE_TEST_DOCUMENT_EXAMPLES.md
firestore_test_seed_examples.json
OWNER_CAMERA_CV_WORK_REPORT.md
```

문서에 소주/맥주 분리 기록 위치와 예시를 추가했다.

## 8. 검증

실행한 검증:

```bash
sh gradlew :app:compileDebugKotlin
sh gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
git diff --check
python3 -m json.tool firestore.indexes.json
python3 -m json.tool firestore_test_seed_examples.json
node -e "JSON.parse(require('fs').readFileSync('functions/package.json','utf8')); JSON.parse(require('fs').readFileSync('functions/tsconfig.json','utf8'))"
```

결과:

- Kotlin 컴파일 성공
- Android assembleDebug 성공
- debug unit test 성공
- lintDebug 성공
- diff whitespace check 성공
- Firestore index JSON 파싱 성공
- 테스트 seed JSON 파싱 성공
- Functions package/tsconfig JSON 파싱 성공

현재 로컬에는 `npm`, `tsc`, `firebase` CLI가 없어 Functions TypeScript 컴파일과 배포는 실행하지 못했다.
