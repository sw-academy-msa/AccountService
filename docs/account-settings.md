# 내 정보와 설정 API

모든 API는 로그인 응답의 Access JWT를 `Authorization: Bearer <accessToken>` 헤더로 전달합니다.
회원 ID는 JWT에서만 결정하며 Refresh 쿠키만으로 호출할 수 없습니다. 이 API에는 CSRF 헤더가 필요하지 않습니다.
정지·탈퇴한 계정은 기존 JWT가 남아 있어도 접근을 거절합니다.

## 조회

`GET /api/v1/accounts/me`는 이름·이메일·권한·관심 카테고리·알림 설정·광고 동의와 시각을 반환합니다.
기존 `GET /api/v1/auth/me`는 기본 회원 조회로 계속 사용할 수 있습니다.

## 변경

아래 변경 요청의 정상 응답은 모두 본문 없는 204입니다. 변경 후 조회 API로 저장 결과를 확인합니다.

| 메서드와 경로 | JSON 본문 |
|---|---|
| PATCH /api/v1/accounts/me | `{"name":"새이름"}` |
| PUT /api/v1/accounts/me/interests | `{"interestCategories":["FASHION","ELECTRONICS"]}` |
| PUT /api/v1/accounts/me/notification-settings | `{"saleStart":true,"personalizedRecommendation":false}` |
| PUT /api/v1/accounts/me/marketing-consent | `{"marketingConsent":false}` |
| PUT /api/v1/accounts/me/password | `{"currentPassword":"Password!123","newPassword":"Changed!456"}` |

- 관심 카테고리는 목록 전체를 교체합니다. 빈 배열은 모두 해제하며 null·생략은 거절합니다.
- 알림 설정은 두 boolean을 모두 입력합니다. 광고 동의를 자동 변경하지 않습니다.
- 광고 동의는 true로 동의, false로 철회합니다. 실제 상태 변경 시에만 이력을 추가합니다.
  마지막 동의·철회 시각을 각각 보존하며 현재 동의 여부는 `marketingConsent.agreed`로 판단합니다.
  최초 미동의는 철회로 기록하지 않습니다.
- 이름은 앞뒤 공백을 제거한 1~100자입니다. 이메일·권한 변경은 제공하지 않습니다.
- 비밀번호는 영문·숫자·특수문자 포함 8~64자, UTF-8 72바이트 이하입니다.
  변경 성공 시 모든 기기의 Refresh 세션을 폐기하고 요청자의 Refresh 쿠키를 삭제합니다.
  다시 로그인해야 하며 이미 발급한 Access JWT는 만료까지 유효합니다.

## 일관성과 연동 범위

같은 회원의 변경은 계정 행 잠금으로 직렬화합니다. 동의 변경과 이력, 비밀번호 변경과 세션 폐기는
각각 하나의 DB 트랜잭션으로 처리합니다. 기존 관심 행은 재사용해 중복 요청의 유일 제약 충돌을 피합니다.

현재 설정 변경은 Account DB에만 반영합니다. Kafka·Outbox·AI 추천 갱신과 Notification 내부 조회 API는
후속 연동 범위입니다. 실제 발송을 구현할 때는 알림 설정과 최신 광고 동의를 별도로 확인해야 합니다.
