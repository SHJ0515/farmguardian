# 구현 시 주의할 점

## Refresh 실패 응답과 폐기 기록

재사용한 Refresh에 오류를 던지는 위치를 옮기면 세션 폐기가 함께 rollback될 수 있다. `AuthService.refreshAccessToken`은 `NOT_SUPPORTED`로 실행하고, 별도 `RefreshSessionTransactionService.rotate`가 폐기·재사용 기록을 commit한 뒤 결과 record를 반환한다. 외부 Service가 그 결과를 오류로 변환한다. 두 클래스를 하나의 기본 트랜잭션으로 합치는 리팩토링은 보안 의미를 바꾼다.

수정 시 다음 검증으로 실패 응답 후 실제 DB 상태까지 확인한다.

```powershell
./gradlew.bat test --tests '*AuthRefreshRollbackIntegrationTest' --tests '*AuthRefreshConcurrencyIntegrationTest'
```

`RefreshTokenRotationResult`는 API 응답이 아니라 Service 사이의 package-private 결과이므로 `dto/response`로 옮길 이유가 없다. 회원가입은 `AuthService`에 남아 있으며 모든 인증 로직을 TransactionService로 옮긴 구조가 아니다.

## UNIQUE 충돌을 막는 flush 두 곳

같은 사용자·클라이언트의 재로그인, 같은 세션의 재발급에서 새 행 INSERT가 기존 행 UPDATE보다 먼저 실행되면 활성 슬롯 UNIQUE가 충돌한다. `active_slot`·`current_slot`은 활성일 때 1, 해제 시 NULL을 사용한다. NULL로 바꾼 UPDATE를 새 INSERT보다 먼저 보내는 로그인·rotation의 명시적 `flush()` 두 곳은 필요하다. 일반적인 더티체킹 선호를 이 두 곳의 제거 근거로 삼지 않는다.

잠금 순서는 사용자 → 세션 → 이력이다. 서로 다른 경로에서 반대 순서로 잠그거나 비관적 잠금을 `@Version`만으로 대체하면 동시성 계약이 달라진다. H2 통과만으로 실제 MySQL의 잠금·격리 의미가 같다고 결론 내리지 않는다.

## DB 버전과 복구

V1은 옛 `refresh_tokens`를 포함하는 기준선이고 V2가 이를 새 세션·해시 이력으로 대체한다. V2는 옛 Refresh 원문을 복사하지 않고 테이블을 삭제하므로 기존 로그인은 재로그인이 필요하다. Git checkout은 이 DB 변경을 되돌리지 않는다.

기본 테스트의 Hibernate `create-drop`은 Flyway SQL의 CHECK·UNIQUE 검증을 대신하지 않는다. 스키마 변경은 격리된 Flyway 테스트에서 확인하고 실제 MySQL 비교가 필요하면 별도의 빈 검증 DB를 사용한다. 예전 legacy dump는 새 세션·이력이 담긴 현재 DB의 완전한 백업이 아니다.

새 Refresh Entity의 제약조건은 SQL이 관리하지만 기존 User·Device·FcmToken에는 DDL 메타데이터가 남아 있다. 이를 발견했다고 기존 Entity 전체의 매핑을 일괄 삭제하지 않는다.

## 현재 장치를 따라가면 과거 사진도 이동한다

사진의 직접 소유자 필드가 없으므로 현재 `image.device.user`를 기준으로 조회하면 장치 A→B 재할당 시 A의 과거 사진이 B에게 보인다. 먼저 장치 소유권만 검사하는 방식으로는 과거 사진의 소유권을 복원할 수 없다. 기존 자료의 생성 시점 소유 근거를 확인하고, 불명확한 행을 현재 소유자에게 몰아넣지 않아야 한다.

직접 촬영 장치도 현재 UUID 규칙으로만 찾아 일반 장치와 같은 연결 해제·재등록 대상이 된다. `mobile-user-{id}` 문자열 일치가 소유권 검사를 대신하지 않는다. 사진 상세·장치별 조회·가상 장치·재할당을 함께 검증해야 부분 수정의 누락을 찾을 수 있다.

## 해충 표시의 두 기준

분석 직후 응답은 신뢰도 항목을 필터링하지만 상세 조회의 `pestDetected`는 저장 JSON의 `total >= 1`을 본다. 따라서 `total=1`이고 모든 신뢰도가 0.2 미만이면 두 응답이 달라질 수 있다. 신뢰도 상수만 높이면 차이가 더 커질 수 있으므로 저장 결과 해석·상세 표시·알림의 경계값을 같은 사례로 비교해야 한다.

목록의 `hasAnalysisResult`는 null 여부일 뿐 분석 성공·해충 유무를 뜻하지 않는다. 현재 상세에서 JSON이 없거나 파싱이 실패하면 해충 여부를 false로 처리하므로, 분석 실패를 ‘해충 없음’으로 오해하지 않는 상태 표현이 후속 과제다.

## 환경과 외부 연동

- `firebase.enabled=false`는 Firebase 초기화만 건너뛴다. `FcmService` 호출 자체를 막지 않고 발송 예외가 흡수될 수 있어 HTTP 성공으로 실제 전달을 판정할 수 없다.
- MQTT 핸들러가 비동기이므로 Service 호출 반환·HTTP 성공은 장치의 촬영 완료 증거가 아니다. broker 수락과 장치 실행 완료를 구분한 시험이 필요하다.
- `New-LocalJwtSecret.ps1 -Force`는 기존 override 파일 전체를 다시 쓴다. JWT 외의 DB·MQTT 값이 같은 파일에 있다면 먼저 별도 보존해야 한다. 단순 실행 편의를 위해 회전시키지 않는다.
- 키 생성 스크립트는 Base64 문자열을 만들지만 JWT provider는 그 문자열의 UTF-8 바이트를 서명 키로 사용한다. 디코딩 방식으로 바꾸면 같은 설정 문자열로 기존 토큰을 검증하지 못한다.
- Spring Boot 4의 애플리케이션 JSON 코드는 `tools.jackson`을 사용한다. JJWT의 Jackson runtime 의존성과 혼동해 전체 import를 일괄 치환하지 않는다.

## URL 검사와 검증 증거

`available`·`connect` 같은 정적 경로를 `/{deviceId}`보다 뒤에 두면 다른 메서드가 ID 경로에 흡수될 수 있다. ERROR 디스패치 전체를 공개하면 오류 처리라는 이유로 업무 경로가 열릴 수 있으므로 `/error`와 디스패치 조건의 결합을 보존한다. 변경 시 `ApiAuthorizationIntegrationTest`의 정적 경로·메서드·디스패치 사례를 확인한다.

MockMvc의 직접 ERROR 디스패치 검증은 실제 Servlet 자동 재디스패치 검증이 아니다. H2/mock, 격리 MySQL, 실제 외부 연동, 부하 시험을 같은 성공 증거로 합치지 않는다. 날짜·선택 테스트가 다른 실행의 테스트 수를 합산하지 않는다.

FCM 수동 발송 테스트는 토큰 placeholder가 그대로면 그냥 반환한다. 따라서 JUnit의 skipped=0·실패=0만 보고 실제 푸시가 전송됐다고 판단하면 안 된다. 시험을 위해 실제 토큰을 테스트 상수에 commit하는 방식도 사용하지 않는다.

Windows 루트의 `nul`은 예약 장치명과 충돌할 수 있는 로컬 산출물이다. 검색에서는 기존 제외 규칙을 유지한다. README의 옛 Refresh 구조·공개 API 표와 과거의 운영 배포 로드맵은 현재 구현·현재 포트폴리오 목표를 대신하지 않는다.
