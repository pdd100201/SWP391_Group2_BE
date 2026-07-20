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
        jdbcTemplate.execute("ALTER TABLE promotions MODIFY COLUMN name VARCHAR(120) NULL");

        if (columnExists("promotions", "value")) {
            jdbcTemplate.execute("ALTER TABLE promotions MODIFY COLUMN value DECIMAL(19,2) NULL");
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
