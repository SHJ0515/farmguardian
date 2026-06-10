package com.farmguardian.farmguardian.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FcmSendRequestDto {

    @NotBlank(message = "제목은 필수입니다")
    private String title;

    @NotBlank(message = "내용은 필수입니다")
    private String body;

    // 해충 감지 이미지 ID (푸시 알림 클릭 시 상세 조회용)
    private Long originImageId;

    private String cloudUrl;

    private Long deviceId;

    public FcmSendRequestDto(String title, String body) {
        this(title, body, null, null, null);
    }
}
