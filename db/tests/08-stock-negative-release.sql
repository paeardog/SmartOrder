UPDATE m_dish_stock SET stock_locked = stock_locked - 500 WHERE dish_id = 7 AND biz_date = CURDATE();
