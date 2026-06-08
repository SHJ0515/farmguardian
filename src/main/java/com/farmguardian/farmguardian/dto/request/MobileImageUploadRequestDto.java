package com.farmguardian.farmguardian.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MobileImageUploadRequestDto {

    @NotBlank(message = "이미지 URL은 필수입니다")
    private String cloudUrl;

    @NotNull(message = "이미지 너비는 필수입니다")
    private Integer width;

    @NotNull(message = "이미지 높이는 필수입니다")
    private Integer height;
}