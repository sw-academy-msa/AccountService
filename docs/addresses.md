# 구매자 배송지 관리

## 프론트와 백엔드의 역할

프론트는 카카오 우편번호 검색 팝업에서 주소를 선택한 뒤 `zonecode`를 `postalCode`,
`roadAddress`를 `roadAddress`로 전달합니다. 수령인·연락처·상세주소·배송 요청사항은 별도로 입력합니다.
선택한 기본 주소와 상세주소를 한 문자열로 합치지 않습니다.

이 저장소는 백엔드이므로 실제 팝업 UI는 포함하지 않습니다. 프론트 연결 시
[카카오 우편번호 공식 가이드](https://postcode.map.kakao.com/guide)의 스크립트와
`kakao.Postcode({oncomplete(data) { ... }}).open()`을 사용합니다.
우편번호 서비스는 REST API 키가 필요하지 않으며 서버가 카카오 주소 검색 REST API를 호출하지 않습니다.

서버는 형식·길이·인증·권한·소유권을 검증하지만 입력이 실제 카카오 검색에서 선택되었는지,
상세주소가 존재하는지, 수령인이 거주하는지까지 인증하지 않습니다.
검색을 통한 입력만으로 서버에서 주소 진위가 보증된다고 간주하지 않습니다.

## 인증과 데이터

모든 요청에 `Authorization: Bearer <accessToken>`이 필요합니다. Refresh 쿠키만으로 사용할 수 없으며
CSRF 헤더는 요구하지 않습니다. JWT의 회원 ID로 현재 ACTIVE·이메일 인증 완료·BUYER 권한을 확인합니다.
SELLER를 추가로 가진 구매자도 이용할 수 있습니다. 모든 단건 접근은 주소 ID와 회원 ID를 함께 확인합니다.
타인 주소와 존재하지 않는 주소는 모두 404로 응답합니다.

저장 테이블은 `account.addresses`입니다. 현재 개발 정책대로 Hibernate update로 생성합니다.
배송지 수령인 연락처는 회원가입 시 휴대전화 수집과 별개이며, 하이픈을 제거해 저장합니다.
기본 정보 수정은 해당 주소 전체 교체이며 배송 요청사항·상세주소는 null 또는 생략으로 비울 수 있습니다.

## Postman 요청

기본 경로: `http://localhost:8080/api/v1/accounts/me/addresses`

| 메서드·경로 | 역할 | 정상 응답 |
|---|---|---|
| GET /addresses | 기본 주소가 먼저 나오는 본인 목록 | 200 배열 |
| GET /addresses/{addressId} | 본인 주소 단건 | 200 |
| POST /addresses | 새 배송지 등록 | 201, addressId·defaultAddress·Location |
| PUT /addresses/{addressId} | 배송지 정보 전체 수정 | 204 |
| PUT /addresses/{addressId}/default | 기본 지정, 본문 없음 | 204 |
| DELETE /addresses/{addressId} | 배송지 삭제 | 204 |

표의 `/addresses` 앞에는 `/api/v1/accounts/me`를 붙입니다.
등록·수정은 Body → raw → JSON으로 다음을 보냅니다.

```json
{
  "label": "집",
  "recipientName": "홍길동",
  "recipientPhone": "010-1234-5678",
  "postalCode": "06236",
  "roadAddress": "서울 강남구 테헤란로 152",
  "detailAddress": "12층",
  "deliveryMemo": "문 앞에 놓아주세요"
}
```

기본 여부와 소유자 ID는 본문으로 받지 않습니다. 첫 배송지는 자동으로 기본 지정합니다.
다른 배송지가 남아 있으면 기본 배송지 삭제는 409 `ADDRESS_DEFAULT_CHANGE_REQUIRED`로 거절합니다.
먼저 다른 주소를 기본 지정해야 하며, 유일한 배송지는 삭제할 수 있습니다.
모두 삭제한 후 다시 등록한 첫 주소는 기본으로 지정합니다.

같은 계정의 등록·수정·삭제·기본 지정은 계정 행 잠금으로 직렬화합니다.
A/B 인스턴스에서 동시에 요청해도 같은 PostgreSQL 잠금을 사용합니다.
주문 시 배송지 스냅샷 저장과 Order 내부 API 연동은 후속 범위이며, 향후 주소를 수정·삭제해도
주문에 이미 저장된 스냅샷은 변경하면 안 됩니다.
