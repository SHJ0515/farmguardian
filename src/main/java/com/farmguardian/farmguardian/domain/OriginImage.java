package com.farmguardian.farmguardian.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Slf4j
@Entity
@Table(name = "origin_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class OriginImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "origin_image_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Column(name = "cloud_url", nullable = false, length = 512)
    private String cloudUrl;

    @Column(nullable = false)
    private Integer height;

    @Column(nullable = false)
    private Integer width;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(columnDefinition = "json")
    private String analysisResult;  // JSON 문자열 그대로 저장


    @Builder
    public OriginImage(Device device, String cloudUrl, Integer width, Integer height) {
        this.device = device;
        this.cloudUrl = cloudUrl;
        this.width = width;
        this.height = height;
    }

    // 분석 결과 업데이트
    public void updateAnalysisResult(String analysisResultJson) {
        this.analysisResult = analysisResultJson;
    }
}