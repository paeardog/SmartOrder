-- 学生端本地联调演示账号: admin / admin
-- 口令哈希同样是 PBKDF2WithHmacSHA256 十二万轮,与 V2 的种子学生一致。
-- 只用于开发联调,上线前必须改密或删除该账号。
INSERT INTO `u_user`
  (`phone`, `password_hash`, `nickname`, `real_name`, `student_no`, `college`,
   `campus_area`, `auth_source`, `verify_status`, `credit_score`, `order_count`, `status`)
SELECT NULL,
       '$pbkdf2-sha256$120000$aCPdICQnTH6DRftY8e4E2A==$ymKA1uTBW0GKgQ1g0IM3UAXfK0fsQ1F/+annzol4c4M=',
       '管理员', '演示账号', 'admin', '',
       '中心校区', 3, 2, 100, 0, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `u_user` WHERE `student_no` = 'admin');
