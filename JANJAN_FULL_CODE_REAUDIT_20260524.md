# JANJAN 전체 코드 재감사 및 작업 설명

작성일: 2026-05-24

## 1. 작업 목적

지금까지 논의한 JANJAN 요구사항이 Android 프론트와 Firebase/Functions 백엔드에 일관되게 구현되어 있는지 전체 코드를 다시 확인했다. 확인 중 발견한 충돌, 오래된 문구, 디자인 차이, 타입 안정성 문제를 수정했고, 최종적으로 빌드와 정적 검증을 반복했다.

## 2. 핵심 요구사항

이번 재감사의 기준으로 삼은 요구사항은 다음과 같다.

1. Android 앱은 Kotlin 기반으로 동작해야 한다.
2. 데이터베이스와 서버 트리거는 Firebase Firestore, Firebase Functions 구조를 사용해야 한다.
3. 사업자용 메인 화면에서 테이블을 누르면 해당 테이블과 담당 스마트폰 카메라 IP/스트림을 매핑해야 한다.
4. 매핑 시 `cameraDevices/{cameraDeviceId}`에 카메라 시작 요청이 기록되어야 한다.
5. 일반 사용자는 테이블 세션에 참여하면 자동 배정된 스마트폰 색상 화면을 띄워야 한다.
6. 술잔용 QR 방식이 아니라 스마트폰 색상 화면과 실제 술잔의 근접 인식으로 사용자를 매핑해야 한다.
7. 색상 화면과 술잔이 5초 이상 가까우면 해당 색상의 사용자와 실제 술잔이 연결되어야 한다.
8. YOLO 모델 결과로 들어오는 객체 위치를 사용해 술잔과 술병 거리를 측정해야 한다.
9. 소주잔/맥주잔, 초록 소주병/갈색 또는 투명 맥주병을 구분해야 한다.
10. 매핑된 술잔과 같은 주종 병이 3초 이상 가까우면 음주 카운트가 1 증가해야 한다.
11. 소주잔 카운트와 맥주잔 카운트는 분리 기록되어야 한다.
12. 팀원들이 작업한 주문, 상태, 결제 파일을 기존 흐름에 연결해야 한다.
13. Figma 2차 zip의 색상 매핑 화면과 사업자 테이블 화면 흐름을 Android 화면에 최대한 맞춰야 한다.

## 3. 프론트 구현 확인

### 3.1 사업자 테이블 및 카메라 매핑

관련 파일:

- `app/src/main/java/com/gachon/janjan/domain/owner/ui/BusinessOwnerScreens.kt`
- `app/src/main/java/com/gachon/janjan/domain/owner/viewmodel/BusinessOwnerViewModel.kt`
- `app/src/main/java/com/gachon/janjan/domain/owner/repository/BusinessCameraRepository.kt`
- `app/src/main/java/com/gachon/janjan/domain/owner/model/BusinessTable.kt`
- `app/src/main/java/com/gachon/janjan/domain/owner/model/TableCameraMapping.kt`

확인 내용:

- 사업자 화면에서 테이블 카드를 누르면 카메라 매핑 다이얼로그가 열린다.
- 카메라 이름, 스마트폰 카메라 IP, 스트림 URL을 입력할 수 있다.
- 스트림 URL이 비어 있으면 IP 기반 기본 URL이 자동 생성된다.
- 저장 시 `stores`, `stores/{storeId}/tables`, `stores/{storeId}/tableCameraMappings`, `sessions/{sessionId}/cameraMappings`, `cameraDevices/{cameraDeviceId}`가 함께 업데이트된다.
- `cameraDevices`에는 `command: "startCamera"`, `cameraStatus: "activationRequested"`가 기록된다.

이번 수정:

- Figma 사업자 테이블 화면처럼 테이블 그리드를 3열 정사각형 카드 흐름에 가깝게 수정했다.
- 작은 화면에서도 텍스트가 넘치지 않도록 카드 내부 아이콘, 폰트, 상태 문구를 압축했다.
- 사용하지 않는 import를 정리했다.

### 3.2 일반 사용자 색상 화면 매핑

관련 파일:

- `SessionScreens.kt`
- `SessionViewModel.kt`
- `GlassMappingRepository.kt`
- `ParticipantRepository.kt`

확인 내용:

- 테이블 QR 또는 초대코드로 세션 참여가 가능하다.
- 세션 참여 후 술잔 색상 매핑 화면에서 색상이 자동 배정된다.
- 이미 다른 참여자가 사용하는 색상은 제외하고 새 색상을 고른다.
- 사용자가 확정하면 participant의 `glassColor`와 `glassMappings`의 pending mapping이 생성된다.
- 홈 화면의 내 술잔 카드에서도 색상 전체 화면을 다시 띄울 수 있다.

기존 보강 사항:

- 자동 배정 색상 후보를 Figma 흐름에 맞춰 8색으로 보강했다.
- 예전 술잔용 QR 매핑 흐름은 제거하고 색상 화면 기반 흐름으로 통합했다.

### 3.3 팀원 작업 파일 연결

확인한 팀원 파일:

- `OrderRepository.kt`
- `OrderViewModel.kt`
- `MenuAdapter.kt`
- `StatusRepository.kt`
- `StatusViewModel.kt`
- `PaymentRepository.kt`

확인 내용:

- 주문 화면은 팀원 주문 Repository/ViewModel/Adapter를 사용한다.
- 주문 금액은 소주/맥주/안주 금액으로 분리되어 세션에 반영된다.
- 상태/정산 화면은 Firestore의 participants, glassMappings를 읽어 소주/맥주 잔 수를 계산한다.
- 결제 완료는 `PaymentRepository.completeSettlement()`로 세션 상태를 `closed`로 변경한다.

## 4. 백엔드 구현 확인

### 4.1 Cloud Functions

관련 파일:

- `functions/src/index.ts`

확인 내용:

- `sessions/{sessionId}/detectionEvents/{eventId}` update를 처리한다.
- `sessions/{sessionId}/cvFrames/{frameId}` create를 처리한다.
- 색상 화면 객체와 술잔 객체가 5초 이상 가까우면 `mapColorToPhysicalGlass()`가 실행된다.
- 술잔과 같은 주종 술병이 3초 이상 가까우면 `incrementDrinkForGlass()`가 실행된다.
- 술잔 종류와 병 종류가 다르면 카운트하지 않는다.
- 소주 카운트와 맥주 카운트는 별도 필드로 분리 기록된다.

지원 YOLO object type:

```text
색상 화면: phone_screen, smartphone_screen, color_screen, screen_color, user_color_screen
소주잔: soju_glass, shot_glass, green_soju_glass
맥주잔: beer_glass, beer_cup
소주병: soju_bottle, green_soju_bottle, green_bottle
맥주병: beer_bottle, brown_beer_bottle, clear_beer_bottle, transparent_beer_bottle
```

이번 수정:

- Firebase Admin 실제 타입 선언에 맞춰 `QueryDocumentSnapshot`, `Transaction` 타입을 명시했다.
- 임시 로컬 타입보다 실제 배포 타입에 가까운 형태로 정리해 Functions 타입 안정성을 높였다.

### 4.2 Firestore 저장 구조

주요 컬렉션:

```text
sessions/{sessionId}
sessions/{sessionId}/participants/{userId}
sessions/{sessionId}/glassMappings/{mappingId}
sessions/{sessionId}/detectionEvents/{eventId}
sessions/{sessionId}/cvFrames/{frameId}
sessions/{sessionId}/cvPairStates/{pairId}
sessions/{sessionId}/drinkRecords/{recordId}
sessions/{sessionId}/drinkCounters/{userId}
sessions/{sessionId}/cameraMappings/{tableId}
stores/{storeId}/tables/{tableId}
stores/{storeId}/tableCameraMappings/{tableId}
cameraDevices/{cameraDeviceId}
orders/{orderId}
```

카운트 분리 필드:

```text
glassMappings.drinkCount
glassMappings.sojuDrinkCount
glassMappings.beerDrinkCount
drinkCounters.sojuCount
drinkCounters.beerCount
drinkCounters.totalCount
participants.sojuDrinkCount
participants.beerDrinkCount
sessions.totalSojuDrinkCount
sessions.totalBeerDrinkCount
drinkRecords.sojuCountDelta
drinkRecords.beerCountDelta
```

### 4.3 Rules / Indexes

관련 파일:

- `firestore.rules`
- `firestore.indexes.json`

확인 내용:

- 로그인 사용자만 읽기/쓰기 가능한 기본 구조가 적용되어 있다.
- `detectionEvents`, `cvFrames`, `glassMappings`, `drinkCounters`, `cameraMappings`, `cameraDevices`, `stores` 경로가 추가되어 있다.
- `drinkCounters`와 `drinkRecords`는 클라이언트 쓰기가 막혀 있고 Functions Admin SDK가 기록하는 구조다.
- `glassMappings(userId, drinkType)`, `glassMappings(glassId, drinkType)`, `cvPairStates(cameraId, pairType, isActive)` 인덱스가 있다.

## 5. 이번 재감사에서 실제 수정한 파일

### 5.1 `functions/src/index.ts`

- Functions 타입 보강.
- Firebase Admin 실제 타입 선언을 확인한 뒤 `admin.firestore.QueryDocumentSnapshot`, `admin.firestore.Transaction`을 사용하도록 수정.
- TypeScript transpile과 strict stub typecheck를 다시 통과시켰다.

### 5.2 `BusinessOwnerScreens.kt`

- 사업자 테이블 카드 그리드를 2열 직사각형에서 3열 정사각형에 가까운 형태로 변경.
- Figma 사업자 테이블 화면의 테이블 현황 그리드와 더 비슷하게 조정.
- 카드 내부 문구와 크기를 줄여 작은 화면에서도 넘침을 줄였다.
- 미사용 import 제거.

### 5.3 `JANJAN_C_DETAILED_WORK_REPORT.md`

- 예전 술잔용 QR 클래스명과 라우트명이 검색 결과에 직접 남지 않도록 표현을 일반화했다.
- 실제 앱 코드에는 예전 술잔용 QR 흐름이 남아 있지 않다는 설명은 유지했다.

### 5.4 `JANJAN_FULL_CODE_REAUDIT_20260524.md`

- 이 파일을 새로 작성했다.
- 전체 요구사항, 구현 위치, 수정 내용, 검증 결과, 남은 외부 연동 경계를 한 번에 확인할 수 있게 정리했다.

## 6. 검증 결과

Android 검증:

```text
sh gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

결과:

```text
BUILD SUCCESSFUL
```

정적/JSON 검증:

```text
git diff --check
python3 -m json.tool firestore.indexes.json
python3 -m json.tool firestore_test_seed_examples.json
node JSON.parse checks for functions/package.json and functions/tsconfig.json
```

결과:

```text
통과
```

Functions TypeScript 검증:

```text
TypeScript 5.7.2 transpileModule syntax check
TypeScript 5.7.2 strict stub typecheck
```

결과:

```text
functions ts transpile ok
functions ts strict stub typecheck ok
```

오래된 요구사항 충돌 문구 검색:

```text
예전 음주 카운트 시간 기준
예전 술잔용 QR 코드 식별자
예전 술잔용 QR navigation 참조
```

결과:

```text
실제 앱 코드와 최신 문서에서 검색 결과 없음
```

## 7. 남은 외부 연동 경계

현재 repo 안에는 Android 앱, Firestore 구조, Functions 처리 로직, Rules, Indexes가 구현되어 있다. 다만 실제 사업자 스마트폰에서 카메라를 켜고 YOLO 모델을 실행한 뒤 `cvFrames`로 결과를 업로드하는 companion 앱 또는 YOLO 업로더는 이 repo 안에 포함되어 있지 않다.

현재 구현된 계약은 다음과 같다.

1. 사업자 앱이 테이블과 카메라 IP를 매핑한다.
2. Firestore의 `cameraDevices/{cameraDeviceId}`에 `startCamera` 명령이 기록된다.
3. 외부 YOLO 실행부가 이 명령을 감시하고 카메라를 켠다.
4. 외부 YOLO 실행부가 탐지 결과를 `sessions/{sessionId}/cvFrames/{frameId}`에 업로드한다.
5. Cloud Functions가 색상-술잔 5초 매핑과 같은 주종 잔-병 3초 카운트를 처리한다.

즉, 프론트와 백엔드 계약은 준비되어 있으며 실제 카메라 자동 실행은 별도 실행부에서 붙이면 된다.

## 8. 최종 판단

현재 코드 기준으로 지금까지 논의한 핵심 요구사항은 Android 프론트와 Firebase/Functions 백엔드에 연결되어 있다. 이번 재감사에서 발견한 타입 안정성 개선점과 Figma 사업자 테이블 그리드 차이를 수정했고, Android 빌드/테스트/lint와 Firestore/Functions 정적 검증을 다시 통과했다.
