# 상태와 영속성 모델

## 범위와 경계

User, Device, OriginImage, FcmToken, RefreshSession, RefreshTokenHistory 및 값 enum·감사 시각을 담당한다. HTTP, principal 해석, 외부 네트워크 호출은 소유하지 않는다. Entity에 Controller·외부 client 의존성을 넣지 않는다.

## 구현 조건

- 상태 변경은 기존 도메인 메서드로 표현한다. Device 연결 해제는 현재 사용자·별칭·작물·좌표를 함께 비운다. `INACTIVE` 전이는 확정된 생애주기로 추측하지 않는다.
- 물리 장치 한 대의 현재 소유자는 하나이며 계정당 여러 대를 허용한다. 직접 촬영 가상 장치는 현재 종류 필드가 없어 일반 장치와 섞이는 문제가 있다.
- OriginImage에는 현재 생성 당시 사용자 참조가 없다. owner 추가 시 기존 행 근거·nullable 전환·FK·탈퇴 보존을 함께 정하고 현재 장치 소유자로 무조건 채우지 않는다.
- RefreshSession은 활성 슬롯·폐기 시각·재사용 시각의 일관성을, History는 현재 슬롯·교체 jti·rotation/재사용 시각의 일관성을 유지한다. 이미 폐기/교체된 상태를 정상 신규 상태로 되돌리지 않는다.
- 새 Refresh 모델의 CHECK·UNIQUE·인덱스는 SQL이 관리한다. `@Version`과 ORM 쓰기 의미가 있는 매핑은 단순 DDL 중복으로 삭제하지 않는다.
- soft delete는 참조 데이터·DB UNIQUE 값을 자동 정리하지 않는다. User 이메일 재사용과 Device/Image/FCM 수명은 별도 결정 대상이다.

## 검증

Refresh 상태·시각·슬롯 변경은 Repository 스키마 테스트와 세션 동시성/rollback 테스트를 함께 확인한다. 사진 owner·장치 종류·탈퇴 변경은 재할당·다른 계정·가상 장치 재등록·기존 행의 경계값을 테스트해야 한다. H2 자동 DDL 결과만으로 Flyway CHECK가 존재한다고 판단하지 않는다.
