package com.farmguardian.farmguardian.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ImageMetadataRequestDto {

    private String deviceUuid;

    private String cloudUrl;

    private Integer width;

    private Integer height;

    private Double temperature;

    private Double humidity;
}
