# 로컬 실행과 검증

FarmGuardian의 실행 목표는 포트폴리오용 기능 재현과 성능 비교다. 실제 서비스 운영 환경은 구축 대상이 아니며, production 설정 파일의 존재가 배포 절차의 완성을 뜻하지 않는다.

## 준비물과 기본 검증

저장소 루트에서 JDK 25와 PowerShell을 사용한다. Wrapper는 Gradle 9.1.0을 내려받으므로 최초 실행은 의존성 다운로드가 필요하다. 이미 캐시된 환경만 `--offline`을 사용할 수 있다. macOS/Linux에서는 `gradlew.bat` 대신 `./gradlew`를 사용한다.

```powershell
java -version
./gradlew.bat --version
./gradlew.bat test
./gradlew.bat check
./gradlew.bat build
```

기본 테스트는 H2 메모리 DB, Firebase 비활성화, 외부 호출 mock/local 설정을 사용한다. 실제 DB 비밀번호나 cloud key를 준비하기 전에 실행할 수 있다. XML은 `build/test-results/test`, HTML은 `build/reports/tests/test/index.html`에 생성된다. 이 결과로 실제 MySQL·FastAPI·MQTT·FCM 동작을 검증했다고 주장하지 않는다.

## 로컬 애플리케이션 시작 순서

1. MySQL 8.x와 접근 가능한 로컬 DB를 준비한다. 스키마 생성은 빈 DB에 V1→V2를 적용하는 방식이다. 기존 데이터가 있는 DB에는 자동 baseline이나 재생성을 시도하지 않는다.
2. 로컬 JWT override가 없을 때만 키를 생성한다. 기존 파일이 있으면 그대로 사용하고 내용이나 비밀값을 출력하지 않는다.

```powershell
if (-not (Test-Path -LiteralPath ./config/application-local-secret.yml)) {
    ./scripts/New-LocalJwtSecret.ps1
}
```

3. `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를 준비한 로컬 DB에 맞게 주입한다. `config/application-local-secret.example.yml`은 값의 위치를 보여주는 예시일 뿐 실제 자격증명이 아니다. 실제 값은 ignored override 또는 프로세스 환경변수로 전달한다.
4. 사진 분석에는 별도 FastAPI 실행과 접근 가능한 이미지 저장소 URL, 실제 촬영에는 로컬 MQTT broker·시험 장치가 필요하다. 이 저장소가 분석기·앱·펌웨어를 실행해 주지는 않는다. Firebase 없이도 서버 검증을 시작할 수 있지만 실제 푸시 전달 시험은 별도 identity와 시험 앱이 필요하다.
5. 이미 버전 관리가 도입된 DB이고 새 SQL 적용 범위가 확인됐다면 아래와 같이 시작한다. 빈 DB의 최초 도입에도 같은 설정을 쓰되 DDL 가능한 계정과 실행 대상 확인을 먼저 마친다.

```powershell
$env:SPRING_PROFILES_ACTIVE = 'local'
$env:FLYWAY_ENABLED = 'true'
$env:JPA_DDL_AUTO = 'validate'
./gradlew.bat bootRun
```

기존 DB가 최신 스키마라서 SQL 적용을 의도적으로 하지 않는 실행은 `FLYWAY_ENABLED=false`로 시작할 수 있다. 이때도 `validate`가 통과해야 한다. main 공통 설정의 기본값은 false이므로 이후 SQL을 적용할 계획이라면 명시적으로 활성화해야 한다. Hibernate `update/create/create-drop`을 문제 해결용 대체 수단으로 쓰지 않는다.

### 새 데모 DB가 필요한 경우

MySQL CLI와 로컬 스키마 생성 권한이 있는 계정을 준비한 뒤, **사용하지 않는 새 이름**으로 DB를 만든다. 아래 `farmguardian_demo`는 예시 이름이다. 동일 이름이 존재하면 삭제하거나 덮어쓰지 말고 새 이름을 선택한다. `--password`는 프롬프트를 사용하므로 비밀번호를 명령 이력에 넣지 않는다.

```powershell
mysql --host=127.0.0.1 --port=3306 --user=$env:DB_USERNAME --password --execute="CREATE DATABASE farmguardian_demo CHARACTER SET utf8mb4;"
$env:DB_URL = 'jdbc:mysql://127.0.0.1:3306/farmguardian_demo'
```

그다음 local 프로필·Flyway 활성·Hibernate validate로 시작한다. V1/V2에는 사용자·물리 장치 seed가 없다. 첫 시작만으로 등록 가능한 물리 장치가 생기는 것은 아니며, 공급 장치의 시험 데이터 입력 경로는 아직 별도 준비가 필요하다. DB를 되돌릴 때는 Git 복구와 별도로 데이터 백업·적용 SQL·복구 대상을 확인한다.

## 환경값의 의미

| 값 | local 기본/용도 | 유의점 |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `local` 지정 | 기본 프로필만으로 외부 연결 설정이 완성되지 않음 |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | localhost의 `farmguardian`, 사용자 `farmguardian`, 빈 비밀번호 기본 | 실제 로컬 DB에 맞춰 주입; prod는 세 값 필수 |
| `FLYWAY_ENABLED` | false | 확인된 빈 DB 최초 도입 또는 관리 중인 DB의 다음 버전에만 true |
| `JPA_DDL_AUTO` | local은 validate | prod는 validate 고정; SQL 적용과 자동 DDL을 병행하지 않음 |
| `JWT_SECRET` | main 기본 없음 | local ignored override 또는 환경값 필요; 테스트 fixture 재사용 금지 |
| `JWT_ISSUER` | `farmguardian-local` | prod 필수; 발급·검증 환경 구분 |
| `JWT_ACCESS_TOKEN_AUDIENCE`, `JWT_REFRESH_TOKEN_AUDIENCE` | `farmguardian-api-local`, `farmguardian-refresh-local` | prod 필수; 용도별 audience 사용 |
| `JWT_ACCESS_TOKEN_EXPIRATION`, `JWT_REFRESH_TOKEN_EXPIRATION` | 1800000 / 604800000 밀리초 | 각각 30분/7일 기본; 실제 private 값과 동일하다고 단정하지 않음 |
| `FIREBASE_ENABLED`, `FIREBASE_CREDENTIALS_PATH` | false, 빈 경로 | true일 때 경로가 없으면 ADC, 있으면 JAR 외부 파일 |
| `FASTAPI_BASE_URL` | `http://localhost:8000` | 실제 분석 서버의 기본 URL; `/v1/infer`가 추가됨 |
| `FASTAPI_CONNECT_TIMEOUT`, `FASTAPI_READ_TIMEOUT` | 5 / 10초 | 응답 대기 제한이지 재시도 횟수가 아님 |
| `MQTT_BROKER_URL`, `MQTT_CLIENT_ID` | `tcp://localhost:1883`, `farmguardian-server-local` | 여러 서버를 동시에 시험하면 client ID 충돌 방지 |
| `MQTT_USERNAME`, `MQTT_PASSWORD` | 빈 값 | broker 설정에 맞춰 주입; prod 필수 |
| `MQTT_QOS` | 1 | broker 전송 수준이며 장치 촬영 완료 보장 아님 |
| `MQTT_CONNECTION_TIMEOUT`, `MQTT_KEEP_ALIVE_INTERVAL` | 30 / 60초 | 연결과 유지 설정 |
| `MQTT_AUTOMATIC_RECONNECT` | true | 테스트는 false; 실제 재연결 시험과 단위 테스트 구분 |
| `SERVER_PORT` | 8080 | 다중 인스턴스 시험 시 포트 충돌 방지 |
| `JPA_SHOW_SQL`, `HIBERNATE_SQL_LOG_LEVEL` | false / INFO | prod SQL 수준은 WARN; 시험 로그에 민감값 유입 주의 |

## 기능 검증 순서

1. `GET /`와 가입 → 로그인 → 재발급 → 로그아웃을 확인한다. PUBLIC 진입 허용과 업무 credential 검증을 구분한다.
2. 서로 다른 두 계정·여러 공급 장치의 시험 데이터를 준비하고 자기 장치 조회·수정·해제와 타 계정 접근 거부를 비교한다. 연결 경쟁·사진 재할당 보호는 아직 보완 대상이다.
3. 외부 분석기의 시험 응답으로 모바일 분석 성공, 분석 timeout, 빈 탐지, 기준값 인근 신뢰도를 확인한다. 실패 후에도 사진 메타데이터가 남고, 푸시 실패 후에도 분석 결과를 조회할 수 있는지 확인한다.
4. MQTT는 명령 발행과 장치 촬영 완료를 따로 관찰한다. 현재 촬영 URL의 소유권 결함을 먼저 보완해야 정상 시나리오만으로 접근 통제가 검증됐다고 주장할 수 있다.
5. 실제 FCM 전달은 사용자가 선택한 시험 앱/계정에서만 확인한다. HTTP 성공만으로 수신을 판단하지 않는다.

IoT 분석·관리자 API는 현재 차단 중이다. 전체 앱→장치→분석 시나리오를 실행하려면 인증·소유권 보완과 구체적인 시험 구성을 먼저 완료해야 한다. 시험 편의를 위해 현재 URL 차단을 일괄 해제하지 않는다.

## 성능 비교 준비

부하 규모·목표 수치·부하 도구는 미정이다. 먼저 비교할 기능 하나와 병목 가설을 정하고, CPU·메모리·JDK·DB 버전, 계정/장치/사진 수, 동시 요청 수, 요청 혼합, 준비 시간·측정 시간을 고정한다. 조회·분석·촬영은 외부 지연과 부작용이 달라 별도 시나리오로 측정한다.

기준 버전과 개선 버전에서 처리량·p50/p95/p99·오류율·DB 쿼리 수·CPU/메모리를 같은 조건으로 기록한다. 외부 분석기는 지연을 고정한 대역과 실제 연동 결과를 분리한다. 부하 중 실제 푸시·장치 명령을 반복 발송하지 않도록 시험 대상을 통제한다. 아직 부하 스크립트·고정 데이터 생성기·성능 결과가 준비된 상태는 아니다.

## 자격증명 교체와 산출물 확인

새 자격증명 준비 → 외부 주입 → 시험 연결·핵심 동작 검증 → 이전 값 폐기 순서를 사용한다. 설정 외부화와 공급자 측 폐기는 다른 작업이다. 운영용 계정 교체는 실제 서비스 운영이 현재 목표가 아니므로 완료를 주장하지 않는다.

```powershell
./gradlew.bat verifyNoSecretResources
./gradlew.bat asciidoctor
```

REST Docs는 `build/generated-snippets`와 `build/docs/asciidoc`의 실제 내용을 확인한다. 현재 Gradle 작업과 일부 테스트 문서화는 있지만 전체 API를 묶은 AsciiDoc 소스는 없다. 명령 성공만으로 최종 API 문서가 완성됐다고 판단하지 않는다.
