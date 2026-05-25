# JANJAN 요구사항 전체 감사 보고서

작성일: 2026-05-24

## 1. 감사 범위

지금까지 논의한 JANJAN 요구사항을 Android Kotlin 프론트, Firebase Firestore, Cloud Functions, Rules, Indexes 기준으로 다시 대조했다. 특히 아래 흐름을 중심으로 확인했다.

- 사업자 테이블 화면에서 테이블을 눌러 담당 스마트폰 카메라 IP/스트림을 매핑한다.
- 일반 사용자는 테이블 세션에 참여한 뒤 앱이 자동 배정한 색상 화면을 띄운다.
- 오버헤드 카메라/YOLO 결과가 `cvFrames`로 들어오면 색상 화면과 술잔의 근접 상태를 추적한다.
- 색상 화면과 술잔이 5초 이상 가까우면 해당 색상 사용자와 실제 술잔을 매핑한다.
- 매핑된 술잔과 같은 주종 술병이 3초 이상 가까우면 음주 카운트를 증가시킨다.
- 소주잔 카운트와 맥주잔 카운트는 서로 섞이지 않고 분리 기록된다.
- Figma 2차 zip 기준의 주요 색상, 테이블 화면, 색상 매핑 화면 흐름이 Android 화면에 반영되어 있는지 확인한다.

## 2. 요구사항별 확인 결과

| 요구사항 | 현재 상태 | 근거 파일 |
| --- | --- | --- |
| Android Studio 언어 Kotlin | 충족 | `app/src/main/java/.../*.kt`, `app/build.gradle.kts` |
| 데이터베이스 Firebase 사용 | 충족 | `FirebaseConfig.kt`, `firestore.rules`, `firestore.indexes.json`, `functions/src/index.ts` |
| 팀원 작업 파일 사용 | 충족 | `OrderRepository.kt`, `OrderViewModel.kt`, `MenuAdapter.kt`, `StatusRepository.kt`, `PaymentRepository.kt`를 세션/주문/정산 흐름에 연결 |
| 사업자 메인에서 테이블 클릭 | 충족 | `BusinessOwnerScreens.kt`의 `OwnerTableTile` 클릭, `CameraMappingDialog` |
| 테이블과 담당 스마트폰 카메라 IP 매핑 | 충족 | `BusinessCameraRepository.saveCameraMapping()`이 `stores/{storeId}/tableCameraMappings`, `sessions/{sessionId}/cameraMappings`, `cameraDevices/{cameraDeviceId}`를 함께 기록 |
| 매핑 시 카메라 활성 요청 | 충족, 외부 카메라 실행부와 연동 필요 | `cameraDevices/{cameraDeviceId}`에 `command: "startCamera"`, `cameraStatus: "activationRequested"` 기록. 실제 스마트폰 카메라 앱/YOLO 업로더는 이 명령을 구독해 `cvFrames`를 올리는 외부 실행부로 둔다. |
| 테이블 QR 스캔/초대코드 참여 | 충족 | `QrScanFragment.kt`, `QrCameraPreview.kt`, `SessionViewModel.connectByQrPayload()`, `findByInviteCode()` |
| 술잔용 QR 방식 대신 스마트폰 색상 화면 사용 | 충족 | `GlassColorScreen`, `assignGlassColor()`, `createPendingColorMapping()` |
| 색상 자동 배정 | 충족 | `SessionViewModel.GLASS_COLORS`, `pickAvailableGlassColor()` |
| Figma 색상 매핑 화면 반영 | 충족 보강 | Figma zip의 자동 배정 풀스크린 색상 화면 흐름을 Android `GlassColorScreen`에 반영. 색상 후보를 8색 흐름으로 보강했다. |
| 색상 화면 + 술잔 5초 근접 매핑 | 충족 | `COLOR_GLASS_MAPPING_THRESHOLD_MS = 5000`, `handleCvFrame()`, `mapColorToPhysicalGlass()` |
| YOLO 결과 객체 위치 기반 처리 | 충족 | `cvFrames`의 `detections`를 파싱해 `centerX/centerY` 또는 bbox 중심값으로 거리 계산 |
| 소주잔/맥주잔 인식 | 충족 | `YOLO_SOJU_GLASS_TYPES`, `YOLO_BEER_GLASS_TYPES` |
| 초록 소주병/갈색 또는 투명 맥주병 인식 | 충족 | `YOLO_SOJU_BOTTLE_TYPES`, `YOLO_BEER_BOTTLE_TYPES` |
| 같은 주종 잔+병만 카운트 | 충족 | `handleCvFrame()`에서 bottle drinkType과 glass drinkType이 다르면 건너뜀 |
| 잔+병 3초 이상 근접 시 카운트 +1 | 충족 | `POUR_THRESHOLD_MS = 3000`, `CV_POUR_THRESHOLD_MS = 3000`, `incrementDrinkForGlass()` |
| 소주/맥주 카운트 분리 기록 | 충족 | `sojuDrinkCount`, `beerDrinkCount`, `drinkCounters.sojuCount`, `drinkCounters.beerCount`, `totalSojuDrinkCount`, `totalBeerDrinkCount`, `sojuCountDelta`, `beerCountDelta` |
| Firestore Rules 반영 | 충족 | `detectionEvents`, `cvFrames`, `glassMappings`, `drinkCounters`, `cameraMappings`, `cameraDevices`, `stores` 규칙 추가 |
| Firestore Indexes 반영 | 충족 | `glassMappings(userId, drinkType)`, `glassMappings(glassId, drinkType)`, `cvPairStates(cameraId, pairType, isActive)` 등 추가 |

## 3. 이번 감사에서 수정한 사항

1. 수동 `detectionEvents` 방식의 음주 카운트 기준이 이전의 더 짧은 기준으로 남아 있던 문제를 수정했다.
   - `POUR_THRESHOLD_MS`를 `3000`으로 변경했다.
   - YOLO `cvFrames` 방식과 수동 이벤트 방식 모두 3초 기준으로 통일했다.

2. 테스트 예시 문서와 작업 보고서의 오래된 시간 기준 설명을 3초로 수정했다.
   - `FIRESTORE_TEST_DOCUMENT_EXAMPLES.md`
   - `OWNER_CAMERA_CV_WORK_REPORT.md`
   - `JANJAN_C_DETAILED_WORK_REPORT.md`

3. Figma 색상 매핑 화면에 더 맞도록 자동 배정 색상 후보를 8개로 보강했다.
   - 빨강, 파랑, 초록, 노랑, 보라, 핑크, 하늘, 민트 계열을 사용한다.

4. 상세 보고서 안의 예전 술잔 스캔 표현이 정적 검색을 헷갈리게 만들던 부분을 정리했다.

## 4. 검증 결과

실행한 검증:

```text
sh gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

결과: `BUILD SUCCESSFUL`

```text
git diff --check
```

결과: 통과

```text
python3 -m json.tool firestore.indexes.json
python3 -m json.tool firestore_test_seed_examples.json
```

결과: 통과

```text
node -e "JSON.parse(... functions/package.json ...); JSON.parse(... functions/tsconfig.json ...)"
```

결과: 통과

```text
TypeScript 5.7.2 transpileModule syntax check for functions/src/index.ts
```

결과: `functions ts transpile ok`

```text
TypeScript 5.7.2 strict stub typecheck for functions/src/index.ts
```

결과: `functions ts strict stub typecheck ok`

정적 검색으로 아래 범주의 오래된 요구사항 충돌 표현이 실제 코드와 최신 문서에 남지 않는 것을 확인했다.

```text
예전 음주 카운트 시간 기준
예전 술잔용 QR 라우트/문구
예전 술잔용 QR Firestore id prefix
```

## 5. 남은 통합 경계

현재 repo 안에는 Android 앱, Firestore 계약, Cloud Functions 처리 로직, Rules/Indexes가 들어 있다. 다만 실제 사업자용 스마트폰 카메라를 켜고 YOLO 모델을 실행해 `sessions/{sessionId}/cvFrames/{frameId}`로 결과를 업로드하는 별도 카메라 실행부는 이 Android 앱 repo 안에 포함되어 있지 않다.

따라서 현재 구현은 다음 계약을 만족한다.

- 사업자 앱이 `cameraDevices/{cameraDeviceId}`에 카메라 시작 명령과 세션 매핑 정보를 기록한다.
- YOLO 실행부가 해당 명령을 보고 카메라를 켠 뒤 `cvFrames`를 업로드하면 Functions가 색상-잔 매핑과 잔-병 카운트를 처리한다.

실제 카메라 디바이스 자동 실행까지 완전한 E2E로 보려면, YOLO 업로더 또는 카메라 companion 앱에서 `cameraDevices.command == "startCamera"`를 구독하는 부분을 별도 모듈로 붙이면 된다.

## 6. 최종 판단

현재 코드 기준으로 Android 프론트와 Firebase/Functions 백엔드의 핵심 요구사항은 같은 Firestore 구조와 같은 시간 기준을 바라보도록 정리되어 있다. 이번 감사에서 발견된 충돌은 수정했고, Android 빌드/테스트/lint 및 Firestore/Functions 정적 검증을 통과했다.
