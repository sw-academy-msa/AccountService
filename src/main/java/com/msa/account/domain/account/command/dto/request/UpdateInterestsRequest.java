package com.msa.account.domain.account.command.dto.request;
import com.msa.account.domain.account.enums.InterestCategory;
import jakarta.validation.constraints.*;
import java.util.Set;
/** 목록 전체를 교체합니다. 빈 배열은 전체 해제이고 생략·null은 입력 오류입니다. */
public record UpdateInterestsRequest(
        @NotNull @Size(max = 12) Set<@NotNull InterestCategory> interestCategories) { }
