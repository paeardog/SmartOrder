INSERT INTO o_order (order_no, user_id, merchant_id, pickup_point_id, fulfillment_type, biz_date, meal_period, status, item_amount, pack_fee, delivery_fee, discount_amount, pay_amount, client_order_no, merchant_name_snapshot, expire_at)
VALUES ('900000000000000031', 1, 1, 1, 'PICKUP', CURDATE(), 'LUNCH', 'PENDING_PAYMENT', 100, 0, 0, 0, 100, 'probe-c-001', '川香盖浇饭', NOW(3));
