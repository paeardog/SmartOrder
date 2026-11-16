-- 学号密码登录与营销数据落库
-- 学生 user 3/5 是短信登录隐式注册的访客,没有学号,置 NULL 以让唯一索引容纳多个空值
ALTER TABLE `u_user`
    MODIFY COLUMN `student_no` VARCHAR(32) NULL COMMENT '学号,密码登录账号;无学号为 NULL';

UPDATE `u_user` SET `student_no` = NULL WHERE `student_no` = '';

ALTER TABLE `u_user` DROP INDEX `idx_u_user_student_no`;
ALTER TABLE `u_user` ADD UNIQUE KEY `uk_u_user_student_no` (`student_no`);

-- 种子学生的初始密码为学号后 6 位,上线前必须全部重置
UPDATE `u_user`
   SET `password_hash` = '$pbkdf2-sha256$120000$KrwmkuZYnoiUBMOipunhYA==$TVt1g/3rVvippGHH3Xgv5n2eCgdkSzbD8UyK6gLuzyQ=',
       `auth_source` = 3
 WHERE `student_no` = '2022010101';

UPDATE `u_user`
   SET `password_hash` = '$pbkdf2-sha256$120000$P6rSLLZbEjkQHuXctfqR+Q==$XnADYOEnGiXu4pfLNGgYUajdS3+nUqPLlkdm8lY2Bbo=',
       `auth_source` = 3
 WHERE `student_no` = '2022010102';

ALTER TABLE `o_order`
    ADD COLUMN `coupon_id` BIGINT UNSIGNED NULL COMMENT '本单核销的用户券 id,未用券为 NULL' AFTER `discount_amount`;

CREATE TABLE IF NOT EXISTS `u_address` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`       BIGINT UNSIGNED NOT NULL,
  `contact_name`  VARCHAR(32)     NOT NULL COMMENT '联系人',
  `contact_phone` VARCHAR(20)     NOT NULL COMMENT '联系电话',
  `campus_area`   VARCHAR(64)     NOT NULL COMMENT '校区',
  `building`      VARCHAR(64)     NOT NULL COMMENT '楼栋',
  `room_no`       VARCHAR(32)     NOT NULL COMMENT '寝室号',
  `tag`           VARCHAR(16)     NOT NULL DEFAULT '' COMMENT '宿舍/家 等标签',
  `remark`        VARCHAR(255)    NOT NULL DEFAULT '' COMMENT '门牌补充说明',
  `is_default`    TINYINT         NOT NULL DEFAULT 0 COMMENT '1 默认地址,每用户至多一条',
  `deleted_at`    DATETIME(3)     NULL COMMENT '软删除时间',
  `created_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_u_address_user` (`user_id`, `deleted_at`, `is_default`),
  CONSTRAINT `fk_u_address_user` FOREIGN KEY (`user_id`) REFERENCES `u_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收货地址簿';

CREATE TABLE IF NOT EXISTS `mk_coupon_template` (
  `id`                  BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
  `name`                VARCHAR(64)      NOT NULL COMMENT '券名称',
  `subtitle`            VARCHAR(128)     NOT NULL DEFAULT '' COMMENT '使用范围说明',
  `kind`                VARCHAR(16)      NOT NULL COMMENT 'CASH 满减 DISCOUNT 折扣 SHIPPING 运费券',
  `threshold_amount`    INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '用券门槛分',
  `discount_amount`     INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT 'CASH/SHIPPING 抵扣分',
  `discount_rate`       INT UNSIGNED     NOT NULL DEFAULT 1000 COMMENT 'DISCOUNT 千分比实付,880 即 8.8 折',
  `max_discount_amount` INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '最高抵扣分,0 表示不设上限',
  `scope_merchant_ids`  VARCHAR(255)     NOT NULL DEFAULT '' COMMENT '限定档口 id 逗号分隔,空为全场',
  `valid_days`          INT UNSIGNED     NOT NULL DEFAULT 30 COMMENT '领取后有效天数',
  `status`              TINYINT          NOT NULL DEFAULT 1 COMMENT '1 上架 0 停用',
  `created_at`          DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`          DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_mk_template_status` (`status`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠券模板';

CREATE TABLE IF NOT EXISTS `mk_user_coupon` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT UNSIGNED NOT NULL,
  `template_id` BIGINT UNSIGNED NOT NULL,
  `state`       VARCHAR(16)     NOT NULL DEFAULT 'UNUSED' COMMENT 'UNUSED/USED/EXPIRED',
  `order_no`    VARCHAR(32)     NOT NULL DEFAULT '' COMMENT '核销订单号',
  `source`      VARCHAR(32)     NOT NULL DEFAULT '' COMMENT '发放来源',
  `start_at`    DATETIME(3)     NOT NULL COMMENT '生效时间',
  `end_at`      DATETIME(3)     NOT NULL COMMENT '失效时间',
  `used_at`     DATETIME(3)     NULL COMMENT '核销时间',
  `created_at`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_mk_user_coupon` (`user_id`, `state`, `end_at`),
  CONSTRAINT `fk_mk_coupon_user` FOREIGN KEY (`user_id`) REFERENCES `u_user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_mk_coupon_template` FOREIGN KEY (`template_id`) REFERENCES `mk_coupon_template` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户持有券';

INSERT INTO `mk_coupon_template`
  (`id`, `name`, `subtitle`, `kind`, `threshold_amount`, `discount_amount`, `discount_rate`,
   `max_discount_amount`, `scope_merchant_ids`, `valid_days`, `status`) VALUES
  (1, '全场满 20 减 5', '全部档口可用 · 每单限用一张', 'CASH', 2000, 500, 1000, 500, '', 30, 1),
  (2, '饮品 8.8 折', '仅限饮品档口 · 最高抵 6 元', 'DISCOUNT', 1000, 0, 880, 600, '3', 15, 1),
  (3, '配送费减免 3 元', '宿舍配送可用', 'SHIPPING', 0, 300, 1000, 300, '', 7, 1),
  (4, '开学季满 30 减 10', '全部档口可用', 'CASH', 3000, 1000, 1000, 1000, '', 60, 1);

INSERT INTO `mk_user_coupon`
  (`user_id`, `template_id`, `state`, `order_no`, `source`, `start_at`, `end_at`, `used_at`) VALUES
  (1, 1, 'UNUSED', '', '新人礼包', DATE_SUB(NOW(3), INTERVAL 2 DAY), DATE_ADD(NOW(3), INTERVAL 12 DAY), NULL),
  (1, 2, 'UNUSED', '', '档口专享', DATE_SUB(NOW(3), INTERVAL 1 DAY), DATE_ADD(NOW(3), INTERVAL 5 DAY), NULL),
  (1, 3, 'UNUSED', '', '雨天补贴', DATE_SUB(NOW(3), INTERVAL 3 DAY), DATE_ADD(NOW(3), INTERVAL 2 DAY), NULL),
  (1, 4, 'USED', '2026111000010001', '签到礼包', DATE_SUB(NOW(3), INTERVAL 12 DAY), DATE_SUB(NOW(3), INTERVAL 4 DAY), DATE_SUB(NOW(3), INTERVAL 4 DAY)),
  (2, 1, 'UNUSED', '', '新人礼包', DATE_SUB(NOW(3), INTERVAL 1 DAY), DATE_ADD(NOW(3), INTERVAL 20 DAY), NULL);

INSERT INTO `u_address`
  (`user_id`, `contact_name`, `contact_phone`, `campus_area`, `building`, `room_no`, `tag`, `remark`, `is_default`) VALUES
  (1, '张狗蛋', '13800000001', '中心校区', '紫荆公寓 3 栋', '412', '宿舍', '楼下有快递柜,放门口即可', 1),
  (1, '张狗蛋', '13800000001', '中心校区', '图书馆', '1F 大厅', '其他', '工作日 18 点后无人', 0),
  (2, '李鹿', '13800000002', '中心校区', '紫荆公寓 5 栋', '208', '宿舍', '', 1);
