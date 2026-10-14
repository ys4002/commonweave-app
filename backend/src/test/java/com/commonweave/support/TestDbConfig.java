package com.commonweave.support;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Wipes and rebuilds the schema at test start. Refuses to run against any database but commonweave_test. */
@Configuration
@Profile("test")
public class TestDbConfig {
    @Bean
    FlywayMigrationStrategy cleanThenMigrate() {
        return flyway -> {
            String catalog = currentDatabase(flyway.getConfiguration().getDataSource());
            if (!"commonweave_test".equals(catalog)) {
                throw new IllegalStateException("Refusing to clean database '" + catalog + "': not commonweave_test");
            }
            flyway.clean();
            flyway.migrate();
        };
    }

    private static String currentDatabase(DataSource ds) {
        try (Connection c = ds.getConnection()) {
            return c.getCatalog();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
