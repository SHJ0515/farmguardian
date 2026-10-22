# 오류 변환

## 범위와 경계

BusinessException, ErrorCode, 도메인별 예외, MVC의 GlobalExceptionHandler를 담당한다. 필터 이전의 인증·인가 응답 작성은 별도 보안 핸들러의 책임이다. 여기서 세션 폐기·DB 변경·외부 재시도를 수행하지 않는다.

## 구현 조건

- 업무 예외는 `exception/{domain}`에 두고 ErrorCode를 통해 응답한다. 기존 코드·HTTP 의미를 변경할 때 Controller·보안 테스트와 소비자 영향을 확인한다.
- AccessDeniedException과 인증 예외가 필터/MVC 경계에 따라 모순된 JSON을 만들지 않도록 공통 DTO를 유지한다.
- 현재 일반 Exception 처리기는 500으로 변환한다. 잘못된 JSON·enum을 자동으로 400 처리한다고 추측하지 않는다. 새 예외 handler의 우선순위를 검증한다.
- 로그에 요청 본문·비밀번호·토큰을 추가하지 않는다. Refresh 재사용 예외를 트랜잭션 안으로 이동시켜 폐기 commit을 취소하지 않는다.

## 검증

업무·검증·인증·인가·누락 query·잘못된 경로 타입의 응답 schema를 확인한다. URL 단계에서 먼저 차단되는 요청과 MVC에 도달한 404/405를 구분한다. 코드에 정의됐지만 현재 호출되지 않는 야간·FCM 예외를 실제 제공 동작으로 간주하지 않는다.
