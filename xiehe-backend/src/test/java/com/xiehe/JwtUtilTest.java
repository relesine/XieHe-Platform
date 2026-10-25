package com.xiehe;

import com.xiehe.common.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JWT 工具单元测试
 * 通过反射注入配置字段,验证令牌"生成->解析->篡改拒绝"全链路。
 */
@DisplayName("JWT工具测试")
class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() throws Exception {
        jwtUtil = new JwtUtil();
        // 反射注入配置字段(脱离Spring容器进行纯单元测试)
        setField("secret", "XieHeUnitTestSecretKeyForJwtSigning1234567890");
        setField("expireHours", 72);
    }

    private void setField(String name, Object value) throws Exception {
        Field field = JwtUtil.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(jwtUtil, value);
    }

    @Test
    @DisplayName("生成令牌后可解析出用户ID与角色")
    void generateAndParse() {
        String token = jwtUtil.generateToken(2L, "USER");
        assertNotNull(token);
        assertEquals(2L, jwtUtil.getUserId(token));
        assertEquals("USER", jwtUtil.parseToken(token).get("role", String.class));
    }

    @Test
    @DisplayName("令牌被篡改后解析必须失败")
    void tamperedTokenRejected() {
        String token = jwtUtil.generateToken(1L, "ADMIN");
        // 篡改签名部分(最后一段)
        String tampered = token.substring(0, token.length() - 4) + "AAAA";
        assertThrows(Exception.class, () -> jwtUtil.parseToken(tampered));
    }

    @Test
    @DisplayName("无效令牌直接拒绝")
    void invalidTokenRejected() {
        assertThrows(Exception.class, () -> jwtUtil.parseToken("not.a.jwt"));
        assertThrows(Exception.class, () -> jwtUtil.parseToken(""));
    }
}
