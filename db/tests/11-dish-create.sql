INSERT INTO m_dish (merchant_id, category_id, name, base_price) VALUES (1, 1, '探针测试菜', 1000);
SELECT COUNT(*) AS active_rows FROM m_dish WHERE merchant_id = 1 AND name = '探针测试菜' AND deleted_flag = 0;
