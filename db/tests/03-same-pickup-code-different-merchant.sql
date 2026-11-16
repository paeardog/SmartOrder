INSERT INTO o_order (order_no, user_id, merchant_id, pickup_point_id, fulfillment_type, biz_date, meal_period, status, item_amount, pack_fee, delivery_fee, discount_amount, pay_amount, client_order_no, pickup_code, merchant_name_snapshot, expire_at)
VALUES
 ('900000000000000021', 1, 1, 1, 'PICKUP', CURDATE(), 'LUNCH', 'READY', 100, 0, 0, 0, 100, 'probe-c-021', '7777', '川香盖浇饭', NOW(3)),
 ('900000000000000022', 2, 2, 1, 'PICKUP', CURDATE(), 'LUNCH', 'READY', 100, 0, 0, 0, 100, 'probe-c-022', '7777', '兰州牛肉面', NOW(3));
SELECT COUNT(*) AS ok_rows FROM o_order WHERE pickup_code='7777' AND biz_date=CURDATE();
