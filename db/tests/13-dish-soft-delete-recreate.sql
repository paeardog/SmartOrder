UPDATE m_dish SET deleted_flag = id WHERE merchant_id = 1 AND name = '探针测试菜' AND deleted_flag = 0;
SELECT ROW_COUNT() AS soft_deleted_should_be_1;
INSERT INTO m_dish (merchant_id, category_id, name, base_price) VALUES (1, 1, '探针测试菜', 1000);
SELECT COUNT(*) AS active_after_recreate FROM m_dish WHERE merchant_id = 1 AND name = '探针测试菜' AND deleted_flag = 0;
SELECT COUNT(*) AS tombstones FROM m_dish WHERE merchant_id = 1 AND name = '探针测试菜' AND deleted_flag <> 0;
