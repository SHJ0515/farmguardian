---
paths:
  - "src/test/java/com/farmguardian/farmguardian/**"
  - "src/test/resources/**"
  - "src/main/java/com/farmguardian/farmguardian/**"
  - "build.gradle"
---

# 테스트 규칙(Testing Rules)

기능 변경(feature change)을 할 때는 구현 전에 test를 작성하거나 기존 test를 확장합니다.

## 커버리지 기대치(Coverage Expectations)

- Controller 변경은 `src/test/java/.../controller`의 integration test를 추가하거나 갱신합니다.
- Service 변경은 normal case와 중요한 exception case를 함께 검증합니다.
- Repository query behavior가 위험 지점이면 `src/test/java/.../repository`에서 검증합니다.
- Security, authentication, token, FCM, MQTT, device ownership 변경에는 regression coverage가 필요합니다.

## 외부 서비스(External Services)

- Test가 live Firebase, MQTT broker, MySQL instance, FastAPI service, cloud credential에 의존하지 않게 합니다.
- Mock, gateway boundary, disabled bean, local endpoint, test profile을 우선 사용합니다.
- Test는 `src/test/resources/application.yml`을 통해 H2 MySQL compatibility mode로 설정되어 있습니다.
- Verification 실패가 environment variable, database state, Firebase credential, 외부 의존성 때문이면 그 원인을 명확히 보고합니다.

## 검증 명령(Verification Commands)

먼저 가장 좁고 유용한 verification을 실행하고, 위험도가 크면 범위를 넓힙니다.

```bash
./gradlew.bat test
./gradlew.bat check
./gradlew.bat build
```

REST Docs 산출물이 필요하면 아래 명령을 사용합니다.

```bash
./gradlew.bat asciidoctor
```

Generated API documentation을 추측해서 만들지 않습니다. `asciidoctor` 실행 후
`build/generated-snippets`와 `build/docs/asciidoc`을 기준으로 확인합니다.
