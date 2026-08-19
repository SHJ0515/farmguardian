---
paths:
  - "src/main/java/com/farmguardian/farmguardian/**"
  - "src/test/java/com/farmguardian/farmguardian/**"
---

# 아키텍처 규칙(Architecture Rules)

FarmGuardian은 Controller-Service-Repository architecture를 따릅니다.

## 계층(Layering)

- Controller는 `@RestController` 진입점(entry point)이며 REST 처리에 집중합니다.
- Service는 application behavior, authorization decision, transaction boundary를 담당합니다.
- Repository는 복잡한 조회가 실제로 필요할 때를 제외하고 Spring Data JPA interface를 사용합니다.
- 외부 연동(external integration)은 controller가 아니라 service 또는 gateway-style component를 통해 접근합니다.

## DTO와 Entity 경계(DTO and Entity Boundaries)

- JPA entity를 request/response API로 직접 노출하지 않습니다.
- Request DTO는 `dto/request` 아래에 둡니다.
- Response DTO는 `dto/response` 아래에 둡니다.
- DTO와 entity mapping은 기존 코드가 사용하는 service 또는 mapper boundary에서 처리합니다.

## 보안(Security)

- JWT 코드는 `config/jwt` 아래에 둡니다.
- Authenticated principal 지원 코드는 `config/auth` 아래에 둡니다.
- Controller에서 ownership, token, device authorization check를 우회하지 않습니다.
- Security, token, device ownership 변경에는 regression test가 필요합니다.

## 예외와 트랜잭션(Exceptions and Transactions)

- Error response는 `GlobalExceptionHandler`, `ErrorCode`, domain-specific custom exception으로 일관되게 처리합니다.
- 새 domain exception은 `exception/{domain}` 아래에 둡니다.
- Service write operation에는 명확한 transaction boundary를 둡니다.
- Read-only service operation에는 가능한 경우 `@Transactional(readOnly = true)`를 사용합니다.
