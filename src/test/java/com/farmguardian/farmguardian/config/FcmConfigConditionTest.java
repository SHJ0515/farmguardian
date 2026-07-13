package com.farmguardian.farmguardian.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FcmConfigConditionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(FcmConfig.class);

    @Test
    void firebaseIsDisabledWhenEnabledPropertyIsMissing() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(FcmConfig.class));
    }

    @Test
    void firebaseIsDisabledWhenEnabledPropertyIsFalse() {
        contextRunner
                .withPropertyValues("firebase.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(FcmConfig.class));
    }

    @Test
    void firebaseStartupFailsWhenEnabledCredentialFileIsMissing() {
        Path missingCredentials = Path.of(
                "build",
                "missing-firebase-credentials-" + UUID.randomUUID() + ".json"
        ).toAbsolutePath();

        contextRunner
                .withPropertyValues(
                        "firebase.enabled=true",
                        "firebase.credentials-path=" + missingCredentials
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(NoSuchFileException.class);
                });
    }
}
