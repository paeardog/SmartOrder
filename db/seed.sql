USE `smartorder`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE `st_dish_daily`;
TRUNCATE TABLE `st_merchant_daily`;
TRUNCATE TABLE `sys_config`;
TRUNCATE TABLE `n_notify_message`;
TRUNCATE TABLE `d_delivery`;
TRUNCATE TABLE `d_rider`;
TRUNCATE TABLE `p_callback_log`;
TRUNCATE TABLE `p_payment`;
TRUNCATE TABLE `o_review`;
TRUNCATE TABLE `o_refund`;
TRUNCATE TABLE `o_order_status_log`;
TRUNCATE TABLE `o_order_item`;
TRUNCATE TABLE `o_order`;
TRUNCATE TABLE `u_cart_item`;
TRUNCATE TABLE `m_dish_stock`;
TRUNCATE TABLE `m_dish_option`;
TRUNCATE TABLE `m_dish_option_group`;
TRUNCATE TABLE `m_dish`;
TRUNCATE TABLE `m_menu_category`;
TRUNCATE TABLE `m_date_override`;
TRUNCATE TABLE `m_business_hours`;
TRUNCATE TABLE `m_merchant`;
TRUNCATE TABLE `u_favorite`;
TRUNCATE TABLE `u_pickup_point`;
TRUNCATE TABLE `u_auth_token`;
TRUNCATE TABLE `u_campus_binding`;
TRUNCATE TABLE `u_sms_code`;
TRUNCATE TABLE `u_verify_apply`;
TRUNCATE TABLE `u_user`;
TRUNCATE TABLE `sys_operation_log`;
TRUNCATE TABLE `sys_account`;

INSERT INTO `sys_account` (`id`, `username`, `password_hash`, `real_name`, `phone`, `role_code`, `merchant_id`, `status`) VALUES
(1, 'admin', '$argon2id$dev-placeholder-replace-before-launch', '平台运营', '13900000001', 'PLATFORM_ADMIN', NULL, 1),
(2, 'chuanxiang', '$argon2id$dev-placeholder-replace-before-launch', '王建国', '13900000002', 'MERCHANT_OWNER', 1, 1),
(3, 'lanzhou', '$argon2id$dev-placeholder-replace-before-launch', '马小龙', '13900000003', 'MERCHANT_OWNER', 2, 1),
(4, 'jido', '$argon2id$dev-placeholder-replace-before-launch', '林小雨', '13900000004', 'MERCHANT_OWNER', 3, 1),
(5, 'chuanxiang02', '$argon2id$dev-placeholder-replace-before-launch', '王小二', '13900000005', 'MERCHANT_STAFF', 1, 1);

INSERT INTO `u_pickup_point` (`id`, `name`, `campus_area`, `building`, `floor_text`, `longitude`, `latitude`, `open_time`, `close_time`, `sort_weight`, `status`) VALUES
(1, '第一食堂', '中心校区', '第一食堂', '一层东侧取餐区', 116.3974280, 39.9092300, '06:30:00', '20:30:00', 100, 1),
(2, '第二食堂', '中心校区', '第二食堂', '二层楼梯口第三窗口', 116.4012300, 39.9123400, '10:30:00', '19:30:00', 90, 1),
(3, '商业街饮品区', '南校区', '学生活动中心', '一层大厅西側', 116.4100200, 39.9001100, '09:00:00', '22:00:00', 80, 1);

INSERT INTO `m_merchant` (`id`, `pickup_point_id`, `name`, `category_code`, `logo_url`, `description`, `contact_name`, `contact_phone`, `license_no`, `min_order_amount`, `pack_fee`, `avg_prepare_minutes`, `month_sold_base`, `rating_score`, `rating_count`, `sort_weight`, `status`) VALUES
(1, 1, '川香盖浇饭', 'RICE', '/static/merchant/1/logo.png', '现炒盖饭,米饭免费加', '王建国', '13900000002', 'JY31101080012345', 0, 100, 12, 4200, 478, 312, 100, 1),
(2, 1, '兰州牛肉面', 'NOODLE', '/static/merchant/2/logo.png', '一清二白三红四绿五黄', '马小龙', '13900000003', 'JY31101080012346', 0, 100, 8, 3800, 465, 268, 90, 1),
(3, 3, '悸动奶茶', 'DRINK', '/static/merchant/3/logo.png', '手作鲜果茶,支持预定', '林小雨', '13900000004', 'JY31101080012347', 800, 0, 5, 6100, 489, 521, 80, 1),
(4, 2, '黄焖鸡米饭', 'RICE', '/static/merchant/4/logo.png', '待审核示例档口', '赵大勺', '13900000006', '', 0, 200, 15, 0, 500, 0, 0, 0);

INSERT INTO `m_business_hours` (`merchant_id`, `day_of_week`, `open_time`, `close_time`, `accept_start`, `accept_end`, `is_closed`) VALUES
(1, 1, '10:00:00', '19:30:00', '10:00:00', '18:45:00', 0),
(1, 2, '10:00:00', '19:30:00', '10:00:00', '18:45:00', 0),
(1, 3, '10:00:00', '19:30:00', '10:00:00', '18:45:00', 0),
(1, 4, '10:00:00', '19:30:00', '10:00:00', '18:45:00', 0),
(1, 5, '10:00:00', '19:30:00', '10:00:00', '18:45:00', 0),
(1, 6, '10:30:00', '19:00:00', '10:30:00', '18:15:00', 0),
(1, 0, '10:30:00', '19:00:00', '10:30:00', '18:15:00', 0),
(2, 1, '06:30:00', '13:00:00', '06:30:00', '12:30:00', 0),
(2, 2, '06:30:00', '13:00:00', '06:30:00', '12:30:00', 0),
(2, 3, '06:30:00', '13:00:00', '06:30:00', '12:30:00', 0),
(2, 4, '06:30:00', '13:00:00', '06:30:00', '12:30:00', 0),
(2, 5, '06:30:00', '13:00:00', '06:30:00', '12:30:00', 0),
(2, 6, '07:00:00', '13:00:00', '07:00:00', '12:30:00', 0),
(2, 0, '00:00:00', '00:00:00', NULL, NULL, 1),
(3, 1, '09:00:00', '21:30:00', '09:00:00', '21:00:00', 0),
(3, 2, '09:00:00', '21:30:00', '09:00:00', '21:00:00', 0),
(3, 3, '09:00:00', '21:30:00', '09:00:00', '21:00:00', 0),
(3, 4, '09:00:00', '21:30:00', '09:00:00', '21:00:00', 0),
(3, 5, '09:00:00', '22:00:00', '09:00:00', '21:30:00', 0),
(3, 6, '09:00:00', '22:00:00', '09:00:00', '21:30:00', 0),
(3, 0, '10:00:00', '21:00:00', '10:00:00', '20:30:00', 0);

INSERT INTO `m_date_override` (`merchant_id`, `biz_date`, `override_type`, `reason`) VALUES
(2, DATE_ADD(CURDATE(), INTERVAL 3 DAY), 'CLOSED', '设备检修,暂停营业一天');

INSERT INTO `m_menu_category` (`id`, `merchant_id`, `name`, `sort_weight`, `status`) VALUES
(1, 1, '招牌盖饭', 100, 1),
(2, 1, '小炒系列', 80, 1),
(3, 1, '汤品加购', 60, 1),
(4, 2, '拉面', 100, 1),
(5, 2, '小菜', 70, 1),
(6, 3, '鲜果茶', 100, 1),
(7, 3, '奶茶', 90, 1);

INSERT INTO `m_dish` (`id`, `merchant_id`, `category_id`, `name`, `subtitle`, `image_url`, `base_price`, `pack_fee`, `min_buy_count`, `max_buy_per_order`, `stock_mode`, `month_sold`, `tags`, `is_recommend`, `sort_weight`, `status`) VALUES
(1, 1, 1, '鱼香肉丝盖饭', '酸甜下饭,现炒', '/static/dish/1.jpg', 1500, 100, 1, 20, 1, 860, '招牌,微辣', 1, 100, 1),
(2, 1, 1, '宫保鸡丁盖饭', '花生米现撒', '/static/dish/2.jpg', 1600, 100, 1, 20, 1, 742, '招牌', 1, 95, 1),
(3, 1, 1, '回锅肉盖饭', '肥而不腻', '/static/dish/3.jpg', 1800, 100, 1, 15, 1, 511, '偏油', 0, 90, 1),
(4, 1, 2, '干锅有机花菜', '可加五花肉', '/static/dish/4.jpg', 2000, 200, 1, 10, 1, 188, '素菜', 0, 80, 1),
(5, 1, 3, '紫菜蛋花汤', '配饭解腻', '/static/dish/5.jpg', 300, 50, 1, 10, 0, 402, '汤', 0, 60, 1),
(6, 2, 4, '牛肉面', '免费加面', '/static/dish/6.jpg', 1400, 100, 1, 10, 1, 1203, '招牌,汤面', 1, 100, 1),
(7, 2, 4, '牛杂面', '限量供应', '/static/dish/7.jpg', 1600, 100, 1, 10, 1, 356, '限量', 0, 90, 2),
(8, 2, 5, '卤蛋', '入味', '/static/dish/8.jpg', 200, 0, 1, 20, 0, 2201, '加购', 0, 50, 1),
(9, 3, 6, '杨枝甘露', '手剥西柚', '/static/dish/9.jpg', 1600, 0, 1, 10, 1, 980, '招牌,冰', 1, 100, 1),
(10, 3, 7, '珍珠奶茶', '鲜奶现调', '/static/dish/10.jpg', 1200, 0, 1, 10, 0, 1560, '经典', 1, 95, 1),
(11, 3, 7, '茉莉奶绿', '茶味偏重', '/static/dish/11.jpg', 1300, 0, 1, 10, 0, 611, '清淡', 0, 90, 1);

INSERT INTO `m_dish_option_group` (`id`, `dish_id`, `name`, `group_type`, `required`, `min_select`, `max_select`, `sort_weight`) VALUES
(1, 1, '辣度', 'SPEC', 1, 1, 1, 100),
(2, 1, '份量', 'SPEC', 1, 1, 1, 90),
(3, 1, '加料', 'TOPPING', 0, 0, 3, 80),
(4, 2, '辣度', 'SPEC', 1, 1, 1, 100),
(5, 2, '份量', 'SPEC', 1, 1, 1, 90),
(6, 6, '辣度', 'SPEC', 1, 1, 1, 100),
(7, 6, '面量', 'SPEC', 1, 1, 1, 90),
(8, 6, '加料', 'TOPPING', 0, 0, 4, 80),
(9, 9, '糖度', 'SPEC', 1, 1, 1, 100),
(10, 9, '冰量', 'SPEC', 1, 1, 1, 90),
(11, 9, '加料', 'TOPPING', 0, 0, 3, 80),
(12, 10, '糖度', 'SPEC', 1, 1, 1, 100),
(13, 10, '冰量', 'SPEC', 1, 1, 1, 90),
(14, 10, '加料', 'TOPPING', 0, 0, 3, 80);

INSERT INTO `m_dish_option` (`group_id`, `name`, `price_delta`, `is_default`, `stock`, `sort_weight`, `status`) VALUES
(1, '不辣', 0, 0, NULL, 100, 1),
(1, '微辣', 0, 1, NULL, 90, 1),
(1, '中辣', 0, 0, NULL, 80, 1),
(1, '特辣', 0, 0, NULL, 70, 1),
(2, '标准份', 0, 1, NULL, 100, 1),
(2, '大份', 300, 0, 40, 90, 1),
(3, '加卤蛋', 200, 0, 60, 100, 1),
(3, '加肉', 500, 0, 25, 90, 1),
(3, '加米饭', 200, 0, NULL, 80, 1),
(4, '不辣', 0, 0, NULL, 100, 1),
(4, '微辣', 0, 1, NULL, 90, 1),
(4, '中辣', 0, 0, NULL, 80, 1),
(5, '标准份', 0, 1, NULL, 100, 1),
(5, '大份', 200, 0, 30, 90, 1),
(6, '不辣', 0, 0, NULL, 100, 1),
(6, '辣', 0, 1, NULL, 90, 1),
(6, '多辣', 0, 0, NULL, 80, 1),
(7, '细面', 0, 1, NULL, 100, 1),
(7, '宽面', 0, 0, NULL, 90, 1),
(7, '毛细', 0, 0, NULL, 80, 1),
(8, '加牛肉', 600, 0, 20, 100, 1),
(8, '加卤蛋', 200, 0, 50, 90, 1),
(8, '加香菜', 0, 0, NULL, 80, 1),
(9, '全糖', 0, 0, NULL, 100, 1),
(9, '七分糖', 0, 1, NULL, 90, 1),
(9, '五分糖', 0, 0, NULL, 80, 1),
(9, '三分糖', 0, 0, NULL, 70, 1),
(9, '无糖', 0, 0, NULL, 60, 1),
(10, '正常冰', 0, 1, NULL, 100, 1),
(10, '少冰', 0, 0, NULL, 90, 1),
(10, '去冰', 0, 0, NULL, 80, 1),
(10, '热饮', 0, 0, NULL, 70, 1),
(11, '西米', 200, 0, 30, 100, 1),
(11, '椰果', 100, 0, 40, 90, 1),
(11, '芝士奶盖', 400, 0, 18, 80, 1),
(12, '全糖', 0, 0, NULL, 100, 1),
(12, '七分糖', 0, 1, NULL, 90, 1),
(12, '五分糖', 0, 0, NULL, 80, 1),
(12, '三分糖', 0, 0, NULL, 70, 1),
(13, '正常冰', 0, 1, NULL, 100, 1),
(13, '少冰', 0, 0, NULL, 90, 1),
(13, '热饮', 0, 0, NULL, 80, 1),
(14, '珍珠', 100, 0, 80, 100, 1),
(14, '椰果', 100, 0, 60, 90, 1),
(14, '芋圆', 200, 0, 22, 80, 1);

INSERT INTO `m_dish_stock` (`dish_id`, `biz_date`, `stock_total`, `stock_locked`, `stock_sold`) VALUES
(1, CURDATE(), 120, 3, 41),
(2, CURDATE(), 120, 1, 38),
(3, CURDATE(), 80, 0, 22),
(4, CURDATE(), 40, 0, 6),
(6, CURDATE(), 150, 2, 66),
(7, CURDATE(), 60, 0, 60),
(9, CURDATE(), 90, 0, 31),
(10, CURDATE(), 999, 0, 55),
(11, CURDATE(), 999, 0, 18);

INSERT INTO `u_user` (`id`, `phone`, `password_hash`, `nickname`, `avatar_url`, `real_name`, `student_no`, `college`, `campus_area`, `dorm_building`, `auth_source`, `verify_status`, `credit_score`, `order_count`, `status`) VALUES
(1, '13800000001', '', 'doge', '', '张狗蛋', '2022010101', '计算机学院', '中心校区', '紫荆公寓 3 栋', 1, 2, 100, 27, 1),
(2, '13800000002', '', '小鹿', '', '李鹿', '2022010102', '软件学院', '中心校区', '紫荆公寓 5 栋', 1, 2, 96, 8, 1),
(3, NULL, '', '未认证访客', '', '', '', '', '南校区', '', 2, 0, 100, 0, 1);

INSERT INTO `u_campus_binding` (`user_id`, `sso_provider`, `sso_uid`, `last_login_at`) VALUES
(1, 'CAS', '2022010101', NOW(3)),
(2, 'CAS', '2022010102', NOW(3));

INSERT INTO `u_favorite` (`user_id`, `target_type`, `target_id`) VALUES
(1, 'MERCHANT', 1),
(1, 'MERCHANT', 3),
(1, 'DISH', 6),
(2, 'MERCHANT', 3);

INSERT INTO `u_cart_item` (`user_id`, `merchant_id`, `dish_id`, `option_json`, `option_hash`, `quantity`, `selected`) VALUES
(1, 1, 1, JSON_ARRAY(JSON_OBJECT('group_id', 1, 'option_id', 2, 'name', '微辣', 'price_delta', 0), JSON_OBJECT('group_id', 2, 'option_id', 5, 'name', '标准份', 'price_delta', 0)), MD5('1:2,2:5'), 1, 1),
(1, 1, 5, JSON_ARRAY(), MD5(''), 2, 1);

INSERT INTO `o_order` (`id`, `order_no`, `client_order_no`, `user_id`, `merchant_id`, `pickup_point_id`, `fulfillment_type`, `biz_date`, `meal_period`, `status`, `item_amount`, `pack_fee`, `delivery_fee`, `discount_amount`, `pay_amount`, `pickup_code`, `expect_take_at`, `pickup_deadline_at`, `taken_at`, `expire_at`, `paid_at`, `accepted_at`, `ready_at`, `finished_at`, `cancelled_at`, `cancel_reason`, `cancel_by`, `merchant_name_snapshot`, `pickup_point_name_snapshot`, `contact_name`, `contact_phone`, `remark`, `item_count`, `version`) VALUES
(1, CONCAT(DATE_FORMAT(CURDATE(), '%Y%m%d'), '1001000001'), 'seed-client-no-0001', 1, 1, 1, 'PICKUP', CURDATE(), 'LUNCH', 'READY', 2600, 200, 0, 0, 2800, '1024', DATE_ADD(NOW(3), INTERVAL 5 MINUTE), DATE_ADD(NOW(3), INTERVAL 45 MINUTE), NULL, DATE_ADD(NOW(3), INTERVAL 15 MINUTE), DATE_SUB(NOW(3), INTERVAL 10 MINUTE), DATE_SUB(NOW(3), INTERVAL 8 MINUTE), DATE_SUB(NOW(3), INTERVAL 1 MINUTE), NULL, NULL, '', '', '川香盖浇饭', '第一食堂', '张狗蛋', '13800000001', '不要葱', 3, 4),
(2, CONCAT(DATE_FORMAT(CURDATE(), '%Y%m%d'), '1001000002'), 'seed-client-no-0002', 2, 1, 1, 'PICKUP', CURDATE(), 'LUNCH', 'PENDING_PAYMENT', 2200, 200, 0, 0, 2400, NULL, NULL, NULL, NULL, DATE_ADD(NOW(3), INTERVAL 12 MINUTE), NULL, NULL, NULL, NULL, NULL, '', '', '川香盖浇饭', '第一食堂', '李鹿', '13800000002', '', 3, 0),
(3, CONCAT(DATE_FORMAT(CURDATE(), '%Y%m%d'), '1003000003'), 'seed-client-no-0003', 1, 3, 3, 'PICKUP', CURDATE(), 'NIGHT', 'ACCEPTED', 3200, 0, 0, 300, 2900, '2048', DATE_ADD(NOW(3), INTERVAL 6 MINUTE), DATE_ADD(NOW(3), INTERVAL 60 MINUTE), NULL, DATE_ADD(NOW(3), INTERVAL 15 MINUTE), DATE_SUB(NOW(3), INTERVAL 4 MINUTE), DATE_SUB(NOW(3), INTERVAL 3 MINUTE), NULL, NULL, NULL, '', '', '悸动奶茶', '商业街饮品区', '张狗蛋', '13800000001', '少冰', 2, 2),
(4, CONCAT(DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '%Y%m%d'), '1002000004'), 'seed-client-no-0004', 1, 2, 1, 'PICKUP', DATE_SUB(CURDATE(), INTERVAL 1 DAY), 'BREAKFAST', 'COMPLETED', 2000, 100, 0, 0, 2100, '0731', DATE_SUB(NOW(3), INTERVAL 22 HOUR), DATE_SUB(NOW(3), INTERVAL 21 HOUR), DATE_SUB(NOW(3), INTERVAL 21 HOUR), DATE_SUB(NOW(3), INTERVAL 23 HOUR), DATE_SUB(NOW(3), INTERVAL 23 HOUR), DATE_SUB(NOW(3), INTERVAL 22 HOUR), DATE_SUB(NOW(3), INTERVAL 21 HOUR), DATE_SUB(NOW(3), INTERVAL 21 HOUR), NULL, '', '', '兰州牛肉面', '第一食堂', '张狗蛋', '13800000001', '多放辣', 3, 5),
(5, CONCAT(DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '%Y%m%d'), '1001000005'), 'seed-client-no-0005', 2, 1, 1, 'PICKUP', DATE_SUB(CURDATE(), INTERVAL 1 DAY), 'DINNER', 'CANCELLED', 1600, 100, 0, 0, 1700, NULL, NULL, NULL, NULL, DATE_SUB(NOW(3), INTERVAL 20 HOUR), NULL, NULL, NULL, NULL, DATE_SUB(NOW(3), INTERVAL 20 HOUR), '点错了', 'USER', '川香盖浇饭', '第一食堂', '李鹿', '13800000002', '', 1, 2);

INSERT INTO `o_order_item` (`order_id`, `order_no`, `dish_id`, `dish_name`, `dish_image`, `category_name`, `option_text`, `option_json`, `unit_price`, `origin_unit_price`, `pack_fee`, `discount_amount`, `quantity`, `line_amount`) VALUES
(1, (SELECT `order_no` FROM `o_order` WHERE `id` = 1), 1, '鱼香肉丝盖饭', '/static/dish/1.jpg', '招牌盖饭', '微辣 / 大份 / 加卤蛋', JSON_ARRAY(JSON_OBJECT('group_id', 1, 'option_id', 2, 'name', '微辣', 'price_delta', 0), JSON_OBJECT('group_id', 2, 'option_id', 6, 'name', '大份', 'price_delta', 300), JSON_OBJECT('group_id', 3, 'option_id', 7, 'name', '加卤蛋', 'price_delta', 200)), 2000, 2000, 100, 0, 1, 2100),
(1, (SELECT `order_no` FROM `o_order` WHERE `id` = 1), 5, '紫菜蛋花汤', '/static/dish/5.jpg', '汤品加购', '', JSON_ARRAY(), 300, 300, 100, 0, 2, 700),
(2, (SELECT `order_no` FROM `o_order` WHERE `id` = 2), 2, '宫保鸡丁盖饭', '/static/dish/2.jpg', '招牌盖饭', '微辣 / 标准份', JSON_ARRAY(JSON_OBJECT('group_id', 4, 'option_id', 11, 'name', '微辣', 'price_delta', 0), JSON_OBJECT('group_id', 5, 'option_id', 13, 'name', '标准份', 'price_delta', 0)), 1600, 1600, 100, 0, 1, 1700),
(2, (SELECT `order_no` FROM `o_order` WHERE `id` = 2), 5, '紫菜蛋花汤', '/static/dish/5.jpg', '汤品加购', '', JSON_ARRAY(), 300, 300, 100, 0, 2, 700),
(3, (SELECT `order_no` FROM `o_order` WHERE `id` = 3), 9, '杨枝甘露', '/static/dish/9.jpg', '鲜果茶', '七分糖 / 少冰 / 芝士奶盖', JSON_ARRAY(JSON_OBJECT('group_id', 9, 'option_id', 25, 'name', '七分糖', 'price_delta', 0), JSON_OBJECT('group_id', 10, 'option_id', 30, 'name', '少冰', 'price_delta', 0), JSON_OBJECT('group_id', 11, 'option_id', 35, 'name', '芝士奶盖', 'price_delta', 400)), 2000, 2000, 0, 300, 1, 1700),
(3, (SELECT `order_no` FROM `o_order` WHERE `id` = 3), 10, '珍珠奶茶', '/static/dish/10.jpg', '奶茶', '七分糖 / 正常冰', JSON_ARRAY(JSON_OBJECT('group_id', 12, 'option_id', 37, 'name', '七分糖', 'price_delta', 0), JSON_OBJECT('group_id', 13, 'option_id', 40, 'name', '正常冰', 'price_delta', 0)), 1200, 1200, 0, 0, 1, 1200),
(4, (SELECT `order_no` FROM `o_order` WHERE `id` = 4), 6, '牛肉面', '/static/dish/6.jpg', '拉面', '辣 / 细面 / 加卤蛋', JSON_ARRAY(JSON_OBJECT('group_id', 6, 'option_id', 16, 'name', '辣', 'price_delta', 0), JSON_OBJECT('group_id', 7, 'option_id', 18, 'name', '细面', 'price_delta', 0), JSON_OBJECT('group_id', 8, 'option_id', 22, 'name', '加卤蛋', 'price_delta', 200)), 1600, 1600, 100, 0, 1, 1700),
(4, (SELECT `order_no` FROM `o_order` WHERE `id` = 4), 8, '卤蛋', '/static/dish/8.jpg', '小菜', '', JSON_ARRAY(), 200, 200, 0, 0, 2, 400),
(5, (SELECT `order_no` FROM `o_order` WHERE `id` = 5), 2, '宫保鸡丁盖饭', '/static/dish/2.jpg', '招牌盖饭', '微辣 / 标准份', JSON_ARRAY(JSON_OBJECT('group_id', 4, 'option_id', 11, 'name', '微辣', 'price_delta', 0), JSON_OBJECT('group_id', 5, 'option_id', 13, 'name', '标准份', 'price_delta', 0)), 1600, 1600, 100, 0, 1, 1700);

INSERT INTO `o_order_status_log` (`order_id`, `from_status`, `to_status`, `operator_type`, `operator_id`, `operator_name`, `note`) VALUES
(1, NULL, 'PENDING_PAYMENT', 'USER', 1, '张狗蛋', '提交订单'),
(1, 'PENDING_PAYMENT', 'PAID', 'SYSTEM', NULL, '支付回调', 'MOCK 渠道支付成功'),
(1, 'PAID', 'ACCEPTED', 'MERCHANT', 2, '王建国', '已接单'),
(1, 'ACCEPTED', 'COOKING', 'MERCHANT', 5, '王小二', '开始制作'),
(1, 'COOKING', 'READY', 'MERCHANT', 5, '王小二', '出餐完成,取餐码 1024'),
(2, NULL, 'PENDING_PAYMENT', 'USER', 2, '李鹿', '提交订单'),
(3, NULL, 'PENDING_PAYMENT', 'USER', 1, '张狗蛋', '提交订单'),
(3, 'PENDING_PAYMENT', 'PAID', 'SYSTEM', NULL, '支付回调', 'MOCK 渠道支付成功'),
(3, 'PAID', 'ACCEPTED', 'MERCHANT', 4, '林小雨', '已接单'),
(4, NULL, 'PENDING_PAYMENT', 'USER', 1, '张狗蛋', '提交订单'),
(4, 'PENDING_PAYMENT', 'PAID', 'SYSTEM', NULL, '支付回调', 'MOCK 渠道支付成功'),
(4, 'PAID', 'ACCEPTED', 'MERCHANT', 3, '马小龙', '已接单'),
(4, 'ACCEPTED', 'COOKING', 'MERCHANT', 3, '马小龙', '开始制作'),
(4, 'COOKING', 'READY', 'MERCHANT', 3, '马小龙', '出餐完成,取餐码 0731'),
(4, 'READY', 'COMPLETED', 'MERCHANT', 3, '马小龙', '扫码核销'),
(5, NULL, 'PENDING_PAYMENT', 'USER', 2, '李鹿', '提交订单'),
(5, 'PENDING_PAYMENT', 'CANCELLED', 'USER', 2, '李鹿', '用户主动取消:点错了');

INSERT INTO `p_payment` (`order_id`, `user_id`, `pay_amount`, `channel`, `status`, `payment_no`, `channel_txn_no`, `paid_at`, `client_ip`) VALUES
(1, 1, 2800, 'MOCK', 'SUCCESS', CONCAT('P', DATE_FORMAT(CURDATE(), '%Y%m%d'), '0000000001'), 'MOCKTXN0000000000000001', DATE_SUB(NOW(3), INTERVAL 10 MINUTE), '10.16.32.101'),
(3, 1, 2900, 'MOCK', 'SUCCESS', CONCAT('P', DATE_FORMAT(CURDATE(), '%Y%m%d'), '0000000002'), 'MOCKTXN0000000000000002', DATE_SUB(NOW(3), INTERVAL 4 MINUTE), '10.16.32.101'),
(4, 1, 2100, 'MOCK', 'SUCCESS', CONCAT('P', DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '%Y%m%d'), '0000000003'), 'MOCKTXN0000000000000003', DATE_SUB(NOW(3), INTERVAL 23 HOUR), '10.16.32.101');

INSERT INTO `o_review` (`order_id`, `user_id`, `merchant_id`, `rating_score`, `taste_score`, `speed_score`, `content`, `is_anonymous`, `reply_content`, `replied_at`) VALUES
(4, 1, 2, 5, 5, 4, '出餐快,面可以再多点', 0, '感谢支持,大份面免费加', DATE_SUB(NOW(3), INTERVAL 20 HOUR));

INSERT INTO `n_notify_message` (`user_id`, `scene`, `channel`, `title`, `content`, `biz_type`, `biz_id`) VALUES
(1, 'ORDER_READY', 'INAPP', '可以取餐了', '川香盖浇饭 已出餐,取餐码 1024,请及时取餐', 'ORDER', (SELECT `order_no` FROM `o_order` WHERE `id` = 1)),
(1, 'ORDER_ACCEPTED', 'INAPP', '商家已接单', '悸动奶茶 已接单,预计 6 分钟后可取', 'ORDER', (SELECT `order_no` FROM `o_order` WHERE `id` = 3)),
(2, 'ORDER_CANCELLED', 'INAPP', '订单已取消', '你的订单已成功取消', 'ORDER', (SELECT `order_no` FROM `o_order` WHERE `id` = 5));

INSERT INTO `st_merchant_daily` (`merchant_id`, `biz_date`, `order_count`, `paid_count`, `cancel_count`, `turnover_amount`, `net_amount`, `avg_prepare_seconds`) VALUES
(1, CURDATE(), 2, 1, 0, 2800, 2800, 540),
(2, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 1, 1, 0, 2100, 2100, 300),
(1, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 1, 0, 1, 0, 0, 0),
(3, CURDATE(), 1, 1, 0, 2900, 2900, 360);

INSERT INTO `st_dish_daily` (`dish_id`, `merchant_id`, `biz_date`, `sold_count`, `amount_sum`, `stock_total`, `stock_sold_out`) VALUES
(1, 1, CURDATE(), 41, 82000, 120, 0),
(6, 2, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 66, 105600, 150, 0),
(9, 3, CURDATE(), 31, 52700, 90, 0),
(7, 2, CURDATE(), 60, 96000, 60, 1);

INSERT INTO `sys_config` (`config_key`, `config_value`, `value_type`, `name`, `description`, `is_public`, `editor_account_id`) VALUES
('ORDER_PAY_TIMEOUT_SECONDS', '900', 'INT', '支付超时秒数', '未支付订单自动关闭的等待时长', 1, 1),
('ORDER_AUTO_COMPLETE_MINUTES', '60', 'INT', '超时未取自完成分钟数', '出餐后多久无人领取自动置为已完成', 1, 1),
('MERCHANT_MAX_ACTIVE_ORDERS', '40', 'INT', '商家同时进行中单量上限', '超载后商家端提示暂缓接单', 0, 1),
('CART_EXPIRE_HOURS', '24', 'INT', '购物车有效小时数', '超过此时长未结算的购物车行自动清理', 1, 1),
('OPEN_CAMPUS_AREAS', '中心校区,南校区', 'STRING', '已开放校区', '不在列表内的校区首页不展示档口', 1, 1),
('PAYMENT_ENABLED_CHANNELS', 'MOCK', 'STRING', '已启用支付渠道', '逗号分隔,拿到商户号后追加 WXPAY_H5', 1, 1),
('USER_CANCEL_WINDOW_SECONDS', '180', 'INT', '用户自助取消窗口', '支付后允许无理由自助取消的秒数,超时走退款申请', 1, 1),
('MIN_VERIFY_STATUS_TO_ORDER', '2', 'INT', '可下单的最低认证状态', '设为 0 则允许游客下单,用于内测放量', 1, 1);

SET FOREIGN_KEY_CHECKS = 1;
