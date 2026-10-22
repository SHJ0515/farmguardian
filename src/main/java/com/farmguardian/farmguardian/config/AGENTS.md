# 설정·인증 경계

## 범위와 경계

SecurityFilterChain, `auth` principal·오류 핸들러, `jwt` 발급·검증, FastAPI/Firebase/MQTT 클라이언트를 조립한다. 장치 소유권·사진 판정·알림 수신자 선택은 Service의 업무이며 설정 계층에서 대신 구현하지 않는다.

## 구현 조건

- 정적 차단 경로를 동적 ID 경로보다 앞에 두고 허용 method/path만 개방한다. ERROR 디스패치와 `/error`의 AND 조건, 기본 logout 비활성화를 유지한다.
- `JwtFilter`는 Access를 한 번 파싱하고 기존 Authentication을 보존한다. 무효 Bearer는 인증을 만들지 않으며 PUBLIC 업무 검증을 막는 전역 응답으로 바꾸지 않는다.
- Access와 Refresh의 type·audience·필수 claim 검증을 구분한다. Refresh에 role/userId를 넣어 사용자 인증을 만들지 않는다. DB 세션 유효성·재사용은 provider가 아닌 Service에서 확인한다.
- 현재 키 문자열은 UTF-8 바이트로 사용한다. Base64 디코딩 도입은 키 의미를 바꾸므로 단순 정리로 수행하지 않는다.
- 필터 오류도 기존 ErrorResponseDto와 Spring ObjectMapper를 사용한다. HEAD 본문 억제와 인증 응답의 Bearer 헤더를 보존한다.
- Firebase 비활성은 Service의 무동작 보장이 아니다. MQTT 비동기 발행 반환을 장치 완료 확인으로 바꾸어 설명하지 않는다.

## 검증

JWT 변경은 `JwtTokenProviderTest`, `JwtFilterTest`, `RefreshTokenHasherTest`의 만료·용도 혼용·잘못된 claim·기존 인증 보존 사례를 확인한다. URL 변경은 `ApiAuthorizationIntegrationTest`의 전체 mapping, 정적/동적 충돌, HEAD·OPTIONS·ERROR 경계를 갱신한다. 외부 클라이언트 구성 테스트에는 실제 cloud credential을 사용하지 않는다.
