---
paths:
  - "**/*"
---

# 시행착오 기록(Lessons Learned)

- Documentation change는 요청된 file 범위로 제한합니다.
- README는 implementation보다 늦을 수 있습니다. 현재 동작은 `rg`, source file, test, `build.gradle`을 기준으로 확인합니다.
- 이 프로젝트의 Spring Boot 4.x 구성은 `spring-boot-starter-webmvc`와 `spring-boot-starter-webmvc-test`를 사용합니다.
- JJWT는 API, implementation, Jackson runtime dependency로 분리되어 있습니다. Version upgrade가 필요하지 않으면 이 split을 유지합니다.
- Test resources는 Firebase를 비활성화하고 MQTT/FastAPI를 local endpoint로 둡니다. Test가 live cloud credential이나 broker에 의존하지 않게 합니다.
- Firebase service-account JSON과 local application profile은 sensitive file입니다. 새 secret을 commit하지 않습니다.
- 루트에는 Windows reserved name과 충돌하는 local artifact `nul`이 있을 수 있으며, search failure를 막기 위해 ignore합니다.
- Edit 전후로 `git status --short`를 확인하고, 관련 없는 user change를 되돌리지 않습니다.
