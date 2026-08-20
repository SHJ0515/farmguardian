---
paths:
  - "**/*"
---

# 능력의 경계(Capability Boundaries)

## 사용 가능한 프로젝트 인터페이스(Available Project Interfaces)

- Gradle Wrapper command가 이 프로젝트의 공식 automation entry point입니다.
- Runtime persistence는 MySQL이고, test는 H2 MySQL compatibility mode를 사용합니다.
- Firebase Admin SDK는 `firebase.enabled=true`일 때 FCM에 사용됩니다.
- MQTT 촬영 명령(capture command)은 Spring Integration MQTT와 `gateway/MqttGateway.java`를 사용합니다.
- Image analysis는 `fastapi.*` 속성으로 설정되는 external FastAPI service가 수행합니다.
- REST Docs output은 `./gradlew.bat asciidoctor`로 생성합니다.

## 진실의 원천(Source of Truth)

- 동작이 다르면 README보다 `build.gradle`, source file, test, Spring configuration을 우선합니다.
- README는 high-level product context로 사용하고 현재 구현의 증거로 단정하지 않습니다.
- Environment-dependent behavior를 바꾸기 전에 `src/main/resources`와 `src/test/resources`를 확인합니다.
- Generated REST Docs는 docs task 실행 후 확인하고, 없는 snippet은 추측하지 않습니다.

## 추측 금지(Do Not Guess)

- 관련 code와 test를 읽지 않고 API contract, database schema, topic format, JWT claim, FCM behavior를 만들지 않습니다.
- 명시적 요청 없이 secret, credential, service-account file, environment-specific value를 수정하지 않습니다.
- 관련 configuration과 test를 확인하기 전에는 external service를 사용할 수 없다고 단정하지 않습니다.
