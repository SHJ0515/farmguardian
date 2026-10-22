# 업무 흐름과 트랜잭션

## 범위와 경계

인증 세션, 장치 관리, 사진 저장·분석, FCM·MQTT 업무 흐름을 담당한다. Controller의 HTTP 형식이나 DB SQL 버전 관리를 서비스에서 대신하지 않는다. 외부 FastAPI·앱·펌웨어 구현을 이 패키지의 소스로 가정하지 않는다.

## 인증 세션

`AuthService`는 가입과 인증 흐름을, `RefreshSessionTransactionService`는 잠금이 필요한 세션 변경을 맡는다. Refresh 외부 진입의 `NOT_SUPPORTED`와 내부 트랜잭션을 합치지 않는다. 재사용 탐지 결과를 반환해 폐기 기록 commit 후 오류를 만든다. package-private `RefreshTokenRotationResult`는 API DTO가 아니다.

User → Session → History 잠금 순서와 재로그인/rotation의 두 flush를 유지한다. signout은 principal·Refresh 소유자·clientUuid·현재 이력이 맞아야 한다. 탈퇴는 Refresh 세션 폐기·장치 해제·사용자 soft delete를 수행하지만 FCM·사진의 수명까지 완성하지는 않았다.

## 장치·사진·연동

- 한 계정에 여러 장치, 각 물리 장치에 한 현재 소유자를 허용한다. 현재 연결은 CONNECTED만 거부하고 경쟁 제어가 없다. MOBILE UUID 단독 조회·일반 재등록·INACTIVE 처리는 후속 설계 대상이다.
- 사진 메타데이터 저장 → DB 트랜잭션 밖 분석 호출 → 결과 저장의 경계를 보존한다. 분석 실패로 사진 정보를, 푸시 실패로 결과를 함께 되돌리지 않는다.
- 현재 신뢰도 필터는 0.2 이상이며 향후 0.8 수준 상향 계획의 정확값은 미정이다. 상세 DTO의 total 기반 판정과 함께 검증해야 한다.
- FCM 중복 등록은 현재 소유자 변경 없이 반환한다. token 문자열 단독 삭제·원문 로그·탈퇴 잔존은 보완 대상이다. 관리자 개별 알림의 기존 실패 흡수·빈 성공 응답 유지 결정이 일반 토큰 보안 수정을 금지하는 것은 아니다.
- MQTT 촬영은 현재 path에서 받은 userId와 장치를 비교하는 결함이 있다. 새로운 호출에서는 principal을 신뢰하고 거부 시 발행하지 않는다. 야간 검사는 비활성 상태를 유지하되 19~06시 제한 의도를 지우지 않는다. publish 반환은 촬영 완료가 아니다.

## 검증

인증 변경은 Refresh 보안·Session·동시성·rollback 테스트를 함께 확인한다. 장치·사진·FCM·촬영 변경은 정상·타인 접근·재할당·외부 실패·부분 저장·중복 요청을 검증한다. 현재 Image Service·MQTT Service 전용 테스트가 없으므로 Controller mock 통과만으로 업무 검증을 끝내지 않는다. 실제 외부 발송이나 업무 DB를 자동 테스트에 연결하지 않는다.
