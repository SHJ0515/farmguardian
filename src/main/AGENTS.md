# 애플리케이션 시작·설정·DB 변경

## 범위와 경계

`java/com/farmguardian/farmguardian/FarmguardianApplication.java`의 시작·JPA auditing과 `resources`의 공개 환경 설정·Flyway SQL을 담당한다. 앱·펌웨어·FastAPI 소스·저장소 업로드는 이 실행 단위에 없다. 설정 작업을 실제 로컬 DB 초기화나 공급자 credential 변경으로 확대하지 않는다.

## 유지할 조건

- 공통 `application.yml`은 비밀값 없는 공통 설정이다. local은 ignored 외부 override, prod는 필수 환경변수, test는 H2와 mock/비활성 연동으로 구분한다.
- Firebase는 명시 활성화 시만 초기화하며 ADC 또는 외부 파일을 사용한다. 실제 키·비밀번호·서비스 계정 JSON을 `resources`에 넣지 않는다.
- 공통 Flyway 기본값은 false다. 확인된 빈 DB 또는 이미 관리 중인 DB에서만 활성화하며 Hibernate validate와 함께 사용한다. 기존 DB에 자동 baseline·clean·out-of-order를 켜지 않는다.
- V1은 옛 Refresh 구조, V2는 새 Session·History 생성 후 옛 테이블 삭제다. 적용된 SQL을 수정하지 않는다. DB 복구가 Git 복구로 해결된다고 가정하지 않는다.
- 새 Refresh Entity와 SQL 제약을 함께 검증하되 기존 Entity의 DDL 메타데이터를 무관하게 일괄 삭제하지 않는다. 설명용 파일을 런타임 리소스에 추가하면 JAR에 포함될 수 있다.

## 검증

설정 변경은 `FcmConfigConditionTest`, `FlywayDefaultSafetyTest`, `FarmguardianApplicationTests` 중 해당 경계를 확인한다. SQL 변경은 `FlywayBaselineMigrationTest`, `FlywayRefreshSessionMigrationTest`, `FlywayJpaSchemaValidationTest` 및 관련 Repository 테스트로 확인한다.

```powershell
./gradlew.bat test --tests '*migration.*'
./gradlew.bat verifyNoSecretResources
```

일반 H2 create-drop은 실제 SQL 제약 검증을 대신하지 않는다. MySQL 검증이 필요하면 별도 격리 DB·실행 대상·백업을 준비하며 기존 업무 DB나 과거 초기화 도구를 자동 사용하지 않는다.
