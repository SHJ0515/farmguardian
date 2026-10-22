# 네이티브 앱의 직접 HTTP·Bearer 인증을 사용한다

## 배경과 결정

사용자·관리자 클라이언트는 네이티브 앱이며 WebView·브라우저·쿠키 인증을 사용하지 않기로 확정했다. 앱은 `Authorization: Bearer`로 Access를 전달한다. 현재 클라이언트 전제에서는 웹 origin 허용 목록이나 전체 OPTIONS 공개를 새로 만들지 않는다. 기본 HTTP Basic·Spring logout과 CSRF 보호는 비활성화한 현재 구성을 유지한다.

## 대안과 결과

- 쿠키 기반 세션이나 브라우저 클라이언트는 자동 전송 credential·웹 origin·CSRF 방어를 다시 설계해야 하므로 현재 구현에 암묵적으로 추가하지 않는다.
- STATELESS 설정만으로 CSRF가 안전한 것은 아니다. 쿠키·웹 클라이언트를 도입하면 현재 비활성화의 전제가 깨진다.
- 공개 경로에 잘못된 Access 헤더가 있어도 인증 객체 없이 공개 업무를 계속 처리한다. 보호 경로의 인증 실패와 공개 업무의 credential 검증을 구분한다.
