package com.xiehe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xiehe.common.AuditService;
import com.xiehe.common.BusinessException;
import com.xiehe.common.Constants;
import com.xiehe.common.JwtUtil;
import com.xiehe.common.PasswordUtil;
import com.xiehe.dto.LoginDTO;
import com.xiehe.dto.RegisterDTO;
import com.xiehe.entity.User;
import com.xiehe.mapper.UserMapper;
import com.xiehe.service.AuthService;
import com.xiehe.vo.LoginVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务实现
 * 安全设计:
 * 1. 登录失败次数限制(Redis计数,key=用户名:IP,防暴力破解 + 防恶意锁死他人账号)
 * 2. 登录成功将令牌登记到"用户令牌集合",支持改密/禁用时批量踢出
 * 3. 注册捕获数据库唯一键冲突,避免并发重复用户名
 * 4. 密码使用 BCrypt 存储,登录时兼容并自动升级历史 MD5 数据
 */
@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private AuditService auditService;

    @Value("${xiehe.jwt.expire-hours}")
    private int expireHours;

    @Override
    public void register(RegisterDTO dto) {
        // 1. 检查用户名是否已被占用
        Long exists = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()));
        if (exists > 0) {
            throw new BusinessException("用户名已存在,请换一个");
        }
        // 2. 组装新用户: 普通用户,默认5GB空间;密码使用 BCrypt 存储(自带随机盐)
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPasswordSalt("");
        user.setPassword(PasswordUtil.hash(dto.getPassword()));
        user.setNickname(dto.getNickname());
        user.setRole(Constants.ROLE_USER);
        user.setUserType(Constants.TYPE_NORMAL);
        user.setStorageLimit(Constants.DEFAULT_LIMIT_5GB);
        user.setUsedStorage(0L);
        user.setStatus(Constants.USER_STATUS_NORMAL);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 并发注册时唯一索引兜底,避免抛500
            throw new BusinessException("用户名已存在,请换一个");
        }
        auditService.log(user.getId(), "注册账号", "新用户[" + dto.getUsername() + "] 注册成功");
    }

    @Override
    public LoginVO login(LoginDTO dto, String clientIp) {
        // 0. 防暴力破解: 连续失败达到上限则锁定一段时间(key 含IP,防止攻击者锁死他人账号)
        String failKey = Constants.REDIS_LOGIN_FAIL + dto.getUsername() + ":" + safeIp(clientIp);
        Integer failCount = (Integer) redisTemplate.opsForValue().get(failKey);
        if (failCount != null && failCount >= Constants.LOGIN_FAIL_LIMIT) {
            throw new BusinessException("尝试次数过多,请" + Constants.LOGIN_LOCK_MINUTES + "分钟后再试");
        }

        // 1. 按用户名查用户
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()));
        if (user == null || !PasswordUtil.verify(dto.getPassword(), user.getPassword(), user.getPasswordSalt())) {
            // 记录失败次数,10分钟内有效
            long count = redisTemplate.opsForValue().increment(failKey);
            if (count == 1) {
                redisTemplate.expire(failKey, Constants.LOGIN_LOCK_MINUTES, TimeUnit.MINUTES);
            }
            throw new BusinessException("用户名或密码错误");
        }
        // 登录成功后清除失败计数
        redisTemplate.delete(failKey);

        // 1.1 旧密码格式(加盐MD5/无盐MD5)在登录成功后静默升级为 BCrypt
        if (PasswordUtil.needsUpgrade(user.getPassword())) {
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, user.getId())
                    .set(User::getPassword, PasswordUtil.hash(dto.getPassword()))
                    .set(User::getPasswordSalt, ""));
        }

        // 2. 检查账号状态
        if (user.getStatus() != Constants.USER_STATUS_NORMAL) {
            throw new BusinessException(403, "账号已被禁用,请联系管理员");
        }

        // 3. 生成JWT令牌
        String token = jwtUtil.generateToken(user.getId(), user.getRole());

        // 4. 令牌写入Redis,设置有效期 -> 登出时可立即失效
        redisTemplate.opsForValue().set(Constants.REDIS_TOKEN_KEY + token, user.getId(),
                expireHours, TimeUnit.HOURS);
        // 5. 登记到用户令牌集合(改密/禁用时可批量踢出旧令牌);集合TTL与令牌一致,防止无限累积
        redisTemplate.opsForSet().add(Constants.REDIS_USER_TOKENS + user.getId(), token);
        redisTemplate.expire(Constants.REDIS_USER_TOKENS + user.getId(), expireHours, TimeUnit.HOURS);
        // 6. 缓存用户状态,拦截器免查库快速校验
        redisTemplate.opsForValue().set(Constants.REDIS_USER_STATUS + user.getId(),
                user.getStatus(), Constants.REDIS_USER_STATUS_TTL_MINUTES, TimeUnit.MINUTES);

        auditService.log(user.getId(), "登录", "用户[" + user.getUsername() + "] 登录成功", clientIp);
        return LoginVO.of(token, user);
    }

    @Override
    public void logout(String token) {
        // 删除Redis中的令牌记录,实现立即失效
        redisTemplate.delete(Constants.REDIS_TOKEN_KEY + token);
        // 从用户令牌集合中移除(解析失败说明令牌已过期,忽略)
        try {
            Long userId = jwtUtil.getUserId(token);
            redisTemplate.opsForSet().remove(Constants.REDIS_USER_TOKENS + userId, token);
            auditService.log(userId, "退出登录", "用户退出登录");
        } catch (Exception ignored) {
            // 令牌已失效,无需处理
        }
    }

    /** IP归一化: null/空/超长均归为 unknown,避免 Redis key 异常 */
    private String safeIp(String ip) {
        if (!StringUtils.hasText(ip)) {
            return "unknown";
        }
        return ip.length() > 39 ? ip.substring(0, 39) : ip;
    }
}
