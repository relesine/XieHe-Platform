package com.xiehe.common;

/**
 * 当前登录用户上下文
 * 使用 ThreadLocal 在"一次请求的整个执行链路"中传递当前用户ID/角色/客户端IP,
 * 请求结束后由拦截器清理,避免线程间数据串扰。
 */
public class UserContext {

    private static final ThreadLocal<Long> CURRENT_USER = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_ROLE = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_IP = new ThreadLocal<>();

    public static void set(Long userId, String role) {
        CURRENT_USER.set(userId);
        CURRENT_ROLE.set(role);
    }

    /** 设置当前请求的客户端IP(由拦截器写入,供审计日志使用) */
    public static void setIp(String ip) {
        CURRENT_IP.set(ip);
    }

    /** 获取当前登录用户ID */
    public static Long getUserId() {
        return CURRENT_USER.get();
    }

    /** 获取当前登录用户角色 */
    public static String getRole() {
        return CURRENT_ROLE.get();
    }

    /** 获取当前请求客户端IP(可能为null,如拦截器白名单请求) */
    public static String getIp() {
        return CURRENT_IP.get();
    }

    public static boolean isAdmin() {
        return "ADMIN".equals(CURRENT_ROLE.get());
    }

    /** 请求结束后清理,防止内存泄漏 */
    public static void clear() {
        CURRENT_USER.remove();
        CURRENT_ROLE.remove();
        CURRENT_IP.remove();
    }
}
