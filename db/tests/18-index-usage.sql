EXPLAIN SELECT id, order_no FROM o_order WHERE status = 'PENDING_PAYMENT' AND expire_at < NOW(3) ORDER BY expire_at LIMIT 200;
EXPLAIN SELECT id, order_no FROM o_order WHERE merchant_id = 1 AND status = 'PAID' AND biz_date = CURDATE() ORDER BY id DESC LIMIT 50;
EXPLAIN SELECT id, order_no FROM o_order WHERE user_id = 1 AND biz_date = CURDATE() ORDER BY id DESC LIMIT 20;
EXPLAIN SELECT id FROM o_order WHERE merchant_id = 1 AND biz_date = CURDATE() AND pickup_code = '1024';
SELECT COUNT(*) AS timeout_candidates FROM o_order WHERE status = 'PENDING_PAYMENT' AND expire_at < NOW(3);
