# 시스템 구성

FarmGuardian은 네이티브 앱과 운영진이 공급한 농장 IoT 장치를 연결하는 단일 Spring Boot 서버다. 취업 포트폴리오에서 기능 개선과 성능 차이를 재현하는 것이 목적이며, 실제 서비스 운영을 위한 분산 배포는 현재 목표가 아니다. 앱, 장치 펌웨어, 이미지 저장소, FastAPI 분석기는 이 저장소 밖에 있다.

## 실행 단위와 연결

```text
네이티브 앱 ── HTTP JSON / Bearer ──> Spring Boot REST API ── JPA ──> MySQL
     │                                    │
     └── 사진 업로드 ──> 외부 저장소        ├── HTTP /v1/infer ──> FastAPI
                         │                ├── Firebase Admin ──> FCM ──> 앱
                         └── 이미지 URL   └── MQTT publish ──> broker ──> IoT 장치
```

- Java 25·Spring Boot 4.0.6 애플리케이션 하나를 Gradle Wrapper 9.1.0으로 빌드한다. 독립 실행되는 여러 서버 모듈은 없다.
- API 서버는 이미지 바이트를 업로드하지 않는다. 클라이언트가 보낸 저장소 URL과 크기를 받아 저장하고 URL을 외부 분석기에 전달한다. 저장소 공급자·업로드 인증은 이 저장소의 구현만으로 확정할 수 없다.
- MySQL은 사용자, 장치, 사진 메타데이터·분석 JSON, 알림 토큰, 로그인 세션·갱신 이력을 보관한다. 외부 분석기나 브로커가 이 DB를 직접 갱신하는 경로는 없다.
- MQTT는 서버의 촬영 명령 발행 경계다. 수신 채널이나 장치의 촬영 완료 응답 처리기는 없다. FCM은 앱 알림 경계이며 앱 화면의 조회 데이터는 DB에 남는다.
- 자동 테스트는 H2와 mock을 사용한다. 이 결과와 실제 MySQL·브로커·분석기·Firebase 연결 검증은 별도 증거다.

## 코드 경계

Java 경로의 기준은 `src/main/java/com/farmguardian/farmguardian`이다.

| 패키지·영역 | 책임 | 의존 방향 |
| --- | --- | --- |
| `config` | 보안 필터·principal, JWT, 외부 클라이언트와 Spring 조립 | 보안 지원 코드·DTO·예외 및 외부 라이브러리 |
| `controller` | HTTP 입력, 인증 주체 전달, 응답 변환 | Service·DTO·principal |
| `service` | 업무 흐름·소유권 판단·트랜잭션 | Repository·Domain·DTO·gateway·외부 클라이언트 |
| `repository` | 조회·잠금·영속성 접근 | Domain·Spring Data JPA |
| `domain` | 사용자·장치·사진·알림 토큰·Refresh 상태 | JPA 매핑과 도메인 값; HTTP 계층에 의존하지 않음 |
| `dto` | 요청·응답 형식과 매핑 | Domain; Controller나 Repository를 호출하지 않음 |
| `exception` | 업무 오류와 공통 HTTP 오류 변환 | 오류 코드·응답 DTO |
| `gateway` | MQTT 발행 인터페이스 | 메시징 추상화; 업무 소유권을 판단하지 않음 |
| `src/main/resources` | 환경별 설정과 순차 DB 변경 SQL | 실행 환경·DB 스키마; 비밀값은 외부 주입 |

`config/auth`와 `config/jwt`는 같은 설정 영역의 인증 경계다. 테스트 디렉터리는 실행 모듈이 아니라 해당 영역의 검증 코드다.

## 대표 흐름: 모바일 사진 분석

1. 앱이 외부 저장소에 올린 사진 URL·크기를 API에 보낸다. 보안 필터가 Access 인증을 구성하고 Controller가 principal의 사용자 ID를 전달한다.
2. 분석 Service가 사용자의 직접 촬영용 가상 장치를 찾고, 사진 메타데이터 저장 트랜잭션을 완료한다.
3. DB 트랜잭션 밖에서 FastAPI에 URL을 보내 분석한다. 응답 전체를 별도 저장 트랜잭션으로 JSON에 기록한다.
4. 분석 응답에서 알림 대상을 골라 FCM 전송을 시도하고 분석 응답을 반환한다. 메타데이터 저장·분석 결과 저장·알림 전달은 단일 원자적 작업이 아니다.

IoT 분석도 같은 저장·분석 흐름을 갖지만 현재 HTTP 진입점은 닫혀 있다. 모바일 경로가 열려 있다는 이유로 장치용 인증이 구현됐다고 간주하지 않는다.

## 인증 데이터 흐름

JWT 필터는 Access를 검증해 principal을 구성하고 허용된 Controller로 요청을 전달한다. Refresh 재발급은 인증 서비스가 사용자·세션·토큰 이력 저장소의 상태를 확인한 뒤 새 토큰 또는 실패 응답을 반환하는 별도 흐름이다.

현재 분석·알림은 요청 처리 흐름에서 실행되고, MQTT 발행 핸들러는 비동기로 전송한다. 큐·outbox·캐시·분산 추적은 구현된 구성요소가 아니다. 성능 개선을 위해 이를 추가하려면 먼저 로컬 측정으로 병목과 비교 대상을 정한다.
