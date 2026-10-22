# MQTT 발행 인터페이스

## 범위와 경계

MqttGateway는 topic과 payload를 `mqttOutboundChannel`로 전달하는 경계다. 장치 선택·소유권·연결 상태·야간 정책은 Service, broker 접속·QoS는 설정의 책임이다. HTTP·Repository·Firebase 의존성을 gateway에 추가하지 않는다.

## 구현 조건

- 현재 동적 topic은 `cmd/capture/{deviceUuid}`이고 payload에는 command·deviceUuid·서버 로컬 시각이 들어간다. 변경 시 장치 펌웨어 소비자의 호환성을 확인한다.
- 인터페이스는 void이며 현재 메시지 핸들러는 비동기다. 반환이나 broker 전송 결과를 장치 촬영 완료 ack로 취급하지 않는다.
- 자동 retry를 추가하면 중복 촬영이 생길 수 있다. correlation·중복 처리·장치 완료 응답 계약 없이 재시도를 단순 안정화로 추가하지 않는다.

## 검증

Service/Controller 테스트에서는 gateway를 대역으로 두고 거부 요청에서 발행이 없는지 확인한다. 실제 broker/장치 시험은 별도 시험 대상으로 분리한다. 현재 URL 테스트의 MqttService 대역은 topic·실제 전송·장치 완료를 검증하지 않으며, UserControllerIntegrationTest는 안내 응답만 확인한다.
