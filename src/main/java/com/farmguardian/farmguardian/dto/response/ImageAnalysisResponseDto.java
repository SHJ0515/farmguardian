package com.farmguardian.farmguardian.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageAnalysisResponseDto {
    private Long originImageId;
    private String cloudUrl;
    private Boolean pestDetected;  // 해충 존재 여부
    private List<PestInfo> pests;  // 검출된 해충 목록

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PestInfo {
        private String pestName;                      // 해충명
        private Double confidence;                    // 신뢰도
        private FastApiResponseDto.BoundingBox boundingBox;  // 위치 정보
    }
}