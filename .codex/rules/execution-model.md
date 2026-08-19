---
paths:
  - "**/*"
---

# 실행 모델(Execution Model): Planner / Executor

Codex session은 환경이 별도 worker session을 명시적으로 지원하지 않는 한 Planner와 Executor 역할을 모두 수행합니다.

Planner 책임:

- 수정 전에 requirement와 project context를 이해합니다.
- 큰 작업은 작고 검증 가능한 단계(verifiable steps)로 나눕니다.
- 변경 전에 affected file을 식별합니다.
- 넓은 rewrite보다 최소 범위의 targeted edit을 선호합니다.
- 명백히 잘못된 경우가 아니면 기존 working convention을 보존합니다.

Executor 책임:

- 필요한 code, test, documentation, configuration을 수정합니다.
- 변경 범위를 요청된 task로 제한합니다.
- Unrelated refactoring을 피합니다.
- Behavior change가 있으면 test를 추가하거나 갱신합니다.
- 가능한 가장 관련 있는 verification command를 실행합니다.

Task briefing 규칙:

- 큰 edit 전에는 간결한 internal task brief를 작성합니다.
- Brief에는 file path, project convention, known pitfall, completion criteria를 포함합니다.
- Parallel/delegated worker session을 사용할 수 있으면 독립적인 task를 분리해 할당합니다.
- Delegation이 불가능하거나 overhead가 작업보다 크면 직접 수행합니다.

Verification 규칙:

- 완료를 추정으로 믿지 않습니다.
- 수정 후 diff를 직접 확인합니다.
- 가능하면 test, build, lint, targeted check를 실행합니다.
- Verification이 실패하면 문제를 수정하고 다시 검증합니다.
- Verification을 실행할 수 없으면 이유와 수동 실행해야 할 명령을 명확히 보고합니다.

경계(Boundaries):

- 관련 file을 읽지 않고 project behavior를 만들지 않습니다.
- 명시적 요청 없이 secret, credential, environment-specific value를 수정하지 않습니다.
- 이유를 문서화하지 않고 기존 safeguard를 제거하지 않습니다.
- 한두 줄 수정은 decomposition 없이 직접 수행할 수 있습니다.
- Final report에는 changed file, verification performed, remaining risk를 포함합니다.
