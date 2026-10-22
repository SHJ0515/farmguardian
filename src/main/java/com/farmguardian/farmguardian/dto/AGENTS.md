# 요청·응답 데이터

## 범위와 경계

`request`는 외부 입력, `response`는 외부 출력과 현재 Entity→DTO 매핑을 담당한다. Repository·Service 호출이나 사용자 권한 결정을 DTO에 넣지 않는다. Service 내부 결과 record를 외부 응답으로 노출하지 않는다.

## 구현 조건

- DTO annotation과 Controller의 `@Valid`를 함께 확인한다. 필드에 annotation만 추가하고 HTTP에서 검증된다고 가정하지 않는다.
- `clientUuid`는 로그인 클라이언트 식별자이며 물리 장치 UUID와 다르다. 회원가입에서 role을 입력받지 않는다.
- Device 응답은 현재 상태 필드를 포함하지 않는다. PATCH에서 null은 기존 값 유지이므로 필드 삭제 기능을 추가할 때 계약을 따로 정한다.
- 사진 목록과 상세 DTO를 구분한다. `analysisResult`는 JSON 문자열, `hasAnalysisResult`는 null 여부다. 결과 있음·해충 있음·분석 성공을 같은 뜻으로 사용하지 않는다.
- 상세 `pestDetected`의 total 기준과 분석 Service의 신뢰도 기준은 현재 다르다. 임계값 상향 작업은 두 경계를 함께 검증한다.
- ErrorResponseDto의 일반 오류와 필드 검증 오류를 유지한다. FastAPI의 JSON `object` 이름·신뢰도 map·좌표 형식을 임의 치환하지 않는다.

## 검증

입출력 변경은 관련 Controller 테스트의 JSON 필드·null·enum·길이·필수값 검증을 갱신한다. 사진 DTO·외부 응답의 오류 사례는 전용 테스트가 부족하다. 실제 응답 검증 없이 소비자 계약이 호환된다고 단정하지 않는다.
