UPDATE o_order SET status='ACCEPTED', accepted_at=NOW(3), version=version+1 WHERE id=1 AND status='PAID';
SELECT ROW_COUNT() AS cas_wrong_from_should_be_0;
UPDATE o_order SET status='COMPLETED', finished_at=NOW(3), version=version+1 WHERE id=1 AND status='READY';
SELECT ROW_COUNT() AS cas_right_from_should_be_1;
UPDATE o_order SET status='COMPLETED', version=version+1 WHERE id=1 AND status='READY';
SELECT ROW_COUNT() AS cas_repeat_should_be_0;
SELECT id, status, version FROM o_order WHERE id=1;
