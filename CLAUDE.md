# FarmGuardian

FarmGuardian은 운영진이 공급한 농장 IoT 장치와 네이티브 앱을 연결하는 Java 25·Spring Boot 4 REST API다.
취업 포트폴리오용 단일 서버로, 로컬 기능 재현과 개선 전후 성능 비교가 목표다. 실제 서비스 운영·공유 농장·외부 임의 장치 연동은 현재 목표에 포함되지 않는다.
한 계정에 여러 장치를 허용하며 장치마다 현재 소유자는 하나다. 앱·펌웨어·이미지 저장소·FastAPI 분석기는 이 저장소 밖에 있다.

## 프로젝트 경로

```text
farmguardian/
├── AGENTS.md                              ← 프로젝트 개요와 작업 시작 기준
├── CLAUDE.md                              ← 동일한 프로젝트 개요와 작업 시작 기준
├── docs/
│   ├── architecture.md                    ← 시스템 구성과 의존 방향
│   ├── business-rules.md                  ← 공급 장치·사진·알림의 업무 규칙
│   ├── security.md                        ← 인증·소유권·자격증명 정책
│   ├── standards.md                       ← 계층·변경·검증의 전체 기준
│   ├── engineering-notes.md               ← 트랜잭션·DB·연동의 함정
│   ├── operations.md                      ← 로컬 준비·실행·기능/성능 검증
│   ├── contracts.md                       ← 현재 API·외부 메시지·오류 계약
│   └── tracking/
│       ├── status.md                      ← 구현·검증·후속 범위·미정 사항
│       ├── findings.md                    ← 조건과 영향이 있는 미해결 문제
│       └── decisions/
│           ├── index.md                   ← 확정 결정과 영향 목록
│           ├── 0001-local-portfolio.md    ← 로컬 재현·성능 비교 목표
│           ├── 0002-supplied-device-ownership.md ← 공급 장치·다중 등록·단일 소유
│           ├── 0003-analysis-retention.md ← 분석·알림 실패와 데이터 보존
│           ├── 0004-refresh-transactions.md ← Refresh 이력·잠금·폐기 commit
│           ├── 0005-explicit-api-access.md ← 정확한 URL 허용과 기본 거부
│           ├── 0006-native-bearer.md      ← 네이티브 앱 인증 전제
│           ├── 0007-admin-current-role.md ← 관리자 현재 권한 확인
│           ├── 0008-admin-device-page.md  ← 관리자 장치 목록의 크기·정렬
│           ├── 0009-admin-notification.md ← 관리자 개별 알림의 기존 계약
│           └── 0010-versioned-schema.md   ← 빈 DB 기준선·불변 순차 SQL
└── src/
    └── main/
        ├── AGENTS.md                      ← 애플리케이션 시작·설정·DB 변경 경계
        └── java/com/farmguardian/farmguardian/
            ├── config/AGENTS.md           ← 보안·JWT·외부 클라이언트 조립
            ├── controller/AGENTS.md       ← HTTP·principal·Service 호출
            ├── domain/AGENTS.md           ← 상태·Entity·영속성 제약
            ├── dto/AGENTS.md              ← 요청 검증·응답 형식·매핑
            ├── exception/AGENTS.md        ← 업무 오류·HTTP 오류 변환
            ├── gateway/AGENTS.md          ← MQTT 메시징 인터페이스
            ├── repository/AGENTS.md       ← 소유권 조회·잠금·DB 접근
            └── service/AGENTS.md          ← 업무 흐름·트랜잭션·외부 연동
```

## 반드시 지킬 기준

1. Controller에 업무 로직을 넣거나 JPA Entity를 API payload로 노출하지 않는다. Service·Repository·DTO 경계를 유지한다.
2. 인증 주체와 소유권 검증을 우회하지 않는다. URL 인증이 있다고 사진·토큰·촬영의 자원별 권한까지 구현됐다고 간주하지 않는다.
3. 실제 비밀번호·JWT·서비스 계정·알림 토큰을 소스·일반 로그·문서에 노출하지 않는다. 테스트에 실제 외부 자격증명을 요구하지 않는다.
4. 적용된 DB 변경 SQL을 수정하거나 백업·대상 확인 없이 실제 DB를 변경하지 않는다. Git 복구와 DB 복구를 구분한다.
5. 요청 범위와 사용자 수정을 보존한다. 동작 변경은 회귀 검증을 갖추고, 검증하지 않은 것을 완료로 보고하지 않는다.

## 작업 전 확인

- 기본: `docs/standards.md`, `docs/engineering-notes.md`, 변경 영역의 `AGENTS.md`와 관련 소스·테스트를 읽는다. 기존 `.codex/rules`의 경로별 규칙도 적용한다.
- Git: 새 작업 브랜치는 별도 접두사 없이 `기능명-YYYYMMDD`로 이름 짓는다. 날짜는 한국 시간(Asia/Seoul)의 생성일이며 로컬 Git과 GitHub 원격에서 같은 이름을 사용한다.
- 범위·우선순위: `docs/tracking/status.md`에서 로컬 포트폴리오 목표와 미정을 확인한다. 과거 운영 배포 계획을 최신 사용자 목표로 간주하지 않는다.
- JWT·로그아웃·탈퇴·재발급: `docs/security.md`의 수명 정책과 `docs/engineering-notes.md`의 폐기 commit·잠금·flush 설명을 먼저 확인한다.
- 장치·사진·FCM·촬영: `docs/business-rules.md`의 단일 소유·실패 시 보존, `docs/security.md`의 미완료 소유권, `docs/contracts.md`의 현재 호출 형식을 확인한다.
- Entity·SQL: `src/main/AGENTS.md`와 `docs/engineering-notes.md`의 V1/V2·복구·H2 차이를 확인한 뒤 기존 데이터 영향과 검증 방법을 정한다.
- 설정·실행·성능: `docs/operations.md`에서 프로필·외부 의존성과 측정 전제를 확인한다. 부하 규모나 성능 목표를 임의 확정하지 않는다.

## 문제 발견과 보고

타 사용자 사진·장치·알림 토큰 접근, 인증 우회, 실제 비밀값 노출, Refresh 폐기 rollback, DB 손상 가능성은 영향 경로와 함께 즉시 사용자에게 알린다. 해결되지 않은 다른 문제는 조건·영향·지금 해결하지 못하는 이유·접근 방법을 `docs/tracking/findings.md`에 기록한다. 미정 업무 규칙·보안 정책을 구현 관행만으로 확정하지 않는다.

대화와 지침은 한국어 중심으로 작성한다. 최종 보고에는 변경 파일, 실행한 검증 명령과 결과, 생략 사유, 남은 위험을 포함한다. 기본 검증은 `./gradlew.bat test`, `check`, `build`이며 문서 산출물 검증은 `asciidoctor`다.
