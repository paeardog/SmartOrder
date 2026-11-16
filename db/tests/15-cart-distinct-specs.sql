INSERT INTO u_cart_item (user_id, merchant_id, dish_id, option_json, option_hash, quantity)
VALUES (1, 1, 1, JSON_ARRAY(JSON_OBJECT('option_id', 2, 'name', '微辣')), MD5('probe-A'), 1);
SELECT ROW_COUNT() AS first_cart_row_should_be_1;
INSERT INTO u_cart_item (user_id, merchant_id, dish_id, option_json, option_hash, quantity)
VALUES (1, 1, 1, JSON_ARRAY(JSON_OBJECT('option_id', 9, 'name', '去葱')), MD5('probe-B'), 2);
SELECT COUNT(*) AS two_specs_two_rows FROM u_cart_item WHERE user_id = 1 AND dish_id = 1 AND option_hash IN (MD5('probe-A'), MD5('probe-B'));
