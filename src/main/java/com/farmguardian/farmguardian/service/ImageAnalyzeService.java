package com.farmguardian.farmguardian.service;

import com.farmguardian.farmguardian.domain.Device;
import com.farmguardian.farmguardian.domain.OriginImage;
import com.farmguardian.farmguardian.dto.request.FastApiRequestDto;
import com.farmguardian.farmguardian.dto.request.FcmSendRequestDto;
import com.farmguardian.farmguardian.dto.request.ImageMetadataRequestDto;
import com.farmguardian.farmguardian.dto.request.MobileImageUploadRequestDto;
import com.farmguardian.farmguardian.dto.response.FastApiResponseDto;
import com.farmguardian.farmguardian.dto.response.ImageAnalysisResponseDto;
import com.farmguardian.farmguardian.exception.device.DeviceNotFoundException;
import com.farmguardian.farmguardian.exception.image.FastApiCallFailedException;
import com.farmguardian.farmguardian.repository.DeviceRepository;
import com.farmguardian.farmguardian.repository.OriginImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageAnalyzeService {

    private final DeviceRepository deviceRepository;
    private final OriginImageRepository originImageRepository;
    private final FcmService fcmService;
    private final ImageService imageService;
    private final RestClient fastApiRestClient;
    private final DeviceService deviceService;

    private static final double CONFIDENCE_THRESHOLD = 0.2;

    public ImageAnalysisResponseDto analyzeImage(ImageMetadataRequestDto request) {

        Device device = deviceRepository.findByDeviceUuid(request.getDeviceUuid())
                .orElseThrow(DeviceNotFoundException::new);

        OriginImage originImage = imageService.saveMetaData(request, device);   // db 저장 (api 호출이 실패해 분석결과가 없어도 메타데이터는 저장 필요.)

        FastApiResponseDto fastApiResponse = callFastApi(request); // 외부 api 호출

        imageService.saveAnalysisResult(originImage.getId(), fastApiResponse);

        List<ImageAnalysisResponseDto.PestInfo> detectedPests = filterHighConfidencePests(fastApiResponse);
        boolean pestDetected = !detectedPests.isEmpty();

        if (pestDetected && device.getUser() != null) {
            sendPestDetectionNotification(device.getUser().getId(), detectedPests.size(), originImage.getId());
        }

        // 응답 생성
        return ImageAnalysisResponseDto.builder()
                .originImageId(originImage.getId())
                .cloudUrl(request.getCloudUrl())
                .pestDetected(pestDetected)
                .pests(detectedPests)
                .build();
    }

    private FastApiResponseDto callFastApi(ImageMetadataRequestDto request) {
        try {
            FastApiRequestDto fastApiRequest = new FastApiRequestDto();
            fastApiRequest.setUrl(request.getCloudUrl());

            return fastApiRestClient.post()
                    .uri("/v1/infer")
                    .body(fastApiRequest)
                    .retrieve()
                    .body(FastApiResponseDto.class);
        } catch (Exception e) {
            log.error("FastAPI 호출 실패: {}", e.getMessage(), e);
            throw new FastApiCallFailedException("이미지 분석 중 오류가 발생했습니다");
        }
    }

    private List<ImageAnalysisResponseDto.PestInfo> filterHighConfidencePests(FastApiResponseDto response) {
        List<ImageAnalysisResponseDto.PestInfo> pestList = new ArrayList<>();

        if (response.getDetectedObjects() == null) {
            return pestList;
        }

        for (FastApiResponseDto.DetectedObject obj : response.getDetectedObjects()) {
            if (obj.getConfidence() == null) {
                continue;
            }

            // confidence는 Map<String, Double> 형태
            for (Map.Entry<String, Double> entry : obj.getConfidence().entrySet()) {
                if (entry.getValue() >= CONFIDENCE_THRESHOLD) {
                    ImageAnalysisResponseDto.PestInfo pestInfo = ImageAnalysisResponseDto.PestInfo.builder()
                            .pestName(entry.getKey())
                            .confidence(entry.getValue())
                            .boundingBox(obj.getPoints())
                            .build();
                    pestList.add(pestInfo);
                }
            }
        }

        return pestList;
    }

    // 모바일 직접 촬영 이미지 분석
    public ImageAnalysisResponseDto analyzeMobileImage(Long userId, MobileImageUploadRequestDto request) {
        // 사용자의 모바일 디바이스 조회
        Device mobileDevice = deviceService.getMobileDeviceByUserId(userId);

        // 메타데이터 생성
        ImageMetadataRequestDto metadataRequest = new ImageMetadataRequestDto();
        metadataRequest.setDeviceUuid(mobileDevice.getDeviceUuid());
        metadataRequest.setCloudUrl(request.getCloudUrl());
        metadataRequest.setWidth(request.getWidth());
        metadataRequest.setHeight(request.getHeight());
        metadataRequest.setTemperature(null);
        metadataRequest.setHumidity(null);

        // db 저장
        OriginImage originImage = imageService.saveMetaData(metadataRequest, mobileDevice);

        // FastAPI 호출
        FastApiResponseDto fastApiResponse = callFastApi(metadataRequest);

        // 분석 결과 저장
        imageService.saveAnalysisResult(originImage.getId(), fastApiResponse);

        // 해충 필터링
        List<ImageAnalysisResponseDto.PestInfo> detectedPests = filterHighConfidencePests(fastApiResponse);
        boolean pestDetected = !detectedPests.isEmpty();

        // 해충 감지 시 알림 전송
        if (pestDetected) {
            sendPestDetectionNotification(userId, detectedPests.size(), originImage.getId());
        }

        // 응답 생성
        return ImageAnalysisResponseDto.builder()
                .originImageId(originImage.getId())
                .cloudUrl(request.getCloudUrl())
                .pestDetected(pestDetected)
                .pests(detectedPests)
                .build();
    }

    private void sendPestDetectionNotification(Long userId, int pestCount, Long originImageId) {
        try {
            OriginImage originImage = originImageRepository.findById(originImageId)
                    .orElse(null);

            if (originImage == null) {
                log.warn("이미지를 찾을 수 없어 FCM 알림을 보낼 수 없습니다. originImageId: {}", originImageId);
                return;
            }

            FcmSendRequestDto fcmRequest = new FcmSendRequestDto(
                    "해충 감지 알림",
                    String.format("감지된 해충: %d개", pestCount),
                    originImageId,
                    originImage.getCloudUrl(),
                    originImage.getDevice().getId()
            );
            fcmService.sendNotificationToUser(userId, fcmRequest);
            log.info("FCM 푸시 알림 발송 완료 - userId: {}, pestCount: {}, originImageId: {}, deviceId: {}",
                    userId, pestCount, originImageId, originImage.getDevice().getId());
        } catch (Exception e) {
            log.error("FCM 푸시 알림 발송 실패: {}", e.getMessage(), e);
        }
    }

}