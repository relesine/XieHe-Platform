package com.xiehe.common;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 密码安全工具类
 *
 * 新密码统一使用 BCrypt(自适应哈希,自带随机盐,抗彩虹表/暴力破解),
 * 同时兼容历史数据:
 * - BCrypt 哈希: 以 $2a$/$2b$/$2y$ 开头,直接使用 BCrypt 校验;
 * - 加盐 MD5: password = MD5(salt + 明文),password_salt 非空;
 * - 无盐 MD5: password = MD5(明文),password_salt 为空(最早期的数据)。
 *
 * 登录成功时会检测旧格式并自动重哈希为 BCrypt(由调用方触发),
 * 用户无感知完成迁移,无需停机刷库。
 */
public final class PasswordUtil {

    /** 盐长度(历史加盐 MD5 使用,取 UUID 前 8 位) */
    private static final int SALT_LENGTH = 8;

    /** BCrypt 强度:10 轮(单次校验约 50ms,兼顾安全与登录性能) */
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(10);

    private PasswordUtil() {
        // 工具类禁止实例化
    }

    /** 生成随机盐(仅兼容历史加盐 MD5 数据,BCrypt 自带盐) */
    public static String generateSalt() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, SALT_LENGTH);
    }

    /**
     * 生成 BCrypt 密码哈希(新注册/改密统一使用)
     *
     * @param rawPassword 明文密码
     * @return 60 位 BCrypt 哈希
     */
    public static String hash(String rawPassword) {
        return ENCODER.encode(rawPassword == null ? "" : rawPassword);
    }

    /** 历史加盐加密: MD5(salt + 明文),仅用于校验旧数据 */
    public static String hashWithSalt(String salt, String rawPassword) {
        return md5(salt + (rawPassword == null ? "" : rawPassword));
    }

    /** 历史无盐加密,仅用于校验旧数据 */
    public static String md5(String raw) {
        return DigestUtils.md5DigestAsHex((raw == null ? "" : raw).getBytes(StandardCharsets.UTF_8));
    }

    /** 判断哈希是否为 BCrypt 格式 */
    public static boolean isBcrypt(String storedHash) {
        return storedHash != null && storedHash.startsWith("$2");
    }

    /** 判断旧哈希是否需要在本次登录后升级为 BCrypt */
    public static boolean needsUpgrade(String storedHash) {
        return storedHash != null && !isBcrypt(storedHash);
    }

    /**
     * 校验密码: 自动识别 BCrypt / 加盐 MD5 / 无盐 MD5 三种存储格式
     *
     * @param rawPassword 用户输入的明文密码
     * @param storedHash  数据库中的 password 字段
     * @param storedSalt  数据库中的 password_salt 字段(BCrypt 数据为空)
     * @return 是否匹配
     */
    public static boolean verify(String rawPassword, String storedHash, String storedSalt) {
        if (storedHash == null || storedHash.isEmpty()) {
            return false;
        }
        if (isBcrypt(storedHash)) {
            try {
                return ENCODER.matches(rawPassword == null ? "" : rawPassword, storedHash);
            } catch (IllegalArgumentException e) {
                // 非法密文格式:按校验失败处理,避免异常冒泡到全局异常处理器
                return false;
            }
        }
        if (storedSalt != null && !storedSalt.isEmpty()) {
            return storedHash.equalsIgnoreCase(hashWithSalt(storedSalt, rawPassword));
        }
        // 兼容历史无盐数据
        return storedHash.equalsIgnoreCase(md5(rawPassword));
    }
}
