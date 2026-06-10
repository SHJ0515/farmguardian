package com.farmguardian.farmguardian.service;

import com.farmguardian.farmguardian.domain.Device;
import com.farmguardian.farmguardian.domain.OriginImage;
import com.farmguardian.farmguardian.dto.request.ImageMetadataRequestDto;
import com.farmguardian.farmguardian.dto.response.FastApiResponseDto;
import com.farmguardian.farmguardian.dto.response.ImageListResponseDto;
import com.farmguardian.farmguardian.dto.response.ImageDetailResponseDto;
import com.farmguardian.farmguardian.exception.image.ImageAnalysisFailedException;
import com.farmguardian.farmguardian.exception.image.ImageNotFoundException;
import com.farmguardian.farmguardian.repository.OriginImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ImageService {

    private final OriginImageRepository originImageRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public OriginImage saveMetaData(ImageMetadataRequestDto request, Device device) {

        OriginImage originImage = OriginImage.builder()
                .device(device)
                .cloudUrl(request.getCloudUrl())
                .width(request.getWidth())
                .height(request.getHeight())
                .build();

        originImage = originImageRepository.save(originImage);
        return originImage;
    }

    @Transactional
    public void saveAnalysisResult(Long originImageId, FastApiResponseDto fastApiResponse) {
        String analysisResultJson = convertToJson(fastApiResponse);

        OriginImage originImage = originImageRepository.findById(originImageId)
                .orElseThrow(ImageNotFoundException::new);

        originImage.updateAnalysisResult(analysisResultJson);
        log.info("이미지 분석 결과 저장 완료 - originImageId: {}", originImageId);
    }

    private String convertToJson(FastApiResponseDto response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JacksonException e) {
            log.error("JSON 변환 실패: {}", e.getMessage(), e);
            throw new ImageAnalysisFailedException("분석 결과 JSON 변환 실패");
        }
    }

    // 사용자의 모든 디바이스 이미지 목록 조회
    public Slice<ImageListResponseDto> getAllImagesByUser(Long userId, Pageable pageable) {
        Slice<OriginImage> images = originImageRepository.findAllByDevice_User_IdOrderByCreatedAtDesc(userId, pageable);
        return images.map(ImageListResponseDto::from);
    }

    // 특정 디바이스의 이미지 목록 조회
    public Slice<ImageDetailResponseDto> getImagesByDevice(Long deviceId, Pageable pageable) {
        Slice<OriginImage> images = originImageRepository.findAllByDevice_IdOrderByCreatedAtDesc(deviceId, pageable);
        return images.map(ImageDetailResponseDto::from);
    }

    // 이미지 상세 조회
    public ImageDetailResponseDto getImageById(Long originImageId) {
        OriginImage originImage = originImageRepository.findById(originImageId)
                .orElseThrow(ImageNotFoundException::new);
        return ImageDetailResponseDto.from(originImage);
    }
}
