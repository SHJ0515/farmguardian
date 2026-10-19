package com.farmguardian.farmguardian.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("Flyway 단계적 도입 안전장치")
class FlywayDefaultSafetyTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("명시적으로 활성화하지 않은 일반 테스트에서는 Flyway가 실행되지 않는다")
    void keepFlywayDisabledByDefault() {
        assertThat(applicationContext.getBeansOfType(Flyway.class)).isEmpty();
    }

    @Test
    @DisplayName("main·local 설정은 Flyway opt-in과 Hibernate validate를 기본값으로 유지한다")
    void keepMainAndLocalConfigurationFailSafe() throws IOException {
        PropertySource<?> main = loadYaml("src/main/resources/application.yml");
        PropertySource<?> local = loadYaml("src/main/resources/application-local.yml");

        assertThat(main.getProperty("spring.flyway.enabled"))
                .isEqualTo("${FLYWAY_ENABLED:false}");
        assertThat(main.getProperty("spring.flyway.baseline-on-migrate")).isEqualTo(false);
        assertThat(main.getProperty("spring.flyway.clean-disabled")).isEqualTo(true);
        assertThat(local.getProperty("spring.jpa.hibernate.ddl-auto"))
                .isEqualTo("${JPA_DDL_AUTO:validate}");
    }

    private PropertySource<?> loadYaml(String path) throws IOException {
        FileSystemResource resource = new FileSystemResource(path);
        assertThat(resource.exists()).as("검증할 설정 파일이 존재해야 한다: %s", path).isTrue();
        return new YamlPropertySourceLoader().load(path, resource).getFirst();
    }
}
