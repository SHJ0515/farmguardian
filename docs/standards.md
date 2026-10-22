# 개발 기준

## 계층과 계약

- Controller는 HTTP·검증·principal 전달만 담당하고 업무 로직이나 Repository 호출을 추가하지 않는다. 소유권 판단과 트랜잭션은 Service에 둔다.
- Repository는 Spring Data JPA를 우선한다. 사용자 소유 데이터의 조회 조건을 넓히거나 Service 검증을 우회하는 변경에는 타 사용자 접근 회귀 검증이 필요하다.
- JPA Entity를 API 요청·응답으로 직접 노출하지 않는다. 요청은 `dto/request`, 응답은 `dto/response`에 두고 기존 Service·DTO 매핑 경계를 따른다.
- 인증 주체는 Spring Security·JWT·principal을 통해 전달한다. 요청의 사용자 ID를 인증된 사용자라고 간주하지 않는다. 현재 남은 예외 경로를 새 API의 패턴으로 복제하지 않는다.
- JWT 코드는 `config/jwt`, principal 지원은 `config/auth`에 둔다. 외부 호출은 Controller에 두지 않고 Service 또는 gateway 경계를 사용한다.
- 업무 예외는 `BusinessException`·`ErrorCode`와 `exception/{domain}`을 사용한다. 필터와 MVC의 오류 변환에서 기존 응답 DTO를 재사용한다. 성공 응답 구조와 오류 코드를 임의로 전면 변경하지 않는다.
- 쓰기 Service에는 명확한 트랜잭션 경계를 둔다. 읽기 전용 처리에는 가능한 경우 `@Transactional(readOnly = true)`를 사용한다. 외부 호출을 감싸는 범위를 바꿀 때 저장·실패 경계를 함께 검증한다.

## 의존성과 설정

- Java toolchain은 25이며 공식 명령 진입점은 Gradle Wrapper다. 별도 설치 Gradle의 버전으로 빌드 기준을 바꾸지 않는다.
- Spring Boot 4의 WebMVC starter/test starter와 현재 JJWT API·implementation·Jackson runtime 분리를 유지한다. 업그레이드 요청이 없으면 문서 정리나 작은 기능 수정에 버전 변경을 끼워 넣지 않는다.
- 공통·local·prod·test 설정의 역할을 유지한다. 환경 의존 동작을 바꾸기 전 main과 test 설정을 함께 확인한다.
- 실제 비밀값은 소스, `src/main/resources`, 테스트, 문서에 넣지 않는다. 로컬 override·서비스 계정 파일·환경별 실제 값 변경은 요청된 범위 안에서만 수행한다.
- `verifyNoSecretResources`는 `processResources`와 `check`에 연결된 검증 조건이다. 통과를 위해 검출 패턴을 느슨하게 하거나 검사 자체를 제거하지 않는다.
- 이미 적용한 Flyway SQL은 수정하지 않고 새 버전을 추가한다. Flyway와 Hibernate `update/create/create-drop`을 같은 DB에서 병행하지 않는다. DB 스키마 권한·실행 대상·백업을 확인하지 않은 채 migration을 일반 테스트처럼 실행하지 않는다.

## 검증 조건

- 동작 변경은 가능한 한 실패 조건을 테스트로 먼저 고정한다. Controller는 통합 테스트, Service는 정상·주요 실패, Repository는 위험한 query·잠금 동작을 검증한다.
- 인증·토큰·FCM·MQTT·소유권 변경에는 회귀 검증이 필요하다. Refresh 변경은 동시 요청·재사용 탐지·폐기 commit·실패 rollback을 함께 확인한다.
- 자동 테스트가 실제 Firebase, MQTT broker, FastAPI, 운영 MySQL 또는 cloud credential을 요구하게 만들지 않는다. H2/mock 검증과 별도 격리 MySQL 검증의 결과를 혼동하지 않는다.
- 먼저 가장 좁고 유효한 테스트를 실행한다. 기능·보안 변경의 최종 확인은 `./gradlew.bat test`, `./gradlew.bat check`, `./gradlew.bat build`로 수행하며, 환경 실패는 원인과 미검증 범위를 보고한다.
- REST Docs 산출물은 실제 `./gradlew.bat asciidoctor` 실행과 생성 파일로 확인한다. 없는 snippet이나 렌더 결과를 만들었다고 주장하지 않는다.
- 문서만 바뀌면 내용·경로·diff 검증을 우선한다. 테스트 재실행을 생략했다면 이유를 기록하고 이전 통과 결과를 이번 실행으로 표시하지 않는다.
- 성능 수치를 개선 근거로 쓸 때 동일 환경·데이터·요청 혼합과 실행 조건을 남긴다. 측정하지 않은 처리량·동시 사용자 수·응답 시간을 보장 수치로 작성하지 않는다.

## 변경과 협업

- 요청 범위 밖 리팩토링, 일괄 formatter, 사용자 수정 되돌리기를 하지 않는다. 변경 전후 `git status --short`와 diff를 확인한다.
- 새 작업 브랜치 이름은 `<기능명>-<YYYYMMDD>` 형식으로 작성하며 별도 접두사를 붙이지 않는다. 기능명은 작업 내용을 나타내는 영문 소문자·숫자·하이픈으로 작성하고, 날짜는 한국 시간(Asia/Seoul)의 브랜치 생성일을 사용한다. 예: `image-ownership-20261022`, `documentation-migration-20261022`.
- 로컬 Git과 GitHub 원격 브랜치는 같은 이름을 사용한다. 같은 이름이 이미 있으면 기존 작업인지 확인하고, 별도 작업이면 기능명을 더 구체화해 구분한다. 이 규칙은 새 브랜치에 적용하며 기존 브랜치를 일괄 개명하지 않는다.
- commit은 Conventional Commit 형식을 따르며, 호환성이 실제로 깨질 때만 `!`와 변경 내용을 명시한다.
- commit·push·PR·병합·실제 DB 변경은 각각 사용자가 승인한 작업 범위에 한정한다. 과거의 일회성 실행 승인을 다음 실행에 재사용하지 않는다. 게시된 이력의 임의 amend·rebase·강제 push를 하지 않는다.
- 기존 로컬 기록·백업·비밀 설정을 강제 add하지 않는다. 새 기준 문서의 추적 허용이 기존 `docs` 하위 기록 전체의 공개 허용을 뜻하지 않는다.
- 사용자 대화와 프로젝트 지침은 한국어 중심으로 작성하며 필요한 기술 용어를 병기한다. 큰 변경은 영향 파일·동작 영향·검증 방법을 설명하고, 최종 보고에는 변경 파일·검증 명령·결과·생략 사유·남은 위험을 포함한다.
- 실제 소유권 우회, 인증 우회, 비밀값 노출 또는 DB 손상 가능성을 발견하면 영향 경로를 사용자에게 알린다. 범위를 넘는 코드·DB 수정을 문서 정리와 함께 실행하지 않는다.
