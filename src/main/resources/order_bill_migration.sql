-- MANUAL MYSQL 8 MIGRATION: reservation bill + one order per assigned table
--
-- This file is intentionally NOT connected to Flyway/Liquibase and is never
-- executed by application code. Back up the database, run the PRE-FLIGHT
-- queries first, and only run the MIGRATION section when every pre-flight query
-- returns zero rows. Run this migration once before deploying the matching code.

-- ============================================================================
-- PRE-FLIGHT (read only)
-- ============================================================================

-- Every existing order must resolve to either reservations.table_id or a row in
-- reservation_tables. Expected result: zero rows.
SELECT o.order_id, o.reservation_id
FROM restaurant_orders o
JOIN reservations r ON r.reservation_id = o.reservation_id
LEFT JOIN reservation_tables rt ON rt.reservation_id = r.reservation_id
GROUP BY o.order_id, o.reservation_id, r.table_id
HAVING COALESCE(r.table_id, MIN(rt.table_id)) IS NULL;

-- If both legacy assignment representations are populated, the primary table
-- must also be present in reservation_tables. Otherwise the migrated order
-- could point at a table outside the reservation's actual assigned set.
-- Expected result: zero rows.
SELECT r.reservation_id, r.table_id
FROM reservations r
WHERE r.table_id IS NOT NULL
  AND EXISTS (
      SELECT 1
      FROM reservation_tables any_rt
      WHERE any_rt.reservation_id = r.reservation_id
  )
  AND NOT EXISTS (
      SELECT 1
      FROM reservation_tables matching_rt
      WHERE matching_rt.reservation_id = r.reservation_id
        AND matching_rt.table_id = r.table_id
  );

-- Every existing payment must still reference a valid order/reservation.
-- Expected result: zero rows.
SELECT p.payment_id, p.order_id
FROM payments p
LEFT JOIN restaurant_orders o ON o.order_id = p.order_id
LEFT JOIN reservations r ON r.reservation_id = o.reservation_id
WHERE o.order_id IS NULL OR r.reservation_id IS NULL;

-- The legacy model should have at most one order per reservation. If this query
-- returns rows, review them manually before choosing which table owns each order.
SELECT reservation_id, COUNT(*) AS order_count
FROM restaurant_orders
GROUP BY reservation_id
HAVING COUNT(*) > 1;

-- The application guarantees one successful payment per reservation. Do not
-- silently rewrite financial history if legacy data contains duplicates.
-- Expected result: zero rows.
SELECT o.reservation_id, COUNT(*) AS paid_payment_count
FROM payments p
JOIN restaurant_orders o ON o.order_id = p.order_id
WHERE p.status = 'PAID'
GROUP BY o.reservation_id
HAVING COUNT(*) > 1;

-- A historical successful payment must equal the order total that will seed
-- its Bill. Expected result: zero rows. Resolve any mismatch from source
-- records instead of silently rewriting financial history.
SELECT
    p.payment_id,
    p.amount AS paid_amount,
    GREATEST(
        COALESCE(SUM(CASE WHEN oi.status <> 'CANCELLED' THEN oi.subtotal ELSE 0 END), 0.00)
        - LEAST(
            COALESCE(SUM(CASE WHEN oi.status <> 'CANCELLED' THEN oi.subtotal ELSE 0 END), 0.00),
            GREATEST(COALESCE(o.discount_amount, 0.00), 0.00)
        ),
        0.00
    ) AS expected_bill_total
FROM payments p
JOIN restaurant_orders o ON o.order_id = p.order_id
LEFT JOIN restaurant_order_items oi ON oi.order_id = o.order_id
WHERE p.status = 'PAID'
GROUP BY p.payment_id, p.amount, o.discount_amount
HAVING ABS(paid_amount - expected_bill_total) > 0.009;

-- ============================================================================
-- MIGRATION (DDL statements auto-commit in MySQL)
-- ============================================================================

-- 1) Add the table owner to each order and backfill it from the current
-- reservation assignment. The legacy reservations.table_id takes precedence.
ALTER TABLE restaurant_orders
    ADD COLUMN table_id BIGINT NULL AFTER reservation_id;

UPDATE restaurant_orders o
JOIN (
    SELECT
        r.reservation_id,
        COALESCE(r.table_id, MIN(rt.table_id)) AS resolved_table_id
    FROM reservations r
    LEFT JOIN reservation_tables rt ON rt.reservation_id = r.reservation_id
    GROUP BY r.reservation_id, r.table_id
) resolved ON resolved.reservation_id = o.reservation_id
SET o.table_id = resolved.resolved_table_id;

ALTER TABLE restaurant_orders
    MODIFY COLUMN table_id BIGINT NOT NULL,
    ADD INDEX idx_restaurant_orders_table (table_id),
    ADD CONSTRAINT fk_restaurant_orders_table
        FOREIGN KEY (table_id) REFERENCES restaurant_tables (id);

-- Keep a normal reservation index before removing the old one-column UNIQUE
-- index. InnoDB can then continue using an index for its reservation foreign key.
ALTER TABLE restaurant_orders
    ADD INDEX idx_restaurant_orders_reservation (reservation_id);

-- Hibernate/MySQL may have generated an unpredictable name for the old unique
-- reservation_id index, so discover and remove only that one-column unique index.
SET @old_order_reservation_unique := (
    SELECT s.index_name
    FROM information_schema.statistics s
    WHERE s.table_schema = DATABASE()
      AND s.table_name = 'restaurant_orders'
      AND s.non_unique = 0
      AND s.index_name <> 'PRIMARY'
    GROUP BY s.index_name
    HAVING COUNT(*) = 1
       AND MAX(CASE WHEN s.column_name = 'reservation_id' THEN 1 ELSE 0 END) = 1
    LIMIT 1
);
SET @drop_old_order_reservation_unique_sql := IF(
    @old_order_reservation_unique IS NULL,
    'SELECT ''No legacy one-column reservation UNIQUE index found'' AS migration_note',
    CONCAT('ALTER TABLE restaurant_orders DROP INDEX `', @old_order_reservation_unique, '`')
);
PREPARE drop_old_order_reservation_unique_stmt FROM @drop_old_order_reservation_unique_sql;
EXECUTE drop_old_order_reservation_unique_stmt;
DEALLOCATE PREPARE drop_old_order_reservation_unique_stmt;

ALTER TABLE restaurant_orders
    ADD CONSTRAINT uk_restaurant_order_reservation_table
        UNIQUE (reservation_id, table_id);

-- 1b) The legacy model had one representative order even when the reservation
-- owned several rows in reservation_tables. Create an empty audit-safe order
-- for every missing table, cloning only the waiter and lifecycle status from
-- the representative order. Existing dishes always stay with their original
-- table order and are never copied or moved.
INSERT INTO restaurant_orders (
    order_code,
    reservation_id,
    table_id,
    waiter_id,
    public_access_token,
    status,
    note,
    promotion_id,
    discount_amount,
    closed_at,
    version,
    created_at,
    updated_at
)
SELECT
    CONCAT('ORD-MIG-', LEFT(SHA2(CONCAT(rt.reservation_id, ':', rt.table_id), 256), 24)),
    rt.reservation_id,
    rt.table_id,
    seed.waiter_id,
    LOWER(SHA2(CONCAT('order-migration:', rt.reservation_id, ':', rt.table_id, ':', UUID()), 256)),
    seed.status,
    NULL,
    NULL,
    0.00,
    seed.closed_at,
    0,
    COALESCE(seed.created_at, CURRENT_TIMESTAMP(6)),
    COALESCE(seed.updated_at, seed.created_at, CURRENT_TIMESTAMP(6))
FROM reservation_tables rt
JOIN (
    SELECT reservation_id, MIN(order_id) AS seed_order_id
    FROM restaurant_orders
    GROUP BY reservation_id
) seed_ids ON seed_ids.reservation_id = rt.reservation_id
JOIN restaurant_orders seed ON seed.order_id = seed_ids.seed_order_id
LEFT JOIN restaurant_orders existing
    ON existing.reservation_id = rt.reservation_id
   AND existing.table_id = rt.table_id
WHERE existing.order_id IS NULL;

-- 2) Create one bill per reservation. Amounts are snapshots aggregated from all
-- non-cancelled item rows. This script runs against the old one-order model, but
-- the GROUP BY is reservation-safe for reviewed databases that already have more.
CREATE TABLE bills (
    bill_id BIGINT NOT NULL AUTO_INCREMENT,
    bill_code VARCHAR(40) NOT NULL,
    reservation_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    promotion_id BIGINT NULL,
    subtotal DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    discount_amount DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    total DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    locked_at DATETIME(6) NULL,
    paid_at DATETIME(6) NULL,
    version BIGINT NULL DEFAULT 0,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (bill_id),
    CONSTRAINT uk_bills_bill_code UNIQUE (bill_code),
    CONSTRAINT uk_bills_reservation UNIQUE (reservation_id),
    INDEX idx_bills_promotion (promotion_id),
    CONSTRAINT fk_bills_reservation
        FOREIGN KEY (reservation_id) REFERENCES reservations (reservation_id),
    CONSTRAINT fk_bills_promotion
        FOREIGN KEY (promotion_id) REFERENCES promotions (promotion_id)
) ENGINE = InnoDB;

INSERT INTO bills (
    bill_code,
    reservation_id,
    status,
    promotion_id,
    subtotal,
    discount_amount,
    total,
    locked_at,
    paid_at,
    version,
    created_at,
    updated_at
)
SELECT
    CONCAT('BILL-LEGACY-', orders_by_reservation.reservation_id),
    orders_by_reservation.reservation_id,
    CASE
        WHEN payment_summary.has_paid = 1 THEN 'PAID'
        WHEN payment_summary.has_pending = 1 THEN 'PENDING'
        ELSE 'DRAFT'
    END,
    orders_by_reservation.promotion_id,
    orders_by_reservation.subtotal,
    LEAST(
        orders_by_reservation.subtotal,
        GREATEST(orders_by_reservation.discount_amount, 0.00)
    ),
    GREATEST(
        orders_by_reservation.subtotal - LEAST(
            orders_by_reservation.subtotal,
            GREATEST(orders_by_reservation.discount_amount, 0.00)
        ),
        0.00
    ),
    CASE
        WHEN payment_summary.has_paid = 1 OR payment_summary.has_pending = 1
            THEN payment_summary.latest_payment_created_at
        ELSE NULL
    END,
    payment_summary.paid_at,
    0,
    orders_by_reservation.created_at,
    orders_by_reservation.updated_at
FROM (
    SELECT
        o.reservation_id,
        MAX(o.promotion_id) AS promotion_id,
        COALESCE(SUM(item_totals.subtotal), 0.00) AS subtotal,
        COALESCE(MAX(o.discount_amount), 0.00) AS discount_amount,
        MIN(COALESCE(o.created_at, CURRENT_TIMESTAMP(6))) AS created_at,
        MAX(COALESCE(o.updated_at, o.created_at, CURRENT_TIMESTAMP(6))) AS updated_at
    FROM restaurant_orders o
    LEFT JOIN (
        SELECT
            oi.order_id,
            COALESCE(SUM(CASE WHEN oi.status <> 'CANCELLED' THEN oi.subtotal ELSE 0 END), 0.00) AS subtotal
        FROM restaurant_order_items oi
        GROUP BY oi.order_id
    ) item_totals ON item_totals.order_id = o.order_id
    WHERE o.status <> 'CANCELLED'
    GROUP BY o.reservation_id
) orders_by_reservation
LEFT JOIN (
    SELECT
        o.reservation_id,
        MAX(CASE WHEN p.status = 'PAID' THEN 1 ELSE 0 END) AS has_paid,
        MAX(CASE WHEN p.status = 'PENDING' THEN 1 ELSE 0 END) AS has_pending,
        MAX(p.created_at) AS latest_payment_created_at,
        MAX(CASE WHEN p.status = 'PAID' THEN p.paid_at ELSE NULL END) AS paid_at
    FROM restaurant_orders o
    LEFT JOIN payments p ON p.order_id = o.order_id
    GROUP BY o.reservation_id
) payment_summary ON payment_summary.reservation_id = orders_by_reservation.reservation_id;

-- Reservations whose only order was cancelled still need a bill for consistent
-- application reads; their amount is zero and the bill remains DRAFT.
INSERT INTO bills (
    bill_code, reservation_id, status, subtotal, discount_amount, total,
    version, created_at, updated_at
)
SELECT
    CONCAT('BILL-LEGACY-', o.reservation_id),
    o.reservation_id,
    'DRAFT',
    0.00,
    0.00,
    0.00,
    0,
    MIN(COALESCE(o.created_at, CURRENT_TIMESTAMP(6))),
    MAX(COALESCE(o.updated_at, o.created_at, CURRENT_TIMESTAMP(6)))
FROM restaurant_orders o
LEFT JOIN bills b ON b.reservation_id = o.reservation_id
WHERE b.bill_id IS NULL
GROUP BY o.reservation_id;

-- These two columns are kept only for rollback/audit compatibility. New orders
-- no longer map them, so the legacy discount snapshot must not block inserts.
ALTER TABLE restaurant_orders
    MODIFY COLUMN discount_amount DECIMAL(12, 2) NULL DEFAULT 0.00;

-- 3) Re-parent payment attempts from an individual order to its reservation bill.
-- Keep legacy order_id nullable for rollback/audit safety; new code writes bill_id.
ALTER TABLE payments
    ADD COLUMN bill_id BIGINT NULL AFTER payment_id;

UPDATE payments p
JOIN restaurant_orders o ON o.order_id = p.order_id
JOIN bills b ON b.reservation_id = o.reservation_id
SET p.bill_id = b.bill_id;

ALTER TABLE payments
    MODIFY COLUMN bill_id BIGINT NOT NULL,
    MODIFY COLUMN order_id BIGINT NULL,
    ADD INDEX idx_payments_bill (bill_id),
    ADD CONSTRAINT fk_payments_bill
        FOREIGN KEY (bill_id) REFERENCES bills (bill_id);

-- Legacy code reserved a promotion as soon as it was applied to an order. The
-- new rule reserves usage only for PENDING bills and confirms it for PAID bills.
UPDATE promotions p
SET p.used_count = (
    SELECT COUNT(*)
    FROM bills b
    WHERE b.promotion_id = p.promotion_id
      AND b.status IN ('PENDING', 'PAID')
);

-- Post-checks. Expected missing_table_id and missing_bill_id are both zero;
-- duplicate_reservation_table must also return zero rows. Only reservations
-- that have opened at least one order are expected to have an order for every
-- assigned table; reservations that have never opened ordering are ignored.
SELECT COUNT(*) AS missing_table_id
FROM restaurant_orders
WHERE table_id IS NULL;

SELECT COUNT(*) AS missing_bill_id
FROM payments
WHERE bill_id IS NULL;

SELECT reservation_id, table_id, COUNT(*) AS duplicate_count
FROM restaurant_orders
GROUP BY reservation_id, table_id
HAVING COUNT(*) > 1;

SELECT rt.reservation_id, rt.table_id
FROM reservation_tables rt
JOIN (
    SELECT DISTINCT reservation_id
    FROM restaurant_orders
) opened_orders ON opened_orders.reservation_id = rt.reservation_id
LEFT JOIN restaurant_orders o
    ON o.reservation_id = rt.reservation_id
   AND o.table_id = rt.table_id
WHERE o.order_id IS NULL;
