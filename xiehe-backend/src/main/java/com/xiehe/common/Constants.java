package com.xiehe.common;

/**
 * 全局业务常量
 * 统一管理文件状态、权限等级、角色、用户类型等魔法值,
 * 避免字符串/数字散落在各 Service 中,提高可维护性。
 */
public final class Constants {

    /** 文件状态: 正常(可访问) */
    public static final int STATUS_NORMAL = 1;
    /** 文件状态: 回收站 */
    public static final int STATUS_TRASH = 2;

    /** 公开状态: 公开 */
    public static final int FILE_PUBLIC = 1;
    /** 公开状态: 私有 */
    public static final int FILE_PRIVATE = 0;

    /** 收藏状态: 已收藏 */
    public static final int FAVORITE_YES = 1;
    /** 收藏状态: 未收藏 */
    public static final int FAVORITE_NO = 0;

    /** 权限等级: 拥有者(一切权限) */
    public static final String PERM_OWNER = "OWNER";
    /** 权限等级: 可编辑(重命名/替换内容) */
    public static final String PERM_EDIT = "EDIT";
    /** 权限等级: 可查看(浏览/下载) */
    public static final String PERM_VIEW = "VIEW";
    /** 权限等级: 无权限 */
    public static final String PERM_NONE = "NONE";

    /** 角色: 超级管理员 */
    public static final String ROLE_ADMIN = "ADMIN";
    /** 角色: 普通用户 */
    public static final String ROLE_USER = "USER";

    /** 用户类型: 普通用户 */
    public static final String TYPE_NORMAL = "NORMAL";
    /** 用户类型: 高级用户 */
    public static final String TYPE_VIP = "VIP";

    /** 账号状态: 正常 */
    public static final int USER_STATUS_NORMAL = 1;
    /** 账号状态: 禁用 */
    public static final int USER_STATUS_DISABLED = 0;

    /** 普通用户默认存储上限 5GB */
    public static final long DEFAULT_LIMIT_5GB = 5L * 1024 * 1024 * 1024;
    /** VIP 用户存储上限 20GB */
    public static final long VIP_LIMIT_20GB = 20L * 1024 * 1024 * 1024;

    /** Redis key 前缀: 登录令牌 -> 用户ID */
    public static final String REDIS_TOKEN_KEY = "xiehe:token:";
    /** Redis key 前缀: 用户所有有效令牌集合 */
    public static final String REDIS_USER_TOKENS = "xiehe:user:tokens:";
    /** Redis key 前缀: 用户状态缓存(1正常/0禁用) */
    public static final String REDIS_USER_STATUS = "xiehe:user:status:";
    /** Redis key 前缀: 登录失败次数计数(后缀: 用户名:IP) */
    public static final String REDIS_LOGIN_FAIL = "xiehe:login:fail:";

    /** 登录连续失败次数上限,超过则锁定一段时间 */
    public static final int LOGIN_FAIL_LIMIT = 5;
    /** 登录失败锁定时长(分钟) */
    public static final int LOGIN_LOCK_MINUTES = 10;

    /** 用户状态缓存 TTL(分钟): 独立常量,避免误用登录锁定语义 */
    public static final int REDIS_USER_STATUS_TTL_MINUTES = 60;

    /** 管理统计 Redis 缓存 key */
    public static final String REDIS_ADMIN_STATS = "xiehe:stats";
    /** 管理统计缓存时长(秒) */
    public static final int ADMIN_STATS_CACHE_SECONDS = 30;

    /** 用户类型字符串常量(统计聚合/业务判断复用) */
    public static final String USER_TYPE_VIP = "VIP";

    /** 文件名最大长度(与 t_file.filename VARCHAR(255) 一致) */
    public static final int FILENAME_MAX_LENGTH = 255;

    private Constants() {
        // 工具常量类,禁止实例化
    }
}
