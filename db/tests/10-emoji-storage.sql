INSERT INTO o_review (order_id, user_id, merchant_id, rating_score, taste_score, speed_score, content, is_anonymous, reply_content)
VALUES (1, 1, 1, 5, 4, 5, '汤好喝 🍜 加辣更香 😋', 0, '');
SELECT id, content, CHAR_LENGTH(content) AS chars, LENGTH(content) AS bytes, HEX(SUBSTRING(content, 9, 4)) AS emoji_hex FROM o_review WHERE order_id = 1;
