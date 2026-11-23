-- TODO(上线前): 移除演示配送员资格，实际配送员由平台审核后开通
INSERT INTO `d_rider` (`user_id`, `real_name`, `phone`, `campus_area`, `dorm_building`, `status`)
SELECT u.id, u.real_name, u.phone, u.campus_area, u.dorm_building, 1
FROM `u_user` u
WHERE u.student_no = '2022010101' AND u.real_name = '张狗蛋'
  AND u.phone = '13800000001' AND u.status = 1 AND u.verify_status = 2
  AND NOT EXISTS (SELECT 1 FROM `d_rider` r WHERE r.user_id = u.id);
