# HTTP 진입점

## 범위와 경계

요청 DTO 검증, principal 전달, Service 호출과 ResponseEntity를 담당한다. Repository 조회·소유권 판단·외부 전송·트랜잭션을 Controller로 가져오지 않는다. Entity를 요청/응답으로 사용하지 않는다.

## 구현 조건

- 현재 여섯 Controller에 23개 method/path가 있으며 차단된 메서드도 소스에 남아 있다. Controller mapping 존재와 실제 URL 허용은 다르다.
- 새 사용자 작업의 userId는 `@AuthenticationPrincipal`에서 얻는다. 기존 capture path userId·사진 ID 단독 조회·FCM token 단독 삭제는 미해결 예외이므로 복제하지 않는다.
- 새/변경 endpoint는 method/path 보안 규칙과 API 테스트를 함께 맞춘다. 공개 경로의 credential 검증을 생략하지 않는다.
- DTO의 `@Valid` 유무·성공 본문 유무·문자열/배열/Slice 형식을 유지하며 계약 변경을 숨기지 않는다. 직접 Entity/Page 응답을 새로 도입해 응답 필드를 우연히 확장하지 않는다.
- 관리자 이관은 구 경로 우회 진입점까지 함께 다뤄야 한다. 일반 FCM 토큰 관리와 내부 자동 알림에 관리자 권한을 전파하지 않는다.

## 검증

`src/test/java/com/farmguardian/farmguardian/controller`의 관련 통합 테스트와 `ApiAuthorizationIntegrationTest`를 함께 확인한다. 무효/만료 Access·Refresh Bearer·다른 사용자·누락 입력·허용되지 않은 method 사례가 Service 호출 전에 거부되는지 검증한다. Image Controller 업무 테스트는 아직 없으므로 URL mock 테스트를 사진 소유권 검증으로 간주하지 않는다.
