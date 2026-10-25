-- ============================================================
-- 协合XieHe 协作文档云协作平台 - 数据库初始化脚本
-- 数据库: MySQL 8.0 | 字符集: utf8mb4
-- 执行方式: 在 Navicat 或 mysql 命令行中执行本脚本
-- ============================================================

-- 1. 创建数据库
CREATE DATABASE IF NOT EXISTS xiehe DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE xiehe;

-- 2. 用户表 t_user
--    角色: USER=普通用户  ADMIN=超级管理员
--    类型: NORMAL=普通用户(5GB)  VIP=高级用户(20GB)
--    密码: v1.2起加盐存储: password = MD5(password_salt + 明文);password_salt为空=兼容旧的无盐MD5
DROP TABLE IF EXISTS t_user;
CREATE TABLE t_user (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID(主键)',
  username      VARCHAR(50)  NOT NULL COMMENT '用户名(登录账号,唯一)',
  password      VARCHAR(100) NOT NULL COMMENT '密码(MD5加盐加密存储)',
  password_salt VARCHAR(32)  DEFAULT NULL COMMENT '密码盐(为空表示旧的无盐MD5数据,兼容登录)',
  nickname      VARCHAR(50)  NOT NULL COMMENT '昵称(显示名称)',
  avatar        VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
  role          VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '角色:USER普通用户/ADMIN超级管理员',
  user_type     VARCHAR(20)  NOT NULL DEFAULT 'NORMAL' COMMENT '用户类型:NORMAL普通(5GB)/VIP高级(20GB)',
  storage_limit BIGINT       NOT NULL DEFAULT 5368709120 COMMENT '存储空间上限(字节),5GB=5368709120',
  used_storage  BIGINT       NOT NULL DEFAULT 0 COMMENT '已用存储空间(字节)',
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态:1正常/0禁用',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB AUTO_INCREMENT=1 COMMENT='用户表';

-- 3. 文件表 t_file
DROP TABLE IF EXISTS t_file;
CREATE TABLE t_file (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '文件ID(主键)',
  user_id       BIGINT       NOT NULL COMMENT '文件所属用户ID(拥有者)',
  filename      VARCHAR(255) NOT NULL COMMENT '文件名称(显示名)',
  original_name VARCHAR(255) NOT NULL COMMENT '原始文件名(上传时的名字)',
  file_type     VARCHAR(20)  NOT NULL COMMENT '文件类型:DOC文档/IMAGE图片/ZIP压缩包/VIDEO视频',
  size          BIGINT       NOT NULL DEFAULT 0 COMMENT '文件大小(字节)',
  file_url      VARCHAR(500) NOT NULL COMMENT '文件访问路径(相对URL)',
  is_public     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否公开:0私有/1公开(公开仅代表可浏览,编辑需单独授权)',
  is_favorite   TINYINT      NOT NULL DEFAULT 0 COMMENT '是否收藏:0否/1是(拥有者视角星标)',
  download_count INT         NOT NULL DEFAULT 0 COMMENT '下载次数',
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态:1正常/2回收站(删除后进入回收站,可恢复或彻底删除)',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_user_id (user_id),
  KEY idx_user_id_status (user_id, status),       -- v1.2: 我的文件/收藏/回收站列表常用(user_id+status)复合索引
  KEY idx_is_public (is_public),
  KEY idx_is_public_status (is_public, status)    -- v1.2: 公开文件列表(is_public+status)复合索引
) ENGINE=InnoDB AUTO_INCREMENT=1 COMMENT='文件表';

-- 4. 好友关系表 t_friend_relation
--    用户A添加用户B为好友后,记录一条 user_id=A, friend_id=B 的记录
DROP TABLE IF EXISTS t_friend_relation;
CREATE TABLE t_friend_relation (
  id          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '关系ID(主键)',
  user_id     BIGINT   NOT NULL COMMENT '发起添加的用户ID',
  friend_id   BIGINT   NOT NULL COMMENT '被添加为好友的用户ID',
  status      TINYINT  NOT NULL DEFAULT 1 COMMENT '状态:1已添加(学习级项目暂不做申请审批)',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '添加时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_friend (user_id, friend_id),
  KEY idx_friend_id (friend_id)
) ENGINE=InnoDB AUTO_INCREMENT=1 COMMENT='好友关系表';

-- 5. 文件授权表 t_file_permission
--    记录"谁"对"哪个文件"拥有什么权限
--    permission: VIEW只读(查看/下载)  EDIT可编辑(可重命名/替换内容)
--    source: FRIEND=好友快捷授权  MANUAL=手动授权(非好友也可被手动授权)
DROP TABLE IF EXISTS t_file_permission;
CREATE TABLE t_file_permission (
  id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '授权记录ID(主键)',
  file_id     BIGINT      NOT NULL COMMENT '文件ID',
  user_id     BIGINT      NOT NULL COMMENT '被授权用户ID',
  permission  VARCHAR(20) NOT NULL COMMENT '权限:VIEW查看/EDIT编辑',
  source      VARCHAR(20) NOT NULL DEFAULT 'MANUAL' COMMENT '授权来源:FRIEND好友/MANUAL手动',
  create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_file_user (file_id, user_id),
  KEY idx_user_id (user_id)
) ENGINE=InnoDB AUTO_INCREMENT=1 COMMENT='文件授权表';

-- 6. 操作日志表 t_operation_log (管理后台使用)
DROP TABLE IF EXISTS t_operation_log;
CREATE TABLE t_operation_log (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日志ID(主键)',
  user_id     BIGINT       DEFAULT NULL COMMENT '操作人用户ID',
  username    VARCHAR(50)  DEFAULT NULL COMMENT '操作人用户名',
  action      VARCHAR(100) NOT NULL COMMENT '操作动作(如:用户禁用/文件删除)',
  detail      VARCHAR(500) DEFAULT NULL COMMENT '操作详情描述',
  ip          VARCHAR(64)  DEFAULT NULL COMMENT '操作人客户端IP(v1.2新增,审计四要素)',
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (id),
  KEY idx_username (username)
) ENGINE=InnoDB AUTO_INCREMENT=1 COMMENT='操作日志表';

-- 6.1 预置 2 条示例操作日志(管理后台"操作日志"页演示数据,含IP字段)
INSERT INTO t_operation_log (id, user_id, username, action, detail, ip, create_time) VALUES
(1, 1, 'admin', '系统初始化', '平台数据库初始化完成,预置7个用户与10份示例文件', '127.0.0.1', NOW()),
(2, 1, 'admin', '用户升级', '将用户[zhangsan]升级为高级用户(VIP),存储空间扩容至20GB', '127.0.0.1', NOW());

-- 7. 分享链接表 t_share
--    拥有者生成分享链接,任何人凭 token 可免登录访问/下载该文件
--    支持访问密码(可为空)与有效期(为空=永久有效)
DROP TABLE IF EXISTS t_share;
CREATE TABLE t_share (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '分享ID(主键)',
  file_id       BIGINT       NOT NULL COMMENT '被分享的文件ID',
  token         VARCHAR(64)  NOT NULL COMMENT '分享令牌(随机UUID,链接唯一标识)',
  create_user_id BIGINT      NOT NULL COMMENT '创建分享的用户ID(仅拥有者可创建)',
  password      VARCHAR(64)  DEFAULT NULL COMMENT '访问密码(MD5,为空表示无需密码)',
  expires_at    DATETIME     DEFAULT NULL COMMENT '过期时间(为空表示永久有效)',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_token (token),
  KEY idx_file_id (file_id)
) ENGINE=InnoDB AUTO_INCREMENT=1 COMMENT='分享链接表';

-- ============================================================
-- 3. 初始化测试数据
-- 密码均为"加盐MD5": password = MD5('XieHe#2026' + 明文)
--   123456   -> 03438395610eeac428903436564e1e33
--   admin123 -> c0327ee390c9877780647e9b19aedee7
-- (统一演示盐 XieHe#2026;实际注册用户由后端生成随机盐)
-- ============================================================

-- 3.1 超级管理员 1 名
INSERT INTO t_user (id, username, password, password_salt, nickname, role, user_type, storage_limit, used_storage, status) VALUES
(1, 'admin', 'c0327ee390c9877780647e9b19aedee7', 'XieHe#2026', '超级管理员', 'ADMIN', 'VIP', 21474836480, 0, 1);

-- 3.2 预设 6 个测试用户（张三为高级用户VIP，其余为普通用户）
--     used_storage 与下方示例文件大小保持一致,保证存储空间进度条演示真实
INSERT INTO t_user (id, username, password, password_salt, nickname, role, user_type, storage_limit, used_storage, status) VALUES
(2, 'zhangsan', '03438395610eeac428903436564e1e33', 'XieHe#2026', '张三', 'USER', 'VIP',    21474836480, 1302528,   1),
(3, 'lisi',     '03438395610eeac428903436564e1e33', 'XieHe#2026', '李四', 'USER', 'NORMAL', 5368709120,  7749632,   1),
(4, 'wangwu',   '03438395610eeac428903436564e1e33', 'XieHe#2026', '王五', 'USER', 'NORMAL', 5368709120,  220200960, 1),
(5, 'zhaoliu',  '03438395610eeac428903436564e1e33', 'XieHe#2026', '赵六', 'USER', 'NORMAL', 5368709120,  15728640,  1),
(6, 'sunqi',    '03438395610eeac428903436564e1e33', 'XieHe#2026', '孙七', 'USER', 'NORMAL', 5368709120,  157286400, 1),
(7, 'chenba',   '03438395610eeac428903436564e1e33', 'XieHe#2026', '陈八', 'USER', 'NORMAL', 5368709120,  0,         1);

-- 3.3 预设 3 组好友关系：张三-李四、张三-王五、李四-王五
INSERT INTO t_friend_relation (user_id, friend_id, status) VALUES
(2, 3, 1),  -- 张三 -> 李四
(3, 2, 1),  -- 李四 -> 张三（好友关系双向各一条，方便查询）
(2, 4, 1),  -- 张三 -> 王五
(4, 2, 1),  -- 王五 -> 张三
(3, 4, 1),  -- 李四 -> 王五
(4, 3, 1);  -- 王五 -> 李四

-- 3.4 预设 10 份示例文件（覆盖文档/图片/压缩包/视频四类；公开3份+私有7份）
--     文件访问路径指向后端静态资源目录 /uploads/seed/ 下的占位文件
INSERT INTO t_file (id, user_id, filename, original_name, file_type, size, file_url, is_public, download_count, status) VALUES
(1,  2, '项目需求说明书.docx',  '项目需求说明书.docx',  'DOC',   245760,  '/uploads/seed/需求说明书.txt',    1, 12, 1),
(2,  2, '团队合影.jpg',         '团队合影.jpg',         'IMAGE', 1048576, '/uploads/seed/团队合影.png',       1, 30, 1),
(3,  2, '张三的个人笔记.txt',   '张三的个人笔记.txt',   'DOC',   8192,   '/uploads/seed/张三的个人笔记.txt', 0, 3, 1),
(4,  3, '数据库设计文档.docx',  '数据库设计文档.docx',  'DOC',   409600,  '/uploads/seed/需求说明书.txt',    1, 8,  1),
(5,  3, '活动照片.png',         '活动照片.png',         'IMAGE', 2097152, '/uploads/seed/团队合影.png',       0, 0,  1),
(6,  3, '李四的私有档案.zip',   '李四的私有档案.zip',   'ZIP',   5242880, '/uploads/seed/示例压缩包.zip',     0, 0,  1),
(7,  4, '产品原型压缩包.zip',   '产品原型压缩包.zip',   'ZIP',   10485760,'/uploads/seed/示例压缩包.zip',    0, 5,  1),
(8,  4, '王五的会议视频.mp4',   '王五的会议视频.mp4',   'VIDEO', 209715200,'/uploads/seed/示例视频.mp4',     0, 2,  1),
(9,  5, '赵六的学习资料.zip',   '赵六的学习资料.zip',   'ZIP',   15728640,'/uploads/seed/示例压缩包.zip',    0, 1,  1),
(10, 6, '孙七的风景视频.mp4',   '孙七的风景视频.mp4',   'VIDEO', 157286400,'/uploads/seed/示例视频.mp4',    0, 0,  1);

-- 3.5 预设授权示例：
--     张三将私有文件(3号)授权给好友李四【编辑】权限(来源:好友快捷授权)
--     张三将私有文件(3号)手动授权给非好友赵六【查看】权限(证明非好友也可被手动授权)
--     李四将私有文件(6号)授权给好友张三【查看】权限
INSERT INTO t_file_permission (file_id, user_id, permission, source) VALUES
(3, 3, 'EDIT', 'FRIEND'),   -- 张三的文件3 授权给好友李四:可编辑
(3, 5, 'VIEW', 'MANUAL'),   -- 张三的文件3 手动授权给非好友赵六:可查看
(6, 2, 'VIEW', 'FRIEND');   -- 李四的文件6 授权给好友张三:可查看
