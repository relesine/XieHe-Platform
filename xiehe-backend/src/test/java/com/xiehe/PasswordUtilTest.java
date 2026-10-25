package com.xiehe;

import com.xiehe.common.PasswordUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 密码工具单元测试
 * 覆盖:
 * 1. BCrypt 哈希、校验与随机盐
 * 2. 历史加盐 MD5 与 SQL 种子数据互通
 * 3. 无盐 MD5 兼容
 * 4. 旧格式识别与升级判断
 * 5. 错误密码/空值/非法哈希边界
 */
@DisplayName("密码工具测试")
class PasswordUtilTest {

    /** 演示盐(与 sql/xiehe_init.sql 预置数据保持一致) */
    private static final String DEMO_SALT = "XieHe#2026";

    @Test
    @DisplayName("加盐MD5结果与SQL种子数据一致(123456)")
    void hashWithSaltMatchesSqlSeed() {
        // 该值来自 xiehe_init.sql 预置的 zhangsan/lisi 等用户密码
        assertEquals("03438395610eeac428903436564e1e33",
                PasswordUtil.hashWithSalt(DEMO_SALT, "123456"));
        assertEquals("c0327ee390c9877780647e9b19aedee7",
                PasswordUtil.hashWithSalt(DEMO_SALT, "admin123"));
    }

    @Test
    @DisplayName("加盐存储格式校验: 正确密码通过,错误密码拒绝")
    void verifySaltedPassword() {
        String salt = PasswordUtil.generateSalt();
        String hash = PasswordUtil.hashWithSalt(salt, "myPassword123");
        assertTrue(PasswordUtil.verify("myPassword123", hash, salt));
        assertFalse(PasswordUtil.verify("wrongPassword", hash, salt));
    }

    @Test
    @DisplayName("兼容历史无盐MD5数据")
    void verifyLegacyNoSaltData() {
        // 旧格式: password = MD5(明文), password_salt = null
        String legacyHash = PasswordUtil.md5("123456");
        assertTrue(PasswordUtil.verify("123456", legacyHash, null));
        assertFalse(PasswordUtil.verify("654321", legacyHash, null));
    }

    @Test
    @DisplayName("随机盐: 长度8且每次生成不同")
    void generateSaltIsUnique() {
        String salt1 = PasswordUtil.generateSalt();
        String salt2 = PasswordUtil.generateSalt();
        assertEquals(8, salt1.length());
        assertNotEquals(salt1, salt2, "连续生成的盐不应重复");
    }

    @Test
    @DisplayName("边界: 空密码/空哈希/非法盐不应误通过")
    void verifyEdgeCases() {
        // 空哈希必然失败
        assertFalse(PasswordUtil.verify("123456", null, "salt"));
        // 无盐 + 空哈希
        assertFalse(PasswordUtil.verify("123456", "", null));
        // 存储哈希被篡改(长度不符)不应通过
        assertFalse(PasswordUtil.verify("123456", "abc", "salt"));
    }

    @Test
    @DisplayName("BCrypt: 同一密码每次哈希不同,但校验均通过")
    void bcryptHashAndVerify() {
        String hash1 = PasswordUtil.hash("myPassword123");
        String hash2 = PasswordUtil.hash("myPassword123");
        assertTrue(PasswordUtil.isBcrypt(hash1));
        assertNotEquals(hash1, hash2, "BCrypt 应使用随机盐,两次哈希不同");
        assertTrue(PasswordUtil.verify("myPassword123", hash1, ""));
        assertFalse(PasswordUtil.verify("wrongPassword", hash1, ""));
    }

    @Test
    @DisplayName("旧格式密码应标记为需要升级")
    void legacyPasswordNeedsUpgrade() {
        String legacySalted = PasswordUtil.hashWithSalt(DEMO_SALT, "123456");
        assertTrue(PasswordUtil.needsUpgrade(legacySalted));
        assertFalse(PasswordUtil.needsUpgrade(PasswordUtil.hash("123456")));
        assertFalse(PasswordUtil.needsUpgrade(null));
    }
}
