USE restaurant_management_system;

ALTER TABLE qr_orders
    ADD INDEX idx_qr_orders_session (session_id),
    ADD INDEX idx_qr_orders_restaurant_order (restaurant_order_id),
    ADD CONSTRAINT fk_qr_orders_session FOREIGN KEY (session_id) REFERENCES qr_sessions(session_id),
    ADD CONSTRAINT fk_qr_orders_table FOREIGN KEY (table_id) REFERENCES restaurant_tables(id),
    ADD CONSTRAINT fk_qr_orders_customer FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
    ADD CONSTRAINT fk_qr_orders_reservation FOREIGN KEY (reservation_id) REFERENCES reservations(reservation_id),
    ADD CONSTRAINT fk_qr_orders_created_by FOREIGN KEY (created_by) REFERENCES users(user_id),
    ADD CONSTRAINT fk_qr_orders_restaurant_order FOREIGN KEY (restaurant_order_id) REFERENCES restaurant_orders(order_id);

ALTER TABLE qr_order_items
    ADD INDEX idx_qr_order_items_order (order_id),
    ADD INDEX idx_qr_order_items_menu_item (item_id),
    ADD CONSTRAINT fk_qr_order_items_order FOREIGN KEY (order_id) REFERENCES qr_orders(order_id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_qr_order_items_menu_item FOREIGN KEY (item_id) REFERENCES restaurant_menu_items(id);
