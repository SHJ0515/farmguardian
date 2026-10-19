package com.farmguardian.farmguardian.migration;

import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:farmguardian-flyway-jpa-validation;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "spring.flyway.baseline-on-migrate=false",
        "spring.flyway.clean-disabled=true",
        "spring.flyway.validate-on-migrate=true",
        "spring.flyway.validate-migration-naming=true",
        "spring.flyway.fail-on-missing-locations=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@DisplayName("Flyway migration chain과 JPA Entity 계약 검증")
class FlywayJpaSchemaValidationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("전체 migration chain 적용 후 Hibernate schema validation이 성공한다")
    void validateMigratedSchemaAgainstJpaEntities() {
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(entityManagerFactory.isOpen()).isTrue();
    }
}
