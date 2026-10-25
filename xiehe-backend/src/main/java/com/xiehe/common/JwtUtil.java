package com.xiehe.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import java.util.UUID;

/**
 * JWT 工具类
 * JWT(JSON Web Token): 一种登录令牌,服务端签发,客户端保存,
 * 后续每次请求带上令牌即可证明"我是谁",无需重复输入密码。
 */
@Component
public class JwtUtil {

    /** 签名密钥(从配置文件读取) */
    @Value("${xiehe.jwt.secret}")
    private String secret;

    /** 令牌有效期(小时) */
    @Value("${xiehe.jwt.expire-hours}")
    private int expireHours;

    /**
     * 根据用户ID+角色生成令牌
     *
     * 加入随机唯一标识 jti(UUID),保证"同一秒内为同一用户重复签发"的令牌也互不相同。
     */
    public String generateToken(Long userId, String role) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        Date expire = new Date(now.getTime() + expireHours * 3600_000L);
        return Jwts.builder()
                .setSubject(String.valueOf(userId))        // 主体: 用户ID
                .claim("role", role)                        // 附加信息: 角色
                .setId(UUID.randomUUID().toString())        // 唯一ID(jti): 每次登录令牌唯一,避免同秒冲突
                .setIssuedAt(now)                           // 签发时间
                .setExpiration(expire)                      // 过期时间
                .signWith(key, SignatureAlgorithm.HS256)    // 签名算法
                .compact();
    }

    /** 解析令牌,返回 Claims(含用户ID、角色、过期时间) */
    public Claims parseToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /** 从令牌中取用户ID */
    public Long getUserId(String token) {
        return Long.parseLong(parseToken(token).getSubject());
    }
}
