# STOCKDROP Account Service

Java 21 / Spring Boot 4.1.1 기반 Account 애플리케이션입니다. Command와 Query는 패키지로 구분하고 같은 DB와 배포 단위를 사용합니다.

## 로컬 실행

```powershell
# PostgreSQL(5433)과 테스트 메일함 SMTP(1025), 웹 UI(8025)를 실행합니다.
docker compose up -d

# 기본 local 프로필은 Hibernate update를 사용합니다.
.\gradlew.bat bootRun
```

- Swagger: http://localhost:8080/swagger-ui.html
- 로컬 수신 메일함: http://localhost:8025
- 테스트: `.\gradlew.bat test` (Docker 필요, Testcontainers PostgreSQL 사용)

Mailpit은 실제 외부 수신자에게 메일을 보내지 않습니다. 루트 `.env`는 Spring과 Compose가 함께 읽습니다. `.env`가 없으면 기본 local 설정을 사용합니다. Mailpit을 사용하려면 `.env`의 `SPRING_PROFILES_ACTIVE=local`로 설정합니다.

## Gmail 실제 메일 발송

Gmail SMTP를 사용하려면 Google 계정의 2단계 인증을 켜고 [앱 비밀번호](https://myaccount.google.com/apppasswords)를 발급합니다. 앱 이름은 STOCKDROP처럼 용도를 알아볼 수 있게 지정합니다. 앱 비밀번호 생성 조건과 계정별 제한은 [Google 공식 안내](https://support.google.com/accounts/answer/185833?hl=ko)를 참고합니다.

프로젝트 루트의 `.env`에서 다음 값을 설정합니다. 파일이 없으면 `.env.example`을 복사하여 만듭니다. IntelliJ의 Run → Edit Configurations → AccountApplication → Working directory는 프로젝트 루트로 지정합니다.

```text
SPRING_PROFILES_ACTIVE=local,gmail
MAIL_USERNAME=발신계정@gmail.com
MAIL_PASSWORD=발급한16자리앱비밀번호
```

각 줄은 `KEY=value`로 적고 값에 따옴표나 `export`를 붙이지 않습니다. 발급 화면에서 구분용 공백이 표시되면 제거하고 입력합니다. Gmail 로그인 비밀번호를 사용하지 않습니다. 실제 `.env`는 Git 및 Docker 빌드 컨텍스트에서 제외하며 `.env.example`에는 비밀값을 넣지 않습니다. 실행 설정의 Active profiles·VM 옵션·환경변수로 같은 값을 따로 지정했다면 해당 값이 우선하므로 중복 설정을 제거하거나 일치시킵니다.

앱을 재시작하고 로그에서 local과 gmail 프로필이 활성화되었는지 확인한 뒤 Postman으로 인증번호를 요청합니다. Gmail 프로필은 smtp.gmail.com:587에 STARTTLS로 연결하고 MAIL_USERNAME을 발신자로 사용합니다. 이 모드에서는 Mailpit 대신 요청한 실제 이메일 주소의 받은편지함·스팸함을 확인합니다. Gmail의 SMTP 연결 방식은 [Google 개발자 문서](https://developers.google.com/workspace/gmail/imap/imap-smtp)를 따릅니다.

local,gmail은 개발 DB의 update 설정을 유지합니다. 나중에 운영에서는 prod,gmail을 사용할 수 있습니다. 기본 local만 켜면 기존 Mailpit 설정을 사용합니다. 실제 SMTP 인증·발송은 사용자 자격증명 설정 후 확인해야 하며, 앱 시작만으로 자동 발송하지 않습니다.

## 이메일 인증 후 회원가입

1. `POST /api/v1/auth/email-verifications`에 이메일을 전달합니다.

```json
{"email":"buyer@example.com"}
```

응답에는 `challengeId`, 서버 기준 `expiresAt`, `resendAvailableAt`이 있습니다. 인증번호는 메일로만 전송합니다.

2. `POST /api/v1/auth/email-verifications/confirm`에서 받은 번호를 확인합니다.

```json
{"email":"buyer@example.com","challengeId":"발급받은 UUID","code":"메일의 6자리 번호"}
```

성공하면 가입 전용 일회용 `verificationToken`과 만료 시각을 반환합니다. 인증번호와 이 토큰을 로그나 URL에 넣지 않습니다.

3. `GET /api/v1/signup-options`로 제출 가능한 카테고리 코드와 알림 유형을 확인합니다.

4. `POST /api/v1/auth/signup`에서 가입을 완료합니다.

```json
{
  "email": "buyer@example.com",
  "verificationToken": "인증 성공 응답의 토큰",
  "password": "Password!123",
  "name": "김서윤",
  "interestCategories": ["FASHION", "ELECTRONICS"],
  "notificationSettings": {
    "saleStart": true,
    "personalizedRecommendation": true
  },
  "marketingConsent": true
}
```

이메일 인증, 비밀번호, 이름은 필수입니다. 비밀번호는 현재 기존 정책인 영문·숫자·특수문자 포함 8~64자를 유지합니다. 피그마의 8~32자와 통일할지는 별도 확정이 필요합니다. 휴대전화는 받지 않습니다. 관심 카테고리·알림 설정·광고성 동의는 생략하거나 null로 보내도 가입할 수 있으며 빈 관심 목록과 false로 저장합니다. 가입 시 ACTIVE 상태와 BUYER 권한을 부여하며 로그인 토큰은 아직 발급하지 않습니다.

카테고리 코드는 가입용 고정 목록이며 Product의 API·이벤트와 통합할 때 계약을 맞춰야 합니다. 가격 변동·재고 임박 알림은 MVP 범위가 확정되지 않아 제공하지 않습니다.

## 저장과 일관성

| 테이블 | 소유 정보 |
|---|---|
| `account.accounts` | 이메일, 비밀번호 해시, 이름, 인증 시각, 회원 상태 |
| `account.account_roles` | 회원의 복수 권한 |
| `account.account_interests` | 선택한 카테고리별 행 |
| `account.account_notification_settings` | 판매 시작·개인화 추천 알림 설정 |
| `account.account_consents` | 최신 광고성 동의와 시각 |
| `account.account_consent_histories` | 가입 시 선택한 동의 상태의 이력 |
| `account.email_verifications` | 이메일별 최신 인증 요청, 시도 횟수, 가입 증명 해시 |

회원과 선택 정보 저장, 인증 증명 소비는 한 로컬 DB 트랜잭션에 포함합니다. 저장 실패 시 증명도 소비하지 않습니다. 이메일별 PostgreSQL 트랜잭션 잠금으로 여러 Account 인스턴스의 발급·확인·가입 경합을 제어합니다. 이미 존재하는 행만 잠그는 방식과 달리 최초 발급 요청도 직렬화합니다.

인증번호는 BCrypt, 충분한 난수로 만든 가입 증명은 SHA-256 해시로 저장합니다. 가입 증명은 인증한 이메일에 연결되고 한 번만 사용할 수 있습니다. 재발급하면 이전 코드와 증명을 무효화합니다. 인증 성공 응답을 잃었다면 재요청 가능 시각 이후 번호부터 다시 발급합니다.

`application.yaml`의 초기 개발 정책은 인증번호 5분, 가입 증명 15분, 재요청 간격 60초, 오입력 최대 5회, 이메일별 한 시간 최대 10회 발급입니다. 오입력 횟수는 실패 응답에도 커밋합니다. 운영 전 정책 값과 만료된 인증 기록 보존 기간을 확정해야 합니다. 전체/IP별 발송 제한은 Gateway 연동 시 추가 검토 대상입니다.

## 메일과 후속 작업

현재 가입 인증 메일은 Account가 타임아웃을 설정한 SMTP로 직접 보냅니다. 실제 SMTP 전송 어댑터를 인터페이스로 분리했고 테스트에서는 이를 대체합니다. SMTP 오류는 발급 DB 변경을 롤백하지만 SMTP와 DB 사이의 원자적 전달이나 정확히 한 번 수신을 보장하지 않습니다. 메일 전달 후 DB 커밋이 실패하면 재발급이 필요할 수 있습니다.

인증 메일은 피그마의 흰색·차콜 색상과 워드마크를 반영한 HTML로 발송하며 일반 텍스트도 함께 제공합니다. 템플릿은 `src/main/resources/templates/mail/email-verification.html`에 있고 만료 시각은 Asia/Seoul로 표시합니다. 이미지 다운로드나 JavaScript 없이 인증번호를 읽고 복사할 수 있습니다. 인증 흐름은 기존처럼 가입 화면에 번호를 입력하는 방식입니다. 메일 단위 테스트를 실행하면 `build/reports/mail/email-verification-preview.html`에 가짜 코드가 들어간 미리보기를 생성합니다. 실제 Gmail에서의 표시 결과는 수신 환경에 따라 차이가 있을 수 있습니다.

운영 SMTP는 `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`을 배포 환경에서 설정합니다. Mailpit은 로컬 전용입니다. 운영 스키마는 현재 validate이므로 운영 배포 전 별도 마이그레이션을 준비해야 합니다.

알림 설정과 광고성 동의는 별도 조건입니다. 설정이 true여도 동의나 기기 권한을 대신하지 않습니다. Notification은 실제 광고성 발송 직전에 Account의 최신 동의를 확인해야 합니다. 실제 푸시·Kafka 이벤트·가입 후 설정 변경·동의 철회 및 조회 API·로그인/JWT는 후속 구현 범위입니다. 기존 개발 계정의 인증 시각과 초기 설정 보완도 해당 API를 연결하기 전에 필요합니다.
