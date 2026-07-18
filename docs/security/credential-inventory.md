# FarmGuardian 자격증명 인벤토리(Credential Inventory)

이 문서는 자격증명의 **값이 아니라 종류, 사용처, 목표 보관 위치와 교체 상태**만 기록합니다.
비밀번호, token, private key, service-account JSON 내용은 이 문서와 Git 이력에 남기지 않습니다.

## 인벤토리

| 자격증명 | 사용 컴포넌트 | 기존 발견 위치 | 목표 보관 위치 | 현재 상태 | 다음 조치 |
| --- | --- | --- | --- | --- | --- |
| JWT signing key | `JwtTokenProvider` | local/prod profile의 literal 값 | 운영 Secret Manager 또는 `JWT_SECRET`; 로컬 ignored override | 저장소 설정에서 제거, 로컬 512-bit 새 키 생성 완료 | 운영 환경 생성 시 별도 새 키 주입 |
| MySQL application credential | Spring datasource | local/prod profile의 literal 값 | `DB_USERNAME`, `DB_PASSWORD` 또는 Secret Manager | 설정 외부화, 실제 DB 계정 교체 미실행 | 새 최소 권한 계정 검증 후 기존 계정 폐기 |
| MQTT credential | `MqttConfig` | local/prod profile의 literal 값 | `MQTT_USERNAME`, `MQTT_PASSWORD` 또는 broker secret store | 설정 외부화, broker credential 교체 미실행 | 새 계정과 topic ACL 검증 후 기존 계정 폐기 |
| Firebase service-account credential | `FcmConfig` | `src/main/resources`의 JSON 파일 | Application Default Credentials 또는 JAR 외부 파일 | classpath 제거 및 외부 로딩으로 전환 | 새 identity/key 검증 후 기존 key 폐기 |
| FastAPI service credential | `RestClientConfig` | 현재 별도 credential 설정 없음 | 향후 mTLS/API credential을 Secret Manager에 보관 | 미도입 | 서비스 인증 설계 단계에서 추가 |

## 관리 원칙

- secret 값은 `src/main/resources`, Java source, test source, README에 저장하지 않습니다.
- 운영 secret은 환경변수보다 Secret Manager/workload identity를 우선합니다.
- 로컬 secret 파일은 `config/application-local-secret.yml`만 사용하며 Git에서 제외합니다.
- 자격증명 교체 시 새 credential 검증 전 기존 credential을 폐기하지 않습니다.
- 교체일, 담당자, 폐기 여부는 Git 문서가 아니라 접근 통제된 운영 기록에 남깁니다.
- 로컬 복구 snapshot에는 이전 credential이 포함될 수 있으므로 검증 후 폐기합니다.
