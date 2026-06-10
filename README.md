# FarmGuardian

스마트 농장 디바이스 관리 및 모니터링 시스템

## 프로젝트 개요

FarmGuardian은 농장 IoT 디바이스를 관리하고 실시간 알림을 제공하는 Spring Boot 기반 RESTful API 서버입니다.
농작물 모니터링 디바이스를 사용자와 연결하고, Firebase Cloud Messaging을 통해 병해충 탐지 및 농장 상태 알림을 제공합니다.

## 주요 기능

### 인증 및 사용자 관리
- 이메일 기반 회원가입/로그인
- JWT 기반 stateless 인증
- 멀티 디바이스 세션 관리 (디바이스별 개별 로그아웃 지원)
- Refresh Token을 통한 Access Token 갱신
- 소프트 삭제를 통한 안전한 회원 탈퇴

### 디바이스 관리
- 디바이스 화이트리스트 조회
- 사용자-디바이스 연결/해제
- 디바이스 위치 정보 관리 (위도/경도)
- 작물 타입 설정 (감자, 고추, 들깨, 무, 배추, 양배추, 오이, 옥수수, 콩, 파)
- 디바이스 상태 관리 (AVAILABLE, CONNECTED, INACTIVE)

### 푸시 알림
- Firebase Cloud Messaging 통합
- 사용자별 FCM 토큰 관리
- 개별 사용자 알림 발송
- 전체 사용자 브로드캐스트 알림
- 플랫폼별 토큰 관리 (Android/iOS)

## 기술 스택

### Backend
- **Java** 25
- **Spring Boot** 4.0.6
- **Spring Security** - JWT 기반 인증/인가
- **Spring Data JPA** - ORM 및 데이터 접근

### Database
- **MySQL** 8.x
- **Hibernate** - JPA 구현체

### External Services
- **Firebase Admin SDK** 9.2.0 - FCM 푸시 알림

### Security
- **JJWT** 0.11.5 - JWT 토큰 생성/검증
- **BCrypt** - 비밀번호 암호화

### Build Tool
- **Gradle** 9.x

## 프로젝트 구조

```
src/
├── main/
│   ├── java/com/farmguardian/farmguardian/
│   │   ├── config/              # 설정 클래스
│   │   │   ├── SecurityConfig.java
│   │   │   ├── FcmConfig.java
│   │   │   └── jwt/
│   │   │       ├── JwtTokenProvider.java
│   │   │       └── JwtFilter.java
│   │   ├── controller/          # REST API 컨트롤러
│   │   │   ├── AuthController.java
│   │   │   ├── DeviceController.java
│   │   │   ├── FcmController.java
│   │   │   └── UserController.java
│   │   ├── domain/              # 엔티티 도메인 모델
│   │   │   ├── User.java
│   │   │   ├── Device.java
│   │   │   ├── FcmToken.java
│   │   │   ├── RefreshToken.java
│   │   │   └── OriginImage.java
│   │   ├── dto/                 # 데이터 전송 객체
│   │   │   ├── request/
│   │   │   └── response/
│   │   ├── repository/          # JPA 레포지토리
│   │   │   ├── UserRepository.java
│   │   │   ├── DeviceRepository.java
│   │   │   ├── FcmTokenRepository.java
│   │   │   └── RefreshTokenRepository.java
│   │   └── service/             # 비즈니스 로직
│   │       ├── AuthService.java
│   │       ├── DeviceService.java
│   │       └── FcmService.java
│   └── resources/
│       ├── application.yml
│       ├── application-local.yml
│       └── farmguardian-firebase-adminsdk.json
└── test/                        # 테스트 코드
    └── java/com/farmguardian/farmguardian/
        ├── controller/
        └── service/
```

## API 엔드포인트

### 인증 (Authentication)

| Method | Endpoint | 인증 필요 | 설명 |
|--------|----------|-----------|------|
| POST | `/api/auth/signup` | ❌ | 회원가입 |
| POST | `/api/auth/signin` | ❌ | 로그인 |
| POST | `/api/auth/signout` | ✅ | 로그아웃 |
| DELETE | `/api/auth/withdraw` | ✅ | 회원 탈퇴 |

### 디바이스 (Device)

| Method | Endpoint | 인증 필요 | 설명 |
|--------|----------|-----------|------|
| GET | `/api/devices/available` | ❌ | 연결 가능한 디바이스 목록 |
| POST | `/api/devices/connect` | ✅ | 디바이스 연결 |
| GET | `/api/devices` | ✅ | 내 디바이스 목록 |
| GET | `/api/devices/{id}` | ✅ | 디바이스 상세 조회 |
| PATCH | `/api/devices/{id}` | ✅ | 디바이스 정보 수정 |
| DELETE | `/api/devices/{id}` | ✅ | 디바이스 연결 해제 |

### FCM 푸시 알림

| Method | Endpoint | 인증 필요 | 설명 |
|--------|----------|-----------|------|
| POST | `/api/fcm/token` | ✅ | FCM 토큰 등록 |
| DELETE | `/api/fcm/token` | ❌ | FCM 토큰 삭제 |
| POST | `/api/fcm/send/{userId}` | ❌ | 특정 사용자에게 알림 전송 |
| POST | `/api/fcm/broadcast` | ❌ | 전체 사용자 알림 전송 |

## 시작하기

### 사전 요구사항

- JDK 25 이상
- MySQL 8.x
- Gradle 9.x
- Firebase 프로젝트 (FCM 사용)

## 사용 예시

### 1. 회원가입
```bash
curl -X POST http://localhost:8080/api/auth/signup \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "password123"
  }'
```

### 2. 로그인
```bash
curl -X POST http://localhost:8080/api/auth/signin \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "password123",
    "clientUuid": "device-uuid-123"
  }'
```

응답:
```json
{
  "grantType": "Bearer",
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

### 3. 디바이스 연결
```bash
curl -X POST http://localhost:8080/api/devices/connect \
  -H "Authorization: Bearer {accessToken}" \
  -H "Content-Type: application/json" \
  -d '{
    "deviceUuid": "dev-001",
    "targetCrop": "POTATO",
    "latitude": 37.7749,
    "longitude": -122.4194
  }'
```

### 4. FCM 토큰 등록
```bash
curl -X POST http://localhost:8080/api/fcm/token \
  -H "Authorization: Bearer {accessToken}" \
  -H "Content-Type: application/json" \
  -d '{
    "token": "fcm-token-from-mobile-app",
    "platform": "ANDROID"
  }'
```

## 데이터베이스 스키마

### 주요 테이블

#### users
- 사용자 정보 및 인증 데이터
- 소프트 삭제 지원 (`deleted_at`)

#### devices
- IoT 디바이스 정보
- 상태: AVAILABLE, CONNECTED, INACTIVE
- 지원 작물: 10종 (감자, 고추, 들깨, 무, 배추, 양배추, 오이, 옥수수, 콩, 파)

#### refresh_tokens
- JWT Refresh Token 저장
- 디바이스별 세션 관리 (`client_uuid`)

#### fcm_tokens
- Firebase Cloud Messaging 토큰
- 플랫폼별 관리 (Android/iOS)

#### origin_images
- 디바이스에서 업로드된 원본 이미지
- 분석 결과 JSON 저장

### ERD 요약
```
User (1) ─── (N) Device
User (1) ─── (N) FcmToken
User (1) ─── (N) RefreshToken
Device (1) ─── (N) OriginImage
```

## 개발 가이드

### 코딩 컨벤션
- **Controller**: `@RestController` 사용, HTTP 상태 코드 명시
- **Service**: `@Transactional` 트랜잭션 관리
- **Repository**: Spring Data JPA 메서드 네이밍 컨벤션
- **DTO/Entity 분리**: API 응답에 Entity 직접 노출 금지

## 향후 개선 계획
- [ ] GlobalExceptionHandler 구현 (통합 예외 처리)
- [ ] Token Refresh API 구현
- [ ] `@Transactional(readOnly=true)` 최적화
- [ ] Swagger/OpenAPI 문서 자동화
- [ ] Service 레이어 단위 테스트 추가
- [ ] 이미지 업로드/분석 API 구현
- [ ] 성능 모니터링 (Actuator)
