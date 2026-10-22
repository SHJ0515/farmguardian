# 외부 인터페이스 계약

## 공통 형식과 인증

현재 로컬 기본 주소는 `http://localhost:8080`이다. JSON 요청은 `Content-Type: application/json`, 사용자 요청은 `Authorization: Bearer <accessToken>`을 사용한다. Access와 Refresh는 서로 대체하지 못한다. 토큰은 불투명한 credential로 보관하고 서버가 반환한 문자열을 그대로 전달한다.

공개 진입점은 `GET /`, `POST /api/auth/signup`, `POST /api/auth/signin`, `POST /api/auth/refresh`다. 공개 경로라도 로그인·재발급의 credential 검증을 생략하지 않는다. 나머지 허용된 사용자 API는 USER 또는 ADMIN Access가 필요하다. ADMIN도 사용자 API의 소유권을 우회할 권한은 없다.

성공 상태는 아래 현재 API에서 모두 200이다. `본문 없음`과 JSON 객체·배열, 일반 문자열을 구분한다. DTO의 날짜는 시간대 정보가 없는 `LocalDateTime`이며 UTC 오프셋이 붙는 계약으로 가정하지 않는다. 사용자 등록 장치 목록은 배열, 사진 목록은 Slice다.

### 공통 실패

| 조건 | HTTP·code | 소비자가 구분할 점 |
| --- | --- | --- |
| 보호/차단 경로에 Access 없음·무효·만료·Refresh Bearer | 401 · `AUTH_006` | 필터가 Controller 전에 거부; `WWW-Authenticate: Bearer` |
| 인증은 됐지만 허용되지 않은 경로·역할 | 403 · `AUTH_009` | 현재 차단 경로는 ADMIN도 거부 |
| 검증 annotation 위반·필수 query 누락·경로 타입 불일치 | 400 · `COMMON_001` | DTO 검증이면 필드별 오류 목록 포함 |
| 처리하지 못한 서버 오류 | 500 · `COMMON_999` | JSON 파싱·enum 오류까지 일관된 400으로 처리하는 계약은 아직 보완 대상 |

오류 JSON 형식은 다음과 같다. `fieldErrors`는 DTO 검증 외에는 null이다. HEAD 오류는 본문을 쓰지 않는다. Firewall 같은 선행 차단의 400까지 이 형식으로 통일한 상태는 아니다.

```json
{
  "success": false,
  "status": 400,
  "code": "COMMON_001",
  "message": "입력값이 올바르지 않습니다",
  "timestamp": "2026-10-22T12:00:00",
  "fieldErrors": [{"field": "email", "message": "검증 실패 설명"}]
}
```

미정의 경로·메서드, HEAD·OPTIONS·Actuator는 기본 거부다. 따라서 매핑이 없다고 항상 404, 메서드가 다르다고 항상 405인 것은 아니다. MVC까지 도달한 경우에는 404/405에 `COMMON_001`을 사용하지만 보안 경계가 먼저 적용된다.

## 사용자와 인증

| Method·경로 | 입력 | 200 응답 | 주요 업무 오류 |
| --- | --- | --- | --- |
| `GET /` | 없음 | 일반 문자열 `Welcome to API!` | 공통 서버 오류 |
| `POST /api/auth/signup` | JSON `email`, `password` 필수; email 형식 | 본문 없음 | 중복 이메일 409 `AUTH_001` |
| `POST /api/auth/signin` | JSON `email`, `password`, `clientUuid` 필수 | 토큰 객체 | 계정 없음 404 `AUTH_002`; 비밀번호 불일치 401 `AUTH_003` |
| `POST /api/auth/refresh` | JSON `refreshToken`, `clientUuid` 필수 | 새 토큰 객체 | 유효성·세션·재사용 오류 401 `AUTH_004`; 클라이언트 불일치 401 `AUTH_008` |
| `POST /api/auth/signout` | Access + JSON `refreshToken`, `clientUuid` 필수 | 본문 없음 | 계정 없음 404 `AUTH_002`; 토큰·소유자 불일치 401 `AUTH_004`; 클라이언트 불일치 401 `AUTH_008` |
| `DELETE /api/auth/withdraw` | Access, 본문 없음 | 본문 없음 | 사용자 없음 404 `AUTH_002` |

`clientUuid`는 공백이 아닌 최대 255자다. 물리 IoT 장치 식별자가 아니라 로그인 클라이언트를 구분한다. 로그인·재발급·로그아웃에서 같은 값을 유지한다. 현재 비밀번호는 공백 여부를 검사하며 별도 길이·복잡도·이메일 정규화 계약은 미정이다.

토큰 객체는 `{"grantType":"Bearer","accessToken":"…","refreshToken":"…"}`다. 재발급 성공 시 두 값을 함께 교체한다. 같은 Refresh의 병렬 재발급 또는 성공 후 재전송은 재사용으로 처리돼 새 세션 토큰도 더 이상 갱신하지 못할 수 있다. 응답 유실을 고려한 안전한 재시도 계약은 추가되지 않았다.

로그아웃·탈퇴가 기존 Access의 즉시 무효화를 보장하지는 않는다. 로그인 실패 응답 통일도 보류돼 있으므로 현재 404와 401을 동일한 서버 응답이라고 설명하지 않는다.

## 장치

| Method·경로 | 입력 | 200 응답 | 주요 업무 오류 |
| --- | --- | --- | --- |
| `POST /api/devices/connect` | Access + `deviceUuid` 필수; `alias`, `targetCrop`, `latitude`, `longitude` 선택 | 장치 객체 | 사용자 없음 404 `AUTH_002`; 장치 없음 404 `DEVICE_001`; 이미 연결됨 409 `DEVICE_002` |
| `GET /api/devices` | Access | 장치 객체 배열 | 공통 실패 |
| `GET /api/devices/{deviceId}` | Access + 숫자 ID | 장치 객체 | 없음/타인 장치 모두 현재 403 `DEVICE_003` |
| `PATCH /api/devices/{deviceId}` | Access + 선택 필드 `alias`, `targetCrop`, `latitude`, `longitude` | 장치 객체 | 없음/타인 장치 현재 403 `DEVICE_003` |
| `DELETE /api/devices/{deviceId}` | Access | 본문 없음 | 없음/타인 장치 현재 403 `DEVICE_003` |

`alias`는 최대 10자다. PATCH의 null·미지정 필드는 현재 값을 유지하므로 null을 보내 필드를 지우는 계약이 아니다. 좌표의 지리적 범위 검증은 아직 없다. 등록 입력은 현재 `deviceUuid`이며 별도 시리얼 번호·일회용 등록 코드 API는 없다.

장치 객체 필드: `id`, `deviceUuid`, `alias`, `targetCrop`, `targetCropName`, `latitude`, `longitude`, `createdAt`, `updatedAt`. `status`는 포함하지 않는다. 작물은 선택값이며 다음 enum을 사용한다.

| 값 | 표시명 | 값 | 표시명 |
| --- | --- | --- | --- |
| `POTATO` | 감자 | `CHILI_PEPPER` | 고추 |
| `PERILLA_SEEDS` | 들깨 | `RADISH` | 무 |
| `NAPA_CABBAGE` | 배추 | `CABBAGE` | 양배추 |
| `CUCUMBER` | 오이 | `CORN` | 옥수수 |
| `BEAN` | 콩 | `GREEN_ONION` | 파 |

## 사진과 모바일 분석

| Method·경로 | 입력 | 200 응답 | 주요 업무 오류 |
| --- | --- | --- | --- |
| `POST /api/images/mobile/analyze` | Access + JSON `cloudUrl`, `width`, `height` | 분석 객체 | 가상 장치 없음 404 `DEVICE_001`; 분석 호출 실패 503 `IMAGE_003`; 분석 JSON 저장 변환 실패 500 `IMAGE_002` |
| `GET /api/images` | Access + `page`, `size` query | 목록용 사진 Slice | 공통 실패 |
| `GET /api/images/device/{deviceId}` | Access + 숫자 ID + `page`, `size` | 상세용 사진 Slice | 현재 없는 ID는 빈 목록; 소유권 검사는 아직 없음 |
| `GET /api/images/{originImageId}` | Access + 숫자 ID | 상세 사진 객체 | 없음 404 `IMAGE_001`; 현재 소유권 검사는 아직 없음 |

`cloudUrl`은 비어 있지 않은 문자열, `width/height`는 null이 아닌 정수다. 현재 URL 형식·저장소 소유권·허용 host·크기 양수/상한 검증은 없다. 이미지 바이트 multipart 업로드 API는 제공하지 않는다.

분석 객체 필드: `originImageId`, `cloudUrl`, `pestDetected`, `pests`. 각 `pests` 항목은 `pestName`, `confidence`, `boundingBox`이며 좌표는 `xtl`, `ytl`, `xbr`, `ybr`다. 현재 0.2 이상인 신뢰도 항목을 반환한다. 향후 0.8 수준으로 상향할 계획이지만 적용값·경계는 아직 확정되지 않았다.

목록용 사진 필드: `originImageId`, `createdAt`, `deviceId`, `deviceAlias`, `hasAnalysisResult`.

상세 사진 필드: `originImageId`, `cloudUrl`, `width`, `height`, `createdAt`, `deviceId`, `deviceAlias`, `deviceStatus`, `targetCrop`, `latitude`, `longitude`, `analysisResult`, `pestDetected`. `analysisResult`는 중첩 JSON 객체가 아니라 저장 JSON을 담은 **문자열**이다. 현재 상세 `pestDetected`는 JSON의 `total >= 1`을 사용하므로 분석 직후 응답과 다를 수 있다.

사진 목록은 최신 생성 시각순이며 기본 크기는 20, page는 0부터 시작한다. 현재 Spring `Slice`를 직접 직렬화하며 `content`에 위 DTO 배열을 담는다. 반환 타입의 접근자를 기준으로 페이지 정보의 의미는 다음과 같다.

| 페이지 정보 | 의미 |
| --- | --- |
| `number` | 현재 페이지 번호. 첫 페이지는 0 |
| `size` | 현재 페이지에 적용한 요청 크기 |
| `numberOfElements` | 이번 `content`에 실제로 담긴 항목 수 |
| `first`, `last` | 첫 페이지·마지막 페이지 여부. `last=false`이면 `page=number+1`로 다음 페이지 요청 |
| `sort`, `pageable` | 정렬·페이지 요청 정보. 내부 JSON 모양은 프레임워크 직렬화에 의존 |

위 의미는 Controller 반환형과 라이브러리 타입 대조로 확인했다. 실제 HTTP 응답의 전체 JSON 스냅샷 검증과 자체 고정 메타데이터 envelope는 아직 없다. `totalElements/totalPages`를 가진 Page 응답으로 가정하지 않는다. 프레임워크 설정 수준의 제한과 별개로 업무별 최대 size도 미확정이다.

분석 실패 응답을 받아도 사진 메타데이터는 저장됐을 수 있다. 재요청은 새 사진 행과 분석 호출을 만들 수 있고 idempotency key는 지원하지 않는다. 알림 실패도 이미 저장한 분석 결과를 취소하지 않는다.

## 알림 토큰과 촬영

| Method·경로 | 입력 | 200 응답 | 주요 업무 오류·한계 |
| --- | --- | --- | --- |
| `POST /api/fcm/token` | Access + JSON `token` 필수, `platform` = `ANDROID` 또는 `IOS` 필수 | 본문 없음 | 사용자 없음 404 `AUTH_002`; 기존 토큰은 현재 소유자와 무관하게 변경 없이 반환 |
| `DELETE /api/fcm/token` | Access + 필수 query `token` | 본문 없음 | 없는 토큰도 성공; 현재 호출자 소유권 검사는 없음 |
| `POST /api/users/{userId}/devices/{deviceId}/capture` | Access + 숫자 사용자/장치 ID | 일반 문자열 `Capture command sent successfully` | 장치 없음 404 `DEVICE_001`; path 사용자와 소유자 불일치 403 `DEVICE_003`; 미연결 400 `DEVICE_004`; 발행 호출 실패 500 `MQTT_001` |

촬영 URL은 아직 인증 principal과 path 사용자 ID를 대조하지 않는 결함이 있다. 향후 사용자 ID를 path에서 제거할 계획이며, 새 URL은 현재 제공되지 않는다. 야간 검사는 현재 꺼져 있어 `MQTT_002`가 정의돼 있다는 이유로 야간 요청을 거부한다고 가정하지 않는다. HTTP 성공은 촬영 완료 확인이 아니다.

## 현재 차단된 다섯 API와 관리자 계획

| 현재 method/path | 입력·성공 출력 | 현재 오류와 예정 방향 |
| --- | --- | --- |
| `GET /api/devices/available` | 현재 HTTP에서 사용할 성공 계약 없음 | 익명 401 / 인증 사용자 403. 관리자 장치 목록으로 이관 예정 |
| `POST /api/images/analyze` | 현재 HTTP에서 사용할 성공 계약 없음 | 익명 401 / 인증 사용자 403. 별도 DEVICE 인증 전까지 차단 |
| `POST /api/fcm/send/{userId}` | 현재 HTTP에서 사용할 성공 계약 없음 | 익명 401 / 인증 사용자 403. 관리자 개별 알림으로 이관 예정 |
| `POST /api/fcm/broadcast` | 현재 HTTP에서 사용할 성공 계약 없음 | 익명 401 / 인증 사용자 403. 개방 보류 |
| `GET /api/users/` | 현재 HTTP에서 사용할 성공 계약 없음 | 익명 401 / 인증 사용자 403. 중복 안내 mapping 제거 예정 |

위 경로는 다른 메서드도 차단한다. 남아 있는 내부 Controller 메서드의 입력 모양이 외부 사용 허가를 뜻하지 않는다. `/api/admin/**` 역시 모두 차단 중이다.

향후 확정된 경로는 `GET /api/admin/devices/available`, `POST /api/admin/notifications/users/{userId}`, `POST /api/admin/notifications/broadcast`다. 장치 목록은 AVAILABLE·기존 장치 필드·20개 고정·ID ASC를 유지하되 Page JSON과 size/sort 위반 처리는 미정이다. 관리자 인증의 DB 조회 실패 오류 계약도 아직 미정이다.

개별 알림 입력은 필수 `title`, `body`와 선택 `originImageId`, `cloudUrl`, `deviceId`를 유지할 계획이다. 수신자 토큰 없음과 기존에 흡수하던 Firebase 발송 실패는 빈 200을 유지하며, 인증·입력·DB 오류까지 200으로 바꾸는 계약은 아니다. 재요청 시 재발송하고 추가 rate limit·중복 제거·자동 retry·queue는 도입하지 않기로 했다. broadcast 경로의 이름 확정은 개방 승인이 아니다.

## 외부 분석기·메시징

| 경계 | 송신 입력 | 수신 출력·실패 의미 |
| --- | --- | --- |
| FastAPI `POST {base-url}/v1/infer` | JSON `{"url":"이미지 URL"}` | `crop`, `total`, `risk`, `object` 배열. 호출 예외는 `IMAGE_003`; 자동 retry·서비스 인증 없음 |
| MQTT `cmd/capture/{deviceUuid}` | JSON `command:"capture"`, `deviceUuid`, `timestamp` | 반환 메시지 없음. timestamp는 서버 로컬 `ISO_LOCAL_TIME`으로 날짜·시간대 없음. 기본 QoS 1·비동기 발행이며 장치 완료 ack 계약 없음 |
| FCM | notification `title/body`, 선택 data `originImageId`, `cloudUrl`, `deviceId` | data 값은 문자열. 공급자 응답은 내부 처리하며 앱 전달 보장은 HTTP 성공과 별개 |

FastAPI의 `object` 항목은 `id`, `points:{xtl,ytl,xbr,ybr}`, `confidence:{해충명:수치}`, `insectName`, `grow`를 수신한다. 현재 신뢰도 필터는 `confidence`의 각 항목을 사용한다. 외부 응답의 null·비정상 수치·필드 누락 검증은 충분히 고정되지 않았다.

IoT 분석의 현재 내부 입력 형태는 `deviceUuid`, `cloudUrl`, `width`, `height`, `temperature`, `humidity`다. 온도·습도는 현재 저장·분석 요청에 반영되지 않는다. DEVICE API 재개 시 이 형태를 그대로 영구 계약으로 확정하지 말고 인증 주체·사진 소유권·장치 데이터 의미를 함께 정해야 한다.
