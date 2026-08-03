-- MANUAL MYSQL 8 CLEANUP: remove obsolete inventory-era tables.
--
-- Current backend entities use the restaurant_* tables, qr_* tables,
-- bills/payments/promotions/users/customers/reservations/table_types.
-- The tables below are leftovers from the old inventory/order schema and are
-- no longer mapped by the current source code.
--
-- Run only after backing up your local database.

USE restaurant_management_system;

-- Check what will be removed.
SHOW TABLES WHERE Tables_in_restaurant_management_system IN (
  'inventory',
  'inventory_items',
  'inventory_linked_menu_items',
  'inventory_linked_menu_reservation_ingredients',
  'inventory_linked_menu_reservations',
  'inventory_linked_recipe_ingredients',
  'menu_items',
  'orders',
  'order_items',
  'reports',
  'restaurant_order_item_ingredients'
);

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS inventory_linked_menu_reservation_ingredients;
DROP TABLE IF EXISTS inventory_linked_recipe_ingredients;
DROP TABLE IF EXISTS restaurant_order_item_ingredients;
DROP TABLE IF EXISTS inventory_linked_menu_reservations;
DROP TABLE IF EXISTS inventory_linked_menu_items;
DROP TABLE IF EXISTS inventory_items;
DROP TABLE IF EXISTS inventory;
DROP TABLE IF EXISTS order_items;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS menu_items;
DROP TABLE IF EXISTS reports;

SET FOREIGN_KEY_CHECKS = 1;

-- Check again. This should return no rows for the obsolete tables.
SHOW TABLES WHERE Tables_in_restaurant_management_system IN (
  'inventory',
  'inventory_items',
  'inventory_linked_menu_items',
  'inventory_linked_menu_reservation_ingredients',
  'inventory_linked_menu_reservations',
  'inventory_linked_recipe_ingredients',
  'menu_items',
  'orders',
  'order_items',
  'reports',
  'restaurant_order_item_ingredients'
);
