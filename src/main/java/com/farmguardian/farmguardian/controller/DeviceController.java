package com.farmguardian.farmguardian.controller;

import com.farmguardian.farmguardian.config.auth.UserDetailsImpl;
import com.farmguardian.farmguardian.dto.request.DeviceConnectRequestDto;
import com.farmguardian.farmguardian.dto.request.DeviceUpdateRequestDto;
import com.farmguardian.farmguardian.dto.response.DeviceResponseDto;
import com.farmguardian.farmguardian.service.DeviceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    // 디바이스 연결
    @PostMapping("/connect")
    public ResponseEntity<DeviceResponseDto> connectDevice(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody DeviceConnectRequestDto request) {
        Long userId = userDetails.getUserId();
        DeviceResponseDto response = deviceService.connectDevice(userId, request);
        return ResponseEntity.ok(response);
    }

    // 연결 가능한 디바이스 목록 조회 (화이트리스트)
    @GetMapping("/available")
    public ResponseEntity<List<DeviceResponseDto>> getAvailableDevices() {
        List<DeviceResponseDto> responses = deviceService.getAvailableDevices();
        return ResponseEntity.ok(responses);
    }
    // 내 디바이스 목록 조회
    @GetMapping
    public ResponseEntity<List<DeviceResponseDto>> getMyDevices(
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        Long userId = userDetails.getUserId();
        List<DeviceResponseDto> responses = deviceService.getDevicesByUserId(userId);
        return ResponseEntity.ok(responses);
    }

    // 디바이스 상세 조회
    @GetMapping("/{deviceId}")
    public ResponseEntity<DeviceResponseDto> getDevice(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable("deviceId") Long deviceId) {
        Long userId = userDetails.getUserId();
        DeviceResponseDto response = deviceService.getDeviceById(userId, deviceId);
        return ResponseEntity.ok(response);
    }

    // 디바이스 정보 수정
    @PatchMapping("/{deviceId}")
    public ResponseEntity<DeviceResponseDto> updateDevice(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable("deviceId") Long deviceId,
            @Valid @RequestBody DeviceUpdateRequestDto request) {
        Long userId = userDetails.getUserId();
        DeviceResponseDto response = deviceService.updateDevice(userId, deviceId, request);
        return ResponseEntity.ok(response);
    }

    // 디바이스 연결 해제
    @DeleteMapping("/{deviceId}")
    public ResponseEntity<Void> disconnectDevice(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable("deviceId") Long deviceId) {
        Long userId = userDetails.getUserId();
        deviceService.disconnectDevice(userId, deviceId);
        return ResponseEntity.ok().build();
    }

}