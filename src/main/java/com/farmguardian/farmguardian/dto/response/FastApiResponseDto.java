package com.farmguardian.farmguardian.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class FastApiResponseDto {
    private String crop;
    private Integer total;
    private String risk;

    @JsonProperty("object")
    private List<DetectedObject> detectedObjects;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DetectedObject {
        private Integer id;
        private BoundingBox points;
        private Map<String, Double> confidence;
        private String insectName;
        private String grow;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BoundingBox {
        private Double xtl;    // x top left
        private Double ytl;    // y top left
        private Double xbr;    // x bottom right
        private Double ybr;    // y bottom right
    }
}

