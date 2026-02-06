package com.farmguardian.farmguardian.dto.response;

import com.farmguardian.farmguardian.domain.Device;
import com.farmguardian.farmguardian.domain.OriginImage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class ImageDetailResponseDto {
    private Long originImageId;
    private String cloudUrl;
    private Integer width;
    private Integer height;
    private LocalDateTime createdAt;
    private Long deviceId;
    private String deviceAlias;
    private String deviceStatus;
    private String targetCrop;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String analysisResult;
    private boolean pestDetected;

    public static ImageDetailResponseDto from(OriginImage originImage) {
        Device device = originImage.getDevice();

        return ImageDetailResponseDto.builder()
                .originImageId(originImage.getId())
                .cloudUrl(originImage.getCloudUrl())
                .width(originImage.getWidth())
                .height(originImage.getHeight())
                .createdAt(originImage.getCreatedAt())
                .deviceId(device.getId())
                .deviceAlias(device.getAlias())
                .deviceStatus(device.getStatus() != null ? device.getStatus().name() : null)
                .targetCrop(device.getTargetCrop() != null ? device.getTargetCrop().name() : null)
                .latitude(device.getLatitude())
                .longitude(device.getLongitude())
                .analysisResult(originImage.getAnalysisResult())
                .pestDetected(isPestDetected(originImage.getAnalysisResult()))
                .build();
    }

    // analysisResult의 total 값이 1 이상이면 true, 0이면 false
    private static boolean isPestDetected(String analysisResult) {
        if (analysisResult == null || analysisResult.isBlank()) {
            return false;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(analysisResult);
            JsonNode totalNode = root.path("total");

            if (totalNode.isMissingNode()) {
                return false;
            }

            int total = totalNode.asInt(0);
            return total >= 1;
        } catch (JsonProcessingException e) {
            return false;
        }
    }
}