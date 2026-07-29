-- MANUAL MYSQL 8 FIX: restaurant_order_items.menu_item_id must reference restaurant_menu_items(id)
--
-- Run this once on each local database if QR/self-order fails with:
-- Cannot add or update a child row ... restaurant_order_items ... menu_item_id ...
-- REFERENCES inventory_linked_menu_items(id)
--
-- This script is intentionally manual because the project does not use Flyway/Liquibase.
-- It drops only the wrong FK(s) on restaurant_order_items.menu_item_id and recreates
-- the correct FK when existing data is valid.

USE restaurant_management_system;

-- 1) See the current FK target for restaurant_order_items.menu_item_id.
SELECT
    CONSTRAINT_NAME,
    REFERENCED_TABLE_NAME,
    REFERENCED_COLUMN_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'restaurant_order_items'
  AND COLUMN_NAME = 'menu_item_id'
  AND REFERENCED_TABLE_NAME IS NOT NULL;

-- 2) Drop FK(s) that still point to the old inventory-linked menu table.
SET @wrong_fk_names := (
    SELECT GROUP_CONCAT(CONCAT('DROP FOREIGN KEY `', REPLACE(CONSTRAINT_NAME, '`', '``'), '`') SEPARATOR ', ')
    FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'restaurant_order_items'
      AND COLUMN_NAME = 'menu_item_id'
      AND REFERENCED_TABLE_NAME IS NOT NULL
      AND REFERENCED_TABLE_NAME <> 'restaurant_menu_items'
);

SET @drop_wrong_fk_sql := IF(
    @wrong_fk_names IS NULL,
    'SELECT ''No wrong menu_item_id FK found'' AS migration_note',
    CONCAT('ALTER TABLE restaurant_order_items ', @wrong_fk_names)
);

PREPARE drop_wrong_fk_stmt FROM @drop_wrong_fk_sql;
EXECUTE drop_wrong_fk_stmt;
DEALLOCATE PREPARE drop_wrong_fk_stmt;

-- 3) Check existing data before adding the correct FK.
-- This must return 0. If it returns more than 0, fix/delete those order item rows first.
SELECT COUNT(*) AS orphan_order_items
FROM restaurant_order_items oi
LEFT JOIN restaurant_menu_items mi ON mi.id = oi.menu_item_id
WHERE oi.menu_item_id IS NOT NULL
  AND mi.id IS NULL;

-- 4) Add the correct FK if it does not already exist and there are no orphan rows.
SET @correct_fk_exists := (
    SELECT COUNT(*)
    FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'restaurant_order_items'
      AND COLUMN_NAME = 'menu_item_id'
      AND REFERENCED_TABLE_NAME = 'restaurant_menu_items'
);

SET @orphan_count := (
    SELECT COUNT(*)
    FROM restaurant_order_items oi
    LEFT JOIN restaurant_menu_items mi ON mi.id = oi.menu_item_id
    WHERE oi.menu_item_id IS NOT NULL
      AND mi.id IS NULL
);

SET @add_correct_fk_sql := IF(
    @correct_fk_exists > 0,
    'SELECT ''Correct menu_item_id FK already exists'' AS migration_note',
    IF(
        @orphan_count > 0,
        'SELECT ''Cannot add FK: restaurant_order_items has orphan menu_item_id rows'' AS migration_note',
        'ALTER TABLE restaurant_order_items ADD CONSTRAINT fk_restaurant_order_items_menu_item_current FOREIGN KEY (menu_item_id) REFERENCES restaurant_menu_items(id)'
    )
);

PREPARE add_correct_fk_stmt FROM @add_correct_fk_sql;
EXECUTE add_correct_fk_stmt;
DEALLOCATE PREPARE add_correct_fk_stmt;

-- 5) Confirm the final FK target.
SELECT
    CONSTRAINT_NAME,
    REFERENCED_TABLE_NAME,
    REFERENCED_COLUMN_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'restaurant_order_items'
  AND COLUMN_NAME = 'menu_item_id'
  AND REFERENCED_TABLE_NAME IS NOT NULL;
