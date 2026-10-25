package com.xiehe.interceptor;

import com.xiehe.common.BusinessException;
import com.xiehe.common.Constants;
import com.xiehe.common.IpUtil;
import com.xiehe.common.JwtUtil;
import com.xiehe.common.UserContext;
import com.xiehe.entity.User;
import com.xiehe.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.concurrent.TimeUnit;

/**
 * 登录认证拦截器
 * 作用: 拦截所有 /api/** 请求,校验请求头中的 JWT 令牌,
 * 校验通过后把用户ID/角色/客户端IP写入 UserContext,供后续 Controller/Service 使用。
 *
 * 三重校验:
 * 1. JWT 签名与有效期是否合法
 * 2. Redis 中是否存在该令牌(登录时写入,登出/改密/禁用时删除 -> 实现"立即失效")
 * 3. 用户账号状态是否正常(被禁用的用户即使持有令牌也无法访问)
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private UserMapper userMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 放行预检请求(浏览器跨域时会先发 OPTIONS 请求探测)
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 从请求头取令牌: 前端约定格式 Authorization: Bearer <token>
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            throw new BusinessException(401, "未登录,请先登录");
        }
        String token = auth.substring(7);

        // 1. 校验 JWT 签名与有效期
        Claims claims;
        try {
            claims = jwtUtil.parseToken(token);
        } catch (Exception e) {
            throw new BusinessException(401, "登录已过期,请重新登录");
        }

        // 2. 校验 Redis 中的登录态(登出/改密/禁用后会删除,实现立即失效)
        Boolean exists = redisTemplate.hasKey(Constants.REDIS_TOKEN_KEY + token);
        if (Boolean.FALSE.equals(exists)) {
            throw new BusinessException(401, "登录已失效,请重新登录");
        }

        Long userId = Long.parseLong(claims.getSubject());

        // 3. 校验账号状态(被禁用的用户不允许继续访问)
        checkUserStatus(userId);

        // 写入当前用户上下文(ID/角色/客户端IP)
        String role = claims.get("role", String.class);
        UserContext.set(userId, role);
        UserContext.setIp(IpUtil.getClientIp(request));
        return true;
    }

    /** 校验用户账号状态: 优先读Redis缓存,缓存未命中回源数据库并回填 */
    private void checkUserStatus(Long userId) {
        String statusKey = Constants.REDIS_USER_STATUS + userId;
        Object cached = redisTemplate.opsForValue().get(statusKey);
        if (cached instanceof Integer) {
            if ((Integer) cached != Constants.USER_STATUS_NORMAL) {
                throw new BusinessException(403, "账号已被禁用,请联系管理员");
            }
            return;
        }
        // 缓存未命中(如Redis重启/新登录): 查库并回填缓存
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(401, "账号不存在,请重新登录");
        }
        redisTemplate.opsForValue().set(statusKey, user.getStatus(),
                Constants.REDIS_USER_STATUS_TTL_MINUTES, TimeUnit.MINUTES);
        if (user.getStatus() != Constants.USER_STATUS_NORMAL) {
            throw new BusinessException(403, "账号已被禁用,请联系管理员");
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 请求结束,清理 ThreadLocal,防止线程复用导致数据串扰
        UserContext.clear();
    }
}
