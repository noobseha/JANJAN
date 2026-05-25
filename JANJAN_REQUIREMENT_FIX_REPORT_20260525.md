# JANJAN 요구사항 수정 및 점검 보고서

작성일: 2026-05-25

## 목표

사용자 화면에 있던 사업자용 테이블 관리 진입점을 제거하고, 사업자 계정의 테이블 화면에서 테이블 관리와 카메라 매핑을 처리하도록 정리했습니다. 또한 초대코드로 세션 입장이 되지 않거나 테이블 사용 상태가 매핑되지 않는 문제, 프로필 화면의 중앙 정렬 및 닉네임 연동 문제를 함께 수정했습니다.

## 반영 내용

### 1. 사용자 화면의 사업자 테이블 관리 제거

- 사용자 홈 화면의 `사업자 테이블 관리` 버튼을 제거했습니다.
- 사용자용 Navigation Graph에서 `businessOwnerFragment` 진입점을 제거했습니다.
- 사용자 플로우는 QR 스캔, 초대코드 입력, 주문, 색상 매핑, 정산 흐름만 남도록 정리했습니다.

관련 파일:

- `app/src/main/java/com/gachon/janjan/domain/session/ui/SessionHomeFragment.kt`
- `app/src/main/java/com/gachon/janjan/domain/session/ui/SessionScreens.kt`
- `app/src/main/res/navigation/nav_graph.xml`

### 2. 사업자 계정 테이블 화면에 테이블 관리 및 카메라 매핑 연결

- 사업자 계정의 하단 탭 `테이블` 화면에서 테이블 목록을 불러오도록 연결했습니다.
- Firestore에 테이블 문서가 없을 때도 첨부 이미지 기준의 1번부터 9번까지 기본 테이블을 표시합니다.
- 설정 버튼을 누르면 테이블별 IP 설정, 삭제, 추가 모드로 전환됩니다.
- 테이블 IP를 저장하면 다음 데이터가 함께 생성/갱신됩니다.
  - `stores/{storeId}/tables/{tableId}`
  - `stores/{storeId}/tableCameraMappings/{tableId}`
  - `sessions/{sessionId}/cameraMappings/{tableId}`
  - `cameraDevices/{cameraDeviceId}`
- 카메라 IP만 입력해도 기본 스트림 URL은 `http://{ip}:8080/video`로 자동 구성됩니다.
- 매핑 시 활성 세션이 없으면 해당 테이블용 활성 세션과 초대코드를 생성합니다.
- 생성된 초대코드가 테이블 문서에도 같이 저장되도록 보강했습니다.

관련 파일:

- `app/src/main/java/com/gachon/janjan/MainActivity.kt`
- `app/src/main/java/com/gachon/janjan/TableFragment.kt`
- `app/src/main/java/com/gachon/janjan/TableAdapter.kt`
- `app/src/main/java/com/gachon/janjan/StoreTable.kt`
- `app/src/main/java/com/gachon/janjan/domain/owner/repository/BusinessCameraRepository.kt`
- `app/src/main/res/layout/fragment_table.xml`

### 3. 초대코드 세션 입장 오류 수정

- 초대코드 입력값을 공백, 따옴표, 하이픈 제거 후 대문자로 정규화합니다.
- 기존처럼 `status == active`인 문서만 찾지 않고, `active`, `settling`, 닫히지 않은 세션 순서로 조회합니다.
- 정확 일치 조회 실패 시 최근 세션 200개를 로컬 정규화 비교해 예전 형식의 inviteCode도 찾을 수 있게 했습니다.
- QR payload에서도 `inviteCode`, `invite_code`, `code`, 단순 코드 문자열을 모두 처리합니다.

관련 파일:

- `app/src/main/java/com/gachon/janjan/domain/session/repository/SessionRepository.kt`
- `app/src/main/java/com/gachon/janjan/domain/session/viewmodel/SessionViewModel.kt`

### 4. 초대코드 입장 후 테이블 매핑 반영

- 사용자가 초대코드 또는 QR로 세션에 입장하면 해당 세션의 테이블 문서에 `activeSessionId`와 `inviteCode`를 병합 저장합니다.
- 이로 인해 사업자용 테이블 화면의 `사용중 테이블` 카운트와 각 테이블 상태가 입장 후 갱신될 수 있습니다.

관련 Firestore 필드:

- `storeId`
- `storeName`
- `tableId`
- `tableNumber`
- `activeSessionId`
- `inviteCode`
- `updatedAt`

### 5. 프로필 정보 연동 수정

- `users/{uid}` 문서에서 `nickname`, `name`, `bio`, `description`, `phone`, `address`를 읽어 프로필 화면에 반영합니다.
- 프로필 설정 화면의 닉네임과 자기소개가 하드코딩 값이 아니라 현재 사용자 데이터로 초기화됩니다.
- 저장 버튼을 누르면 `users/{uid}`에 `nickname`, `bio`가 병합 저장되고, 화면 상태도 즉시 갱신됩니다.
- 세션 참여자 이름도 저장된 닉네임을 우선 사용하도록 정리했습니다.

관련 파일:

- `app/src/main/java/com/gachon/janjan/domain/session/model/UserProfile.kt`
- `app/src/main/java/com/gachon/janjan/domain/session/viewmodel/SessionViewModel.kt`
- `app/src/main/java/com/gachon/janjan/domain/session/ui/SessionScreens.kt`

### 6. 프로필 내 상태 중앙 정렬

- 프로필 `내 상태` 탭의 `내 간: ...` 문구와 설명 텍스트를 카드 내부 중앙 기준으로 정렬했습니다.
- 카드 내 아이콘, 상태 문구, 설명 문구가 모두 같은 중심축을 쓰도록 정리했습니다.

관련 파일:

- `app/src/main/java/com/gachon/janjan/domain/session/ui/SessionScreens.kt`

## Firebase 데이터 흐름

### 사업자 테이블 카메라 매핑

1. 사업자 계정으로 앱 진입
2. 하단 탭에서 `테이블` 선택
3. 설정 버튼 선택
4. 특정 테이블의 IP 버튼 선택
5. 스마트폰 카메라 IP 입력 후 저장
6. 활성 세션이 없으면 `sessions`에 새 세션 생성
7. 테이블 문서, 매핑 문서, 카메라 장치 문서에 같은 `sessionId`, `tableId`, `inviteCode` 저장

### 일반 사용자 초대코드 입장

1. 사용자가 QR 또는 초대코드 화면에서 코드 입력
2. 입력값 정규화
3. `sessions`에서 inviteCode 조회
4. 참여자 문서 생성/갱신
5. `stores/{storeId}/tables/{tableId}`에 `activeSessionId`와 `inviteCode` 반영
6. 사용자 화면은 해당 세션을 구독하고 주문/정산/색상 매핑 흐름으로 연결

## 점검 결과

다음 명령으로 검증했습니다.

```bash
sh ./gradlew :app:assembleDebug
sh ./gradlew :app:testDebugUnitTest
sh ./gradlew :app:lintDebug
```

결과:

- Debug APK 빌드 성공
- Debug Unit Test 성공
- Debug Lint 성공

## 남은 운영 확인 항목

- 실제 Firebase 프로젝트에서 사업자 계정의 `stores/{uid}` 문서가 로그인 UID와 같은 ID로 존재하는지 확인해야 합니다.
- 실제 카메라 스트리밍 앱이 `http://{ip}:8080/video`가 아닌 다른 URL을 쓰면 IP 저장 시 스트림 URL을 직접 입력해야 합니다.
- Firestore 보안 규칙에서 `stores`, `sessions`, `cameraDevices`, `participants` 쓰기 권한이 현재 로그인 사용자에게 허용되어 있어야 실제 기기에서도 저장됩니다.

## 추가 수정: 예시 초대코드 연결 실패 보강

사용자가 Firebase Console에서 확인한 예시 초대코드가 `sessions` 문서가 아니라 `cameraDevices/camera_192_168_0_10` 같은 카메라 장치 문서에만 있는 경우를 처리하도록 보강했습니다.

변경된 조회 순서:

1. `sessions` 컬렉션에서 `inviteCode` 조회
2. `cameraDevices` 컬렉션에서 `inviteCode` 조회
3. `stores/*/tableCameraMappings` 컬렉션 그룹에서 `inviteCode` 조회
4. `stores/*/tables` 컬렉션 그룹에서 `inviteCode` 조회
5. 대소문자, 공백, 하이픈 차이를 보정하는 fallback 조회

카메라 장치나 테이블 매핑 문서에서 초대코드를 찾으면 `sessionId`, `assignedSessionId`, `activeSessionId` 중 존재하는 값을 사용해 세션을 찾습니다. 세션 문서가 없으면 해당 초대코드로 입장 가능한 `sessions/{sessionId}` 문서를 자동 보강하고, 테이블 문서에도 `activeSessionId`와 `inviteCode`를 다시 병합 저장합니다.

추가 검증:

```bash
sh ./gradlew :app:assembleDebug
sh ./gradlew :app:testDebugUnitTest
sh ./gradlew :app:lintDebug
```

결과:

- Debug APK 빌드 성공
- Debug Unit Test 성공
- Debug Lint 성공
