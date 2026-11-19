-- 学籍认证审核备注：控制台审核时写入，驳回原因同时展示给学生端
-- 用 information_schema 判重：新库结构由 V1 建齐,这里只给存量库补列
SET @sql = (SELECT IF(COUNT(*) = 0,
       'ALTER TABLE `u_user` ADD COLUMN `verify_note` VARCHAR(255) NOT NULL DEFAULT '''' COMMENT ''认证审核备注/驳回原因'' AFTER `forbidden_reason`',
       'SELECT 1')
  FROM information_schema.COLUMNS
 WHERE table_schema = DATABASE() AND table_name = 'u_user' AND column_name = 'verify_note');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
