-- MANUAL MYSQL 8 MIGRATION: support voiding served order items without deleting history.
-- Run this once on each local/team database before testing the Void Item feature.

SET @schema_name := DATABASE();

SET @status_column_type := (
  SELECT COLUMN_TYPE
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name
    AND TABLE_NAME = 'restaurant_order_items'
    AND COLUMN_NAME = 'status'
);

SET @alter_status_sql := IF(
  @status_column_type LIKE 'enum(%' AND @status_column_type NOT LIKE '%VOIDED%',
  'ALTER TABLE restaurant_order_items MODIFY status ENUM(''DRAFT'', ''CONFIRMED'', ''PREPARING'', ''READY'', ''SERVED'', ''CANCELLED'', ''VOIDED'') NOT NULL',
  'SELECT ''restaurant_order_items.status already supports VOIDED or is not ENUM'' AS migration_note'
);

PREPARE alter_status_stmt FROM @alter_status_sql;
EXECUTE alter_status_stmt;
DEALLOCATE PREPARE alter_status_stmt;

SET @add_void_reason_sql := IF(
  NOT EXISTS (
    SELECT 1
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'restaurant_order_items'
      AND COLUMN_NAME = 'void_reason'
  ),
  'ALTER TABLE restaurant_order_items ADD COLUMN void_reason VARCHAR(255) NULL',
  'SELECT ''restaurant_order_items.void_reason already exists'' AS migration_note'
);

PREPARE add_void_reason_stmt FROM @add_void_reason_sql;
EXECUTE add_void_reason_stmt;
DEALLOCATE PREPARE add_void_reason_stmt;

SET @add_voided_at_sql := IF(
  NOT EXISTS (
    SELECT 1
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'restaurant_order_items'
      AND COLUMN_NAME = 'voided_at'
  ),
  'ALTER TABLE restaurant_order_items ADD COLUMN voided_at DATETIME NULL',
  'SELECT ''restaurant_order_items.voided_at already exists'' AS migration_note'
);

PREPARE add_voided_at_stmt FROM @add_voided_at_sql;
EXECUTE add_voided_at_stmt;
DEALLOCATE PREPARE add_voided_at_stmt;

SET @add_voided_by_sql := IF(
  NOT EXISTS (
    SELECT 1
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'restaurant_order_items'
      AND COLUMN_NAME = 'voided_by'
  ),
  'ALTER TABLE restaurant_order_items ADD COLUMN voided_by VARCHAR(100) NULL',
  'SELECT ''restaurant_order_items.voided_by already exists'' AS migration_note'
);

PREPARE add_voided_by_stmt FROM @add_voided_by_sql;
EXECUTE add_voided_by_stmt;
DEALLOCATE PREPARE add_voided_by_stmt;
