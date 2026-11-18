package com.msa.account.domain.account.enums;

/**
 * 가입 화면에서 선택 가능한 고정 카테고리 코드입니다. 상품 DB를 직접 참조하지 않습니다.
 * Product와 통합할 때 이 코드를 공통 API·이벤트 계약에 맞춰 관리해야 합니다.
 */
public enum InterestCategory {
    FASHION("의류·패션"), APPLIANCES("가전제품"), ELECTRONICS("전자기기"),
    HOUSEHOLD("생활용품"), INTERIOR("인테리어·가구"), TOOLS("공구·자재"),
    FOOD("식품·음료"), BEAUTY("뷰티·헬스"), SPORTS("스포츠·레저"),
    STATIONERY("문구·도서"), KIDS("유아·아동"), OTHER("기타");

    private final String label;
    InterestCategory(String label) { this.label = label; }
    public String getLabel() { return label; }
}
