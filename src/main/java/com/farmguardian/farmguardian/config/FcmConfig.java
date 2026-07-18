package com.farmguardian.farmguardian.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "firebase.enabled", havingValue = "true")
public class FcmConfig {

    @Value("${firebase.credentials-path:}")
    private String credentialsPath;

    @PostConstruct
    public void initialize() {
        if (!FirebaseApp.getApps().isEmpty()) {
            log.info("FirebaseApp already initialized");
            return;
        }

        try {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(loadCredentials())
                    .build();

            FirebaseApp.initializeApp(options);
            log.info("FirebaseApp initialized");
        } catch (IOException e) {
            log.error("Firebase initialization failed", e);
            throw new IllegalStateException("Firebase 초기화에 실패했습니다.", e);
        }
    }

    private GoogleCredentials loadCredentials() throws IOException {
        if (!StringUtils.hasText(credentialsPath)) {
            return GoogleCredentials.getApplicationDefault();
        }

        Path path = Path.of(credentialsPath).toAbsolutePath().normalize();
        try (InputStream credentialsStream = Files.newInputStream(path)) {
            return GoogleCredentials.fromStream(credentialsStream);
        }
    }
}
