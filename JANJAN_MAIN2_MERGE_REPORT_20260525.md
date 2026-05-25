# JANJAN-main-2 병합 및 검증 보고서

작성일: 2026-05-25

## 병합 대상

- 현재 작업본: `/Users/shinjunha/Documents/Gachon_Homework/MP_git/JANJAN`
- 팀원 ZIP: `/Users/shinjunha/Downloads/JANJAN-main-2.zip`

## 병합 방향

`JANJAN-main-2.zip`은 개인 사용자 화면과 사업자 화면을 하나의 `MainActivity` 안에서 `userType` 값으로 분기하는 구조였다. 현재 작업본에는 이미 QR/색상 매핑, 세션, CV/Firebase 연동 코드가 들어가 있었기 때문에 기존 세션/주문/상태 로직은 유지하고, 팀원 ZIP의 인증/사업자 관리/정산 UI를 충돌 없이 붙이는 방식으로 병합했다.

## 주요 반영 내용

- `LandingActivity`를 앱 시작 화면으로 등록했다.
- 개인 로그인은 `MainActivity`의 기존 NavHost 기반 세션/주문/상태 플로우로 진입하도록 유지했다.
- 사업자 로그인은 `MainActivity`에서 테이블, 메뉴, 통계, 프로필 하단 탭을 보여주도록 통합했다.
- 로그인 성공 후 뒤로가기로 로그인 화면에 돌아가지 않도록 Activity back stack을 정리했다.
- 팀원 ZIP의 사업자용 화면을 추가했다.
  - 로그인/회원가입/주소 검색/가게 검색
  - 사업자 테이블 관리 및 카메라 IP 저장
  - 메뉴 추가/수정/품절 관리
  - 매출 통계
  - 매장 프로필/앱 설정/알림
- `item_menu.xml` 이름 충돌을 해결했다.
  - 사업자 메뉴 관리는 `item_menu.xml`과 `ItemMenuBinding`을 사용한다.
  - 개인 주문 메뉴 카드는 새 `item.xml`과 `ItemBinding`을 사용한다.
- 정산 화면 파일을 추가하고 `nav_graph.xml`에 `settlementFragment` 목적지를 등록했다.
- Firebase Storage, CardView, RecyclerView 의존성을 추가했다.

## 안정성 보강

- `SettlementActivity`에서 Firestore `participants` 필드를 안전하게 파싱하도록 수정했다.
  - `Long`, `Int`, `String` 숫자 값을 모두 처리한다.
  - 잘못된 데이터 형태가 와도 앱이 바로 종료되지 않는다.
  - 결제 상태 저장 실패 시 Toast로 안내한다.
- `ParticipantAdapter`에서 체크박스 재활용 시 기존 리스너가 잘못 호출되지 않도록 정리했다.
- `StatisticsFragment`에서 `menuOrders` 금액 파싱의 강제 언래핑(`!!`)을 제거했다.
- 통계/정산 금액 표시는 `Locale.KOREA` 기준으로 포맷하도록 정리했다.

## 검증 결과

아래 Android 검증은 모두 성공했다.

```bash
sh ./gradlew :app:assembleDebug
sh ./gradlew :app:testDebugUnitTest
sh ./gradlew :app:lintDebug
```

Firebase Functions는 이번 병합에서 코드를 변경하지 않았지만, 임시 npm CLI를 `/tmp`에 받아 의존성을 설치한 뒤 TypeScript 빌드를 확인했다.

```bash
npm install --no-package-lock --ignore-scripts
npm run build
```

검증 후 생성된 `functions/node_modules`와 `functions/lib` 산출물은 작업트리에서 제거했다.

## 남은 참고 사항

- Android lint는 성공했지만 일부 경고는 남아 있다.
  - 최신 SDK/라이브러리 버전 안내
  - 일부 XML `Switch`를 MaterialSwitch로 바꾸라는 권장사항
  - `notifyDataSetChanged()` 성능 권장사항
  - 벡터 이미지 크기 권장사항
- 위 항목들은 현재 빌드를 막는 오류는 아니며, 앱 실행 실패를 일으키는 치명 오류로 분류되지는 않았다.
