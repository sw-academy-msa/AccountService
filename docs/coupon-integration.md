# 맞춤 쿠폰 동의 조회

기존 GET /internal/v1/notification-eligibility?accountId=&purpose=PERSONALIZED를 재사용합니다. ACTIVE BUYER, personalizedRecommendation 설정, marketingAgreed를 모두 확인합니다. 분석 동의 원본은 AIService analytics_identities이므로 AIService가 별도로 확인합니다.

account.service-auth.enabled는 기본 false입니다. 허용된 notification-service, coupon-service, ai-service의 설정된 subject와 client_id, principal_type=service, account.notifications.read scope를 함께 검증합니다. ai-service와 coupon-service 클라이언트는 기존 환경별 ACCOUNT_SERVICEAUTH_CLIENTS_* 설정으로 등록하며 비밀값은 BCrypt 해시만 둡니다. order-command-service에는 coupon.lock, coupon-service에는 account.notifications.read와 order.coupon.read를 허용합니다.

회원제 원본 이벤트는 Payment의 stockdrop.membership.events.private.v1이며 Account는 projection 소비자입니다. CouponService가 같은 토픽의 MEMBERSHIP_STARTED와 MEMBERSHIP_RENEWED를 독립 소비합니다. Account에서 쿠폰을 발급하거나 이벤트를 재발행하지 않습니다. 쿠폰 발급 멱등 키는 subscriptionId:generation:cycleNumber입니다.

배포 뒤에도 서비스 토큰 플래그와 클라이언트를 명시 설정하기 전에는 새 접근이 열리지 않습니다. 되돌릴 때 새 클라이언트를 비활성화하고 데이터와 회원제 이벤트 토픽은 보존합니다.
