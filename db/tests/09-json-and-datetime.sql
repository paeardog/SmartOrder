SELECT id, JSON_LENGTH(option_json) AS n_opt, JSON_UNQUOTE(JSON_EXTRACT(option_json, '$[0].name')) AS first_name,
       JSON_UNQUOTE(JSON_EXTRACT(option_json, '$[1].price_delta')) AS second_delta
FROM o_order_item WHERE order_id = 1 ORDER BY id LIMIT 1;
SELECT id, CHAR_LENGTH(client_order_no) AS len, DATE_FORMAT(expire_at, '%Y-%m-%d %H:%i:%s.%f') AS expire_ms
FROM o_order WHERE id = 1;
SELECT NOW(3) AS db_now, @@session.time_zone AS sess_tz, @@global.time_zone AS glob_tz;
SELECT @@session.transaction_isolation AS isolation, @@innodb_autoinc_lock_mode AS autoinc_mode;
