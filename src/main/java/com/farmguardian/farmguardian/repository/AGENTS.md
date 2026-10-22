# 조회와 잠금

## 범위와 경계

Spring Data JPA 조회·저장·행 잠금과 필요한 JPQL을 담당한다. HTTP 오류 응답, JWT 파싱, 외부 전송을 수행하지 않는다. 최종 업무 판단과 트랜잭션 흐름은 Service에 둔다.

## 구현 조건

- Device의 `findByIdAndUserId`처럼 소유자를 query에 포함하는 방향을 유지한다. ID만으로 찾은 뒤 DTO를 반환하는 사진 경로는 현재 미해결 문제다.
- 사진 목록의 fetch join은 Device 매핑의 추가 조회를 줄인다. 목록/상세 매핑 변경 시 N+1과 Slice 정렬·경계 행을 함께 확인한다. 현재 사용자 사진 조회는 생성 당시 owner가 아니라 현재 device.user를 사용한다.
- Refresh 잠금은 User → Session → History 순서다. 사용자 전체 활성 세션을 잠글 때 ID 오름차순을 유지한다. lock을 얻은 조회와 메타데이터 lookup을 혼동하지 않는다.
- Session 활성 슬롯과 History 현재 슬롯의 UNIQUE를 해제하는 flush는 서비스의 INSERT 순서 보장을 위한 것이다. save/delete 순서 변경 시 실제 SQL 제약을 확인한다.
- User softDeleteById는 조건부 갱신 결과 1건을 호출자가 확인한다. soft-delete 필터가 DB UNIQUE·FK·FCM 정리를 대신하지 않는다.

## 검증

Repository query·잠금 변경은 관련 Repository 테스트와 `AuthRefreshConcurrencyIntegrationTest`를 확인한다. 새로운 owner·pagination·장치 claim query에는 타인 접근·동시 요청·연결 해제/재할당 사례가 필요하다. H2 테스트와 실제 격리 MySQL 테스트를 구분하고 업무 DB를 테스트 fixture로 사용하지 않는다.
