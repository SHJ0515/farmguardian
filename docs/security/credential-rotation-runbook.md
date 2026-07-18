# 자격증명 교체 및 설정 분리 런북

## 1. 공통 순서

1. 새 credential을 생성합니다.
2. 새 credential을 외부 secret store 또는 로컬 ignored override에 등록합니다.
3. 새 credential로 연결과 핵심 기능을 검증합니다.
4. 이전 credential을 폐기합니다.
5. 이전 credential이 포함된 JAR, 임시 파일, 로컬 backup의 폐기 시점을 기록합니다.

설정 파일의 값만 먼저 바꾸면 DB, MQTT, Firebase 연결이 끊길 수 있으므로 이 순서를 바꾸지 않습니다.

## 2. 로컬 JWT signing key

로컬에서는 다음 명령으로 512-bit random key가 포함된 ignored override를 생성합니다.

```powershell
.\scripts\New-LocalJwtSecret.ps1
```

Windows 실행 정책으로 차단되면 저장소 루트에서 다음과 같이 현재 실행에만 우회 옵션을 적용합니다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\New-LocalJwtSecret.ps1
```

기존 파일을 의도적으로 교체할 때만 다음 명령을 사용합니다.

```powershell
.\scripts\New-LocalJwtSecret.ps1 -Force
```

운영 환경에서는 이 파일을 복사하지 않고 별도의 `JWT_SECRET`을 Secret Manager에서 주입합니다.
JWT key를 교체하면 이전 key로 발급한 token은 사용할 수 없습니다. 현재는 미배포 상태이므로
별도의 dual-key 전환 없이 새 key로 시작할 수 있습니다.

## 3. MySQL credential

자동 변경하지 않습니다. 설정만 변경하면 DB 사용자와 비밀번호가 실제로 바뀌지 않기 때문입니다.

1. 기존 계정을 바로 변경하지 말고 새 application account를 생성합니다.
2. 필요한 schema의 CRUD 권한만 부여합니다. schema migration 계정은 application 계정과 분리합니다.
3. `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`에 새 값을 주입합니다.
4. 로그인, 디바이스 조회·저장, 이미지 저장을 검증합니다.
5. 다른 소비자가 기존 계정을 사용하지 않는지 확인한 뒤 기존 계정을 잠그거나 폐기합니다.

실제 SQL은 DB host 제한과 현재 grant를 확인한 뒤 DBA 또는 관리자 계정으로 실행합니다.

## 4. MQTT credential

1. broker에서 새 application credential을 생성합니다.
2. 현재 서버에 필요한 publish topic ACL만 부여합니다. 현재 코드의 촬영 topic은 `cmd/capture/+`입니다.
3. `MQTT_USERNAME`, `MQTT_PASSWORD`에 새 값을 주입합니다.
4. test device로 publish와 reconnect를 확인합니다.
5. 기존 credential을 폐기합니다.

Broker 종류와 관리 방식이 확인되지 않았으므로 provider console/API 변경은 자동화하지 않습니다.

## 5. Firebase credential

Application Default Credentials 또는 workload identity를 우선합니다. JSON key가 반드시 필요하면 다음을 지킵니다.

1. 최소 권한 service account 또는 새 key를 준비합니다.
2. JSON을 JAR 외부의 접근 통제된 경로에 저장합니다.
3. `FIREBASE_CREDENTIALS_PATH`로 절대 경로를 주입합니다.
4. test user에게만 알림을 발송해 검증합니다.
5. Google Cloud/Firebase console에서 이전 key를 폐기합니다.

`src/main/resources`에는 Firebase JSON을 다시 넣지 않습니다.

## 6. 설정 파일 역할

- `application.yml`: 모든 환경에 공통인 비밀값 없는 설정
- `application-local.yml`: local 기본값과 환경변수 계약, Firebase 기본 비활성화
- `application-prod.yml`: 운영 환경변수 계약과 안전한 운영 기본값
- `config/application-local-secret.yml`: 로컬 개발자만 사용하는 ignored secret override
- `src/test/resources/application.yml`: H2와 mock/disabled external service를 사용하는 test 전용 설정

Spring의 property 우선순위에 따라 local secret override와 환경변수가 profile 기본값을 덮어씁니다.
기본 profile에는 외부 연동 설정을 두지 않았으므로 실행할 때 `local` 또는 `prod`를 반드시 명시합니다.

운영 환경은 다음 값을 필수로 준비합니다.

- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `JWT_SECRET`
- `FASTAPI_BASE_URL`
- `MQTT_BROKER_URL`, `MQTT_CLIENT_ID`, `MQTT_USERNAME`, `MQTT_PASSWORD`
- `FIREBASE_ENABLED` (`true`이면 Application Default Credentials 또는 `FIREBASE_CREDENTIALS_PATH`도 준비)

## 7. 검증

```powershell
.\gradlew.bat test --rerun-tasks
.\gradlew.bat verifyNoSecretResources
.\gradlew.bat clean bootJar
jar tf build\libs\farmguardian-0.0.1-SNAPSHOT.jar
```

JAR에 `*firebase*.json`, `*service-account*.json`, `application-*-secret.yml`, `.env`가 있으면 실패입니다.
