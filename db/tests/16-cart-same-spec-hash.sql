INSERT INTO u_cart_item (user_id, merchant_id, dish_id, option_json, option_hash, quantity)
VALUES (1, 1, 1, JSON_ARRAY(JSON_OBJECT('option_id', 2, 'name', '微辣')), MD5('probe-A'), 5);
