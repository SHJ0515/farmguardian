package com.farmguardian.farmguardian.controller;

import com.farmguardian.farmguardian.config.auth.UserDetailsImpl;
import com.farmguardian.farmguardian.service.MqttService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final MqttService mqttService;

    @GetMapping("/")
    public String home() {
        return "Welcome to API!";
    }

    // 디바이스 촬영 요청
    @PostMapping("/{userId}/devices/{deviceId}/capture")
    public ResponseEntity<String> requestCapture(
            @PathVariable("userId") Long userId,
            @PathVariable("deviceId") Long deviceId) {
        //Long userId = userDetails.getUserId();
        mqttService.requestCapture(userId, deviceId);
        return ResponseEntity.ok("Capture command sent successfully");
    }

}
