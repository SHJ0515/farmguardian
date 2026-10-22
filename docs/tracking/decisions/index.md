# 결정 목록

| 기록 파일 | 결정 | 이후 작업에 미치는 영향 |
| --- | --- | --- |
| `0001-local-portfolio.md` | 실제 운영 대신 로컬 재현·성능 비교를 갖춘 취업 포트폴리오 | 과거 production 준비 전체를 필수 완료 조건으로 강제하지 않음 |
| `0002-supplied-device-ownership.md` | 운영진 공급 장치, 계정당 여러 대·장치당 현재 소유자 하나 | 외부 임의 장치·구성원 공유는 현재 범위 밖; 대수 1개 제한을 추가하지 않음 |
| `0003-analysis-retention.md` | 분석/푸시 실패와 사진·결과 보존 분리 | 외부 실패 때문에 이미 저장한 메타데이터·분석 결과를 rollback하지 않음 |
| `0004-refresh-transactions.md` | Refresh 세션·해시 이력과 폐기 commit 경계 | 재사용 탐지 실패가 폐기를 rollback하지 않도록 유지 |
| `0005-explicit-api-access.md` | 정확한 method/path 허용과 기본 거부 | 새 경로는 별도 접근 정책이 필요; 관리자·DEVICE 미구현 경로의 자동 개방 없음 |
| `0006-native-bearer.md` | 네이티브 직접 HTTP·Bearer 전용 | 쿠키/웹 도입 전 CORS·CSRF 전제 재검토 |
| `0007-admin-current-role.md` | 관리자 수동 지정, JWT와 현재 DB 권한 동시 검사 | DB 역할만 바꾸거나 오래된 토큰만으로 관리자 기능 허용 불가 |
| `0008-admin-device-page.md` | AVAILABLE 관리자 목록 20개 고정·ID ASC | 일반 등록 흐름 대체 불가; Page JSON·위반 입력 처리는 미정 |
| `0009-admin-notification.md` | 개별 알림의 기존 입력·성공 응답·재요청 동작 유지 | 새 제한·자동 retry·중복 제거를 승인 없이 결합하지 않음 |
| `0010-versioned-schema.md` | 빈 DB 기준선과 불변 순차 SQL | 기존 데이터 자동 baseline 금지, 코드 복구와 DB 복구 분리 |

신뢰도 상향의 정확한 경계, Access 즉시 폐기, 사진 과거 소유자 처리, 탈퇴 수명, 장치 인증 방식, 감사 보존, 성능 부하 규모는 확정된 결정이 아니다. 기존 구현값이나 제안만으로 새 결정 기록을 만들지 않는다.
