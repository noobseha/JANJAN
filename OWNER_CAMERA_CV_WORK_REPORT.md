# 사업자 테이블 카메라 매핑 및 YOLO CV 처리 작업 보고서

작성일: 2026-05-24

## 1. 작업 목표

요구사항은 다음 흐름을 앱과 Firebase 백엔드에 반영하는 것이었다.

```text
사업자 메인 화면
 -> 테이블 선택
 -> 해당 테이블을 담당할 스마트폰 카메라 IP 매핑
 -> 매핑 시 카메라 활성 요청
 -> YOLO 결과 좌표를 기준으로 색상 화면/술잔/술병 거리 측정
 -> 색상 화면과 술잔 매핑
 -> 매핑된 술잔과 술병이 3초 이상 가까우면 drinkCount +1
```

## 2. 프론트 작업

### 2.1 사업자 화면 추가

추가 파일:

```text
app/src/main/java/com/gachon/janjan/domain/owner/ui/BusinessOwnerFragment.kt
app/src/main/java/com/gachon/janjan/domain/owner/ui/BusinessOwnerScreens.kt
app/src/main/java/com/gachon/janjan/domain/owner/viewmodel/BusinessOwnerViewModel.kt
app/src/main/java/com/gachon/janjan/domain/owner/repository/BusinessCameraRepository.kt
app/src/main/java/com/gachon/janjan/domain/owner/model/BusinessTable.kt
app/src/main/java/com/gachon/janjan/domain/owner/model/TableCameraMapping.kt
```

구현 내용:

- 사용자 홈 화면에 `사업자 테이블 관리` 진입 버튼을 추가했다.
- 사업자 화면에서 매장 ID와 매장명을 입력하고 테이블 목록을 불러올 수 있게 했다.
- Firestore에 테이블 문서가 없으면 기본 1번부터 8번 테이블을 보여준다.
- 테이블 카드를 누르면 카메라 이름, 카메라 IP, 스트림 URL을 입력하는 다이얼로그가 열린다.
- 저장 시 테이블에 활성 세션이 없으면 새 `sessions` 문서를 자동 생성한다.
- 저장 시 카메라 활성 요청 상태를 Firestore에 기록한다.

### 2.2 네비게이션 연결

수정 파일:

```text
app/src/main/res/navigation/nav_graph.xml
app/src/main/java/com/gachon/janjan/domain/session/ui/SessionHomeFragment.kt
app/src/main/java/com/gachon/janjan/domain/session/ui/SessionScreens.kt
```

추가된 화면 ID:

```text
businessOwnerFragment
```

## 3. Firestore 저장 구조

사업자 화면에서 테이블-카메라 매핑을 저장하면 아래 문서들이 생성 또는 갱신된다.

```text
stores/{storeId}
stores/{storeId}/tables/{tableId}
stores/{storeId}/tableCameraMappings/{tableId}
sessions/{sessionId}
sessions/{sessionId}/cameraMappings/{tableId}
cameraDevices/{cameraDeviceId}
```

예시:

```text
stores/1/tableCameraMappings/table_1
  tableId: "table_1"
  sessionId: "..."
  cameraDeviceId: "camera_192_168_0_10_8080"
  cameraIp: "192.168.0.10:8080"
  cameraStreamUrl: "http://192.168.0.10:8080/video"
  cameraStatus: "activationRequested"
  cameraEnabled: true
```

`cameraDevices/{cameraDeviceId}`에는 다음 명령 필드가 들어간다.

```text
command: "startCamera"
```

카메라 스마트폰 또는 YOLO 실행 서비스는 이 문서를 감시해서 실제 카메라와 모델 추론을 시작하면 된다.

## 4. YOLO 결과 입력 구조

YOLO 모델 결과는 아래 경로로 프레임 단위 저장한다.

```text
sessions/{sessionId}/cvFrames/{frameId}
```

예시:

```text
cameraId: "camera_192_168_0_10_8080"
storeId: "1"
tableId: "table_1"
frameWidth: 1280
frameHeight: 720
capturedAt: Timestamp
detections: [
  {
    trackId: "phone_a",
    objectType: "phone_screen",
    centerX: 310,
    centerY: 330,
    confidence: 0.96,
    screenColorHex: "#22c55e"
  },
  {
    trackId: "glass_a",
    objectType: "soju_glass",
    centerX: 360,
    centerY: 340,
    confidence: 0.91
  },
  {
    trackId: "bottle_a",
    objectType: "green_soju_bottle",
    centerX: 382,
    centerY: 348,
    confidence: 0.9
  }
]
```

지원하는 객체 타입:

```text
색상 화면:
phone_screen, smartphone_screen, color_screen, screen_color, user_color_screen

소주잔:
soju_glass, shot_glass, green_soju_glass

맥주잔:
beer_glass, beer_cup

소주병:
soju_bottle, green_soju_bottle, green_bottle

맥주병:
beer_bottle, brown_beer_bottle, clear_beer_bottle, transparent_beer_bottle
```

## 5. Cloud Functions 처리 로직

수정 파일:

```text
functions/src/index.ts
```

추가 트리거:

```text
sessions/{sessionId}/cvFrames/{frameId} onCreate
```

처리 순서:

1. YOLO detection 배열에서 색상 화면, 소주잔, 맥주잔, 소주병, 맥주병을 분리한다.
2. `frameWidth`, `frameHeight`가 있으면 좌표를 비율 좌표로 정규화한다.
3. 색상 화면과 술잔 사이 거리가 기준 이하이면 `cvPairStates`에 추적 상태를 저장한다.
4. 색상 화면과 술잔이 5초 이상 가까우면 해당 색상을 가진 참가자에게 실제 `physicalGlassId`를 매핑한다.
5. 매핑된 술잔과 같은 주종의 술병이 3초 이상 가까우면 해당 주종의 카운트만 증가시키고 `drinkRecords`를 생성한다.
6. 같은 접촉이 계속 유지되는 동안은 중복 카운트하지 않는다.
7. 객체가 떨어졌다가 다시 가까워지면 새 접촉으로 보고 다시 3초를 측정한다.

추가 상태 경로:

```text
sessions/{sessionId}/cvPairStates/{pairId}
```

### 5.1 소주/맥주 분리 기록

카운트는 다음 네 위치에 모두 주종별로 분리 기록된다.

```text
sessions/{sessionId}/glassMappings/{mappingId}
  drinkType: "soju" 또는 "beer"
  drinkCount: 해당 mapping의 총 잔 수
  sojuDrinkCount: 소주 mapping에서만 증가
  beerDrinkCount: 맥주 mapping에서만 증가

sessions/{sessionId}/drinkCounters/{userId}
  sojuCount: 사용자 소주잔 수
  beerCount: 사용자 맥주잔 수
  totalCount: 사용자 전체 잔 수

sessions/{sessionId}/drinkRecords/{recordId}
  drinkType: "soju" 또는 "beer"
  sojuCountDelta: 소주 이벤트면 1, 아니면 0
  beerCountDelta: 맥주 이벤트면 1, 아니면 0

sessions/{sessionId}
  totalSojuDrinkCount: 세션 전체 소주잔 수
  totalBeerDrinkCount: 세션 전체 맥주잔 수
```

YOLO 프레임 처리에서는 `soju_glass` 계열은 소주병 계열과만, `beer_glass` 계열은 맥주병 계열과만 카운트된다. 지정된 `drinkType`의 `glassMappings`가 없으면 다른 주종 mapping으로 대체하지 않고 카운트를 중단한다.

## 6. 시간 기준

```text
색상 화면 + 술잔 매핑: 5초 이상
술잔 + 술병 음주 카운트: 3초 이상
프레임 간 추적 허용 간격: 1.5초
```

기존 수동 `detectionEvents` 방식도 유지된다.

```text
colorGlassProximity: 5초
pour: 3초
```

수동 `detectionEvents` 방식과 YOLO `cvFrames` 방식 모두 병-잔 기준을 3초로 적용했다.

## 7. Firestore Rules

수정 파일:

```text
firestore.rules
```

추가 허용 경로:

```text
sessions/{sessionId}/cameraMappings/{mappingId}
sessions/{sessionId}/cvFrames/{frameId}
sessions/{sessionId}/cvPairStates/{stateId}
sessions/{sessionId}/drinkCounters/{counterId}
stores/{storeId}/tables/{tableId}
stores/{storeId}/tableCameraMappings/{mappingId}
cameraDevices/{deviceId}
```

앱 또는 YOLO 서비스는 `cvFrames`를 생성할 수 있고, `cvPairStates`와 `drinkRecords`는 Cloud Functions가 Admin SDK로만 갱신한다.

## 8. Firestore Indexes

수정 파일:

```text
firestore.indexes.json
```

추가 인덱스:

- `sessions`: `inviteCode + status`
- `sessions`: `storeId + tableId + status`
- `glassMappings`: `userId + drinkType`
- `glassMappings`: `glassId + drinkType`
- `cvPairStates`: `cameraId + pairType + isActive`

## 9. 테스트 문서

수정 파일:

```text
FIRESTORE_TEST_DOCUMENT_EXAMPLES.md
firestore_test_seed_examples.json
```

추가 내용:

- 사업자 테이블-카메라 매핑 문서 예시
- `cameraDevices` 활성 요청 예시
- YOLO `cvFrames` 입력 예시
- 지원 객체 타입 목록
- 5초 매핑 / 3초 카운트 기준

## 10. 검증 결과

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

- Android Kotlin 컴파일 성공
- Android assembleDebug 성공
- debug unit test 성공
- lintDebug 성공
- diff whitespace check 성공
- Firestore index JSON 파싱 성공
- 테스트 seed JSON 파싱 성공
- Functions package/tsconfig JSON 파싱 성공

현재 로컬 환경에는 `npm`, `npx`, `tsc`, `firebase` CLI가 없어 Cloud Functions TypeScript 컴파일과 배포는 실행하지 못했다. Firebase CLI 환경에서는 아래 명령으로 추가 확인하면 된다.

```bash
cd functions
npm install
npm run build
firebase deploy --only functions
```

## 11. 남은 실제 연동 조건

현재 앱과 Firebase에는 매핑 UI, 활성 요청, YOLO 결과 처리 로직이 들어갔다.

실제 카메라가 켜지고 YOLO가 계속 동작하려면 아래 실행 주체가 필요하다.

```text
카메라 스마트폰 또는 별도 YOLO 실행 서비스
 -> cameraDevices/{deviceId}.command == "startCamera" 감시
 -> cameraStreamUrl 또는 로컬 카메라 실행
 -> YOLO inference 수행
 -> sessions/{sessionId}/cvFrames/{frameId}에 detection 배열 업로드
```

즉, 이번 작업은 앱/Firebase 쪽 연결 구조와 판단 로직을 구현한 것이고, 실제 YOLO 모델 실행 프로세스는 학습팀의 모델 러너에서 이 스키마에 맞춰 붙이면 된다.
