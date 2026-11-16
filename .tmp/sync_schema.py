import io

path = "db/schema.sql"
with io.open(path, encoding="utf-8") as f:
    lines = f.readlines()

address_block = """
CREATE TABLE `u_address` (
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
""".lstrip("\n")

coupon_block = """
CREATE TABLE `mk_coupon_template` (
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

CREATE TABLE `mk_user_coupon` (
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
""".lstrip("\n")

out = []
for line in lines:
    out.append(line)
    if line.startswith("  `discount_amount`             INT UNSIGNED"):
        out.append("  `coupon_id`                   BIGINT UNSIGNED NULL COMMENT '本单核销的用户券 id,未用券为 NULL',\n")
    if line.rstrip("\n") == ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收藏';":
        out.append("\n" + address_block)
    if line.rstrip("\n") == ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单评价';":
        out.append("\n" + coupon_block)

with io.open(path, "w", encoding="utf-8", newline="") as f:
    f.writelines(out)

print("coupon_id rows:", sum(1 for l in out if "coupon_id" in l))
print("u_address:", sum(1 for l in out if "u_address" in l))
print("mk_user_coupon:", sum(1 for l in out if "mk_user_coupon" in l))
