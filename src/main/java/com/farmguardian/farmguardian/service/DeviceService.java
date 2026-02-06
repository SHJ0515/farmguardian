package com.farmguardian.farmguardian.service;

import com.farmguardian.farmguardian.domain.Device;
import com.farmguardian.farmguardian.domain.DeviceStatus;
import com.farmguardian.farmguardian.domain.User;
import com.farmguardian.farmguardian.dto.request.DeviceConnectRequestDto;
import com.farmguardian.farmguardian.dto.request.DeviceUpdateRequestDto;
import com.farmguardian.farmguardian.dto.response.DeviceResponseDto;
import com.farmguardian.farmguardian.exception.auth.UserNotFoundException;
import com.farmguardian.farmguardian.exception.device.DeviceAlreadyConnectedException;
import com.farmguardian.farmguardian.exception.device.DeviceNotFoundException;
import com.farmguardian.farmguardian.exception.device.UnauthorizedDeviceAccessException;
import com.farmguardian.farmguardian.repository.DeviceRepository;
import com.farmguardian.farmguardian.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;

    // 디바이스 연결 (화이트리스트에서 선택)
    @Transactional
    public DeviceResponseDto connectDevice(Long userId, DeviceConnectRequestDto request) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        Device device = deviceRepository.findByDeviceUuid(request.getDeviceUuid())
                .orElseThrow(DeviceNotFoundException::new);

        // 이미 연결된 디바이스인지 확인
        if (device.getStatus() == DeviceStatus.CONNECTED) {
            throw new DeviceAlreadyConnectedException();
        }

        // 디바이스 연결
        device.connectToUser(
                user,
                request.getAlias(),
                request.getTargetCrop(),
                request.getLatitude(),
                request.getLongitude()
        );

        return DeviceResponseDto.from(device);
    }

    // 디바이스 상세 조회
    public DeviceResponseDto getDeviceById(Long userId, Long deviceId) {
        Device device = deviceRepository.findByIdAndUserId(deviceId, userId)
                .orElseThrow(UnauthorizedDeviceAccessException::new);

        return DeviceResponseDto.from(device);
    }

    // 내 디바이스 목록 조회
    public List<DeviceResponseDto> getDevicesByUserId(Long userId) {
        List<Device> devices = deviceRepository.findAllByUserId(userId);

        return devices.stream()
                .map(DeviceResponseDto::from)
                .collect(Collectors.toList());
    }

    // 연결 가능한 디바이스 목록 조회 (화이트리스트)
    public List<DeviceResponseDto> getAvailableDevices() {
        List<Device> devices = deviceRepository.findAllByStatus(DeviceStatus.AVAILABLE);

        return devices.stream()
                .map(DeviceResponseDto::from)
                .collect(Collectors.toList());
    }

    // 디바이스 수정
    @Transactional
    public DeviceResponseDto updateDevice(Long userId, Long deviceId, DeviceUpdateRequestDto request) {
        Device device = deviceRepository.findByIdAndUserId(deviceId, userId)
                .orElseThrow(UnauthorizedDeviceAccessException::new);

        // 비즈니스 메서드를 통한 업데이트
        device.updateAlias(request.getAlias());
        device.updateTargetCrop(request.getTargetCrop());
        device.updateLocation(request.getLatitude(), request.getLongitude());

        return DeviceResponseDto.from(device);
    }

    // 디바이스 연결 해제
    @Transactional
    public void disconnectDevice(Long userId, Long deviceId) {
        Device device = deviceRepository.findByIdAndUserId(deviceId, userId)
                .orElseThrow(UnauthorizedDeviceAccessException::new);

        device.disconnectFromUser();
    }

    // 사용자의 모바일 디바이스 조회
    public Device getMobileDeviceByUserId(Long userId) {
        String mobileDeviceUuid = "mobile-user-" + userId;
        return deviceRepository.findByDeviceUuid(mobileDeviceUuid)
                .orElseThrow(DeviceNotFoundException::new);
    }
}
