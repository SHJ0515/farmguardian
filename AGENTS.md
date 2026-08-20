# 프로젝트: farmguardian

FarmGuardian은 농장 IoT 디바이스 관리, 이미지 분석(image analysis) 흐름,
JWT 인증(authentication), Firebase Cloud Messaging(FCM), MQTT 촬영 명령,
MySQL 영속성(persistence)을 담당하는 Spring Boot REST API 서버입니다.

이 파일은 저장소 로컬 Codex 지침(repository-local Codex instructions)입니다.
사용자/개발자/전역 지침도 적용되지만, 이 파일은 이 프로젝트의 기본 규칙을 정의합니다.

## 프로젝트 지도(Project Map)

- `src/main/java/com/farmguardian/farmguardian/FarmguardianApplication.java`: Spring Boot 진입점(entry point).
- `src/main/java/com/farmguardian/farmguardian/config`: Spring 설정(configuration).
- `src/main/java/com/farmguardian/farmguardian/config/auth`: 인증 Principal(authenticated principal) 지원.
- `src/main/java/com/farmguardian/farmguardian/config/jwt`: JWT provider와 filter.
- `src/main/java/com/farmguardian/farmguardian/controller`: REST controller.
- `src/main/java/com/farmguardian/farmguardian/domain`: JPA entity와 domain model.
- `src/main/java/com/farmguardian/farmguardian/dto/request`: 요청 DTO(request DTO).
- `src/main/java/com/farmguardian/farmguardian/dto/response`: 응답 DTO(response DTO).
- `src/main/java/com/farmguardian/farmguardian/exception`: global/domain exception.
- `src/main/java/com/farmguardian/farmguardian/gateway`: MQTT 같은 integration gateway interface.
- `src/main/java/com/farmguardian/farmguardian/repository`: Spring Data JPA repository.
- `src/main/java/com/farmguardian/farmguardian/service`: application service와 business logic.
- `src/main/resources`: runtime Spring configuration, static/template resource.
- `src/test/java/com/farmguardian/farmguardian/controller`: controller integration test.
- `src/test/java/com/farmguardian/farmguardian/service`: service test.
- `src/test/java/com/farmguardian/farmguardian/repository`: repository test.
- `src/test/resources`: H2와 local/mock external endpoint를 사용하는 test configuration.
- `build/generated-snippets`, `build/docs/asciidoc`: `asciidoctor` 실행 후 생성되는 Spring REST Docs 산출물.

이 저장소에는 FastAPI 소스가 없습니다. FastAPI는 `fastapi.*` 속성으로 설정되는
외부 이미지 분석 서비스(external image analysis service)입니다.

## 기술 스택(Tech Stack)

- Java 25, Spring Boot 4.0.x, Gradle Wrapper.
- Spring WebMVC, Spring Security, validation, JJWT 기반 JWT.
- Spring Data JPA / Hibernate, runtime MySQL, test H2.
- Firebase Admin SDK / FCM.
- Spring Integration MQTT, Eclipse Paho.
- JUnit 5, Spring Boot Test, Spring Security Test, Spring REST Docs.
- Lombok, dev/test용 p6spy, Actuator.

## 주요 명령(Key Commands)

이 Windows 워크스페이스에서는 아래 명령을 사용합니다.

```bash
./gradlew.bat test
./gradlew.bat check
./gradlew.bat build
./gradlew.bat bootRun
./gradlew.bat asciidoctor
```

macOS/Linux에서는 `./gradlew`를 사용합니다.

## 핵심 규칙(Core Rules)

- Controller-Service-Repository 계층(layering)을 유지하고 controller에 business logic을 두지 않습니다.
- JPA entity를 API request/response payload로 직접 노출하지 않습니다.
- 인증/인가(authentication/authorization)는 Spring Security, JWT component, authenticated principal을 통해 처리합니다.
- 예외 응답은 `GlobalExceptionHandler`, `ErrorCode`, domain exception으로 일관되게 처리합니다.
- Spring Data JPA repository를 우선 사용하고, 필요한 경우에만 custom query strategy를 추가합니다.
- 동작 변경(behavior change)이 있으면 가능한 한 먼저 test를 작성하거나 갱신합니다.
- 변경 범위는 요청된 작업에 한정하고 unrelated refactoring을 피합니다.
- 사용자 대화는 별도 요청이 없으면 한국어로 합니다. repository instruction 문서는 한글 중심에 핵심 기술 용어를 영어로 병기합니다.
- 최종 보고에는 변경 파일, verification command, 결과, 검증 생략 사유를 포함합니다.

## 세부 규칙 참조(Rule References)

- Architecture rules: `.codex/rules/architecture.md`
- Testing rules: `.codex/rules/testing.md`
- Capability boundaries: `.codex/rules/capability-boundaries.md`
- Lessons learned: `.codex/rules/lessons-learned.md`
- Execution model: `.codex/rules/execution-model.md`
