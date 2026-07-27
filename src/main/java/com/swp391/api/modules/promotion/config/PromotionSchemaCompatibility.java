package com.swp391.api.modules.promotion.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(4)
public class PromotionSchemaCompatibility implements CommandLineRunner {
    private final JdbcTemplate jdbcTemplate;

    public PromotionSchemaCompatibility(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        if (!columnExists("promotions", "name")) {
            return;
        }

        // These columns belong to the old promotion model. The current entity
        // writes to name, type, value and is_active, so legacy columns must not
        // reject new rows when they are still present in an existing database.
        makeNullableIfPresent("promotion_name", "VARCHAR(100)");
        makeNullableIfPresent("discount_type", "ENUM('FIXED','PERCENT')");
        makeNullableIfPresent("discount_value", "DECIMAL(12,2)");
        makeNullableIfPresent("status", "ENUM('ACTIVE','INACTIVE')");

        jdbcTemplate.execute("ALTER TABLE promotions MODIFY COLUMN name VARCHAR(120) NULL");

        if (columnExists("promotions", "value")) {
            jdbcTemplate.execute("ALTER TABLE promotions MODIFY COLUMN value DECIMAL(19,2) NULL");
        }
    }

    private void makeNullableIfPresent(String columnName, String sqlType) {
        if (columnExists("promotions", columnName)) {
            jdbcTemplate.execute(
                    "ALTER TABLE promotions MODIFY COLUMN " + columnName + " " + sqlType + " NULL"
            );
        }
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                """, Integer.class, tableName, columnName);
        return count != null && count > 0;
    }
}
