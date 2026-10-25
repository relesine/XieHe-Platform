package com.xiehe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiehe.common.BusinessException;
import com.xiehe.common.Constants;
import com.xiehe.common.UserContext;
import com.xiehe.entity.FileInfo;
import com.xiehe.entity.OperationLog;
import com.xiehe.entity.User;
import com.xiehe.mapper.FileMapper;
import com.xiehe.mapper.FilePermissionMapper;
import com.xiehe.mapper.OperationLogMapper;
import com.xiehe.mapper.UserMapper;
import com.xiehe.service.AdminService;
import com.xiehe.vo.AdminStatsVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 管理后台服务实现
 * 平台统计使用数据库聚合(COUNT/SUM/GROUP BY),避免整表载入内存;Redis 30s 缓存保留。
 */
@Service
public class AdminServiceImpl implements AdminService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private FileMapper fileMapper;

    @Autowired
    private FilePermissionMapper permissionMapper;

    @Autowired
    private OperationLogMapper logMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public AdminStatsVO stats(boolean refresh) {
        // 先从Redis缓存取,缓存命中直接返回(30秒内不重复查库); refresh=true 强制绕过缓存
        String key = Constants.REDIS_ADMIN_STATS;
        if (!refresh) {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached instanceof AdminStatsVO) {
                return (AdminStatsVO) cached;
            }
        }
        AdminStatsVO vo = new AdminStatsVO();
        // 1. 计数类指标: 单值COUNT聚合
        vo.setUserCount(userMapper.selectCount(null));
        vo.setVipCount(userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUserType, Constants.USER_TYPE_VIP)));
        vo.setFileCount(fileMapper.selectCount(null));
        vo.setPublicFileCount(fileMapper.countPublicFiles());
        vo.setTotalDownloads(fileMapper.sumDownloadCount());
        // 2. 存储汇总: SUM聚合(已用/配额)
        Map<String, Object> storage = userMapper.sumStorage();
        vo.setTotalStorageUsed(toLong(storage.get("totalUsed")));
        vo.setTotalStorageLimit(toLong(storage.get("totalLimit")));
        // 3. 文件类型分布: GROUP BY 聚合
        Map<String, Long> typeDist = new HashMap<>();
        typeDist.put("DOC", 0L);
        typeDist.put("IMAGE", 0L);
        typeDist.put("ZIP", 0L);
        typeDist.put("VIDEO", 0L);
        for (Map<String, Object> row : fileMapper.countGroupByFileType()) {
            String type = row.get("typeKey") == null ? "DOC" : String.valueOf(row.get("typeKey"));
            typeDist.merge(type, toLong(row.get("cnt")), Long::sum);
        }
        vo.setFileTypeDist(typeDist);
        // 4. 用户类型分布: GROUP BY 聚合
        Map<String, Long> userTypeDist = new HashMap<>();
        for (Map<String, Object> row : userMapper.countGroupByUserType()) {
            String type = row.get("typeKey") == null ? "NORMAL" : String.valueOf(row.get("typeKey"));
            userTypeDist.merge(type, toLong(row.get("cnt")), Long::sum);
        }
        vo.setUserTypeDist(userTypeDist);
        // 写入缓存
        redisTemplate.opsForValue().set(key, vo, Constants.ADMIN_STATS_CACHE_SECONDS, TimeUnit.SECONDS);
        return vo;
    }

    /** 数据库聚合结果转 long(兼容 NULL) */
    private long toLong(Object obj) {
        if (obj == null) {
            return 0L;
        }
        if (obj instanceof Number) {
            return ((Number) obj).longValue();
        }
        return Long.parseLong(String.valueOf(obj));
    }

    @Override
    public Page<User> listUsers(int page, int size, String keyword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(User::getUsername, keyword).or().like(User::getNickname, keyword));
        }
        wrapper.orderByAsc(User::getId);
        return userMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    public void updateUserStatus(Long operatorId, Long targetId, Integer status) {
        if (operatorId.equals(targetId)) {
            throw new BusinessException("不能禁用自己");
        }
        User target = userMapper.selectById(targetId);
        if (target == null) {
            throw new BusinessException("目标用户不存在");
        }
        boolean disabling = status != null && status == Constants.USER_STATUS_DISABLED;
        target.setStatus(disabling ? Constants.USER_STATUS_DISABLED : Constants.USER_STATUS_NORMAL);
        userMapper.updateById(target);
        if (disabling) {
            // 安全: 禁用账号后,其所有已签发令牌立即失效,防止被禁用用户继续访问
            invalidateUserTokens(targetId);
        } else {
            // 启用账号: 清除状态缓存,拦截器回源数据库读取最新状态
            redisTemplate.delete(Constants.REDIS_USER_STATUS + targetId);
        }
        log(operatorId, disabling ? "禁用用户" : "启用用户",
                "用户[" + target.getUsername() + "] 状态已更新");
    }

    /** 批量作废某用户的所有登录令牌 */
    private void invalidateUserTokens(Long userId) {
        String setKey = Constants.REDIS_USER_TOKENS + userId;
        Set<Object> tokens = redisTemplate.opsForSet().members(setKey);
        if (tokens != null) {
            for (Object t : tokens) {
                redisTemplate.delete(Constants.REDIS_TOKEN_KEY + t);
            }
        }
        redisTemplate.delete(setKey);
        redisTemplate.delete(Constants.REDIS_USER_STATUS + userId);
    }

    @Override
    public void updateUserType(Long operatorId, Long targetId, String userType) {
        if (!Constants.TYPE_NORMAL.equals(userType) && !Constants.TYPE_VIP.equals(userType)) {
            throw new BusinessException("用户类型只能为 NORMAL 或 VIP");
        }
        User target = userMapper.selectById(targetId);
        if (target == null) {
            throw new BusinessException("目标用户不存在");
        }
        target.setUserType(userType);
        // 升级VIP -> 20GB; 降级普通 -> 5GB (若已用超过5GB则不降级)
        if (Constants.TYPE_VIP.equals(userType)) {
            target.setStorageLimit(Constants.VIP_LIMIT_20GB);
        } else {
            if (target.getUsedStorage() != null && target.getUsedStorage() > Constants.DEFAULT_LIMIT_5GB) {
                throw new BusinessException("该用户已用空间超过5GB,暂不能降级为普通用户");
            }
            target.setStorageLimit(Constants.DEFAULT_LIMIT_5GB);
        }
        userMapper.updateById(target);
        log(operatorId, "调整用户类型", "用户[" + target.getUsername() + "] 类型变更为 " + userType);
    }

    @Override
    public Page<FileInfo> listFiles(int page, int size, String keyword) {
        LambdaQueryWrapper<FileInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileInfo::getStatus, 1);   // 仅正常状态文件
        if (StringUtils.hasText(keyword)) {
            wrapper.like(FileInfo::getFilename, keyword);
        }
        wrapper.orderByDesc(FileInfo::getCreateTime);
        return fileMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    public void deleteFile(Long operatorId, Long fileId) {
        FileInfo file = fileMapper.selectById(fileId);
        if (file == null) {
            throw new BusinessException("文件不存在");
        }
        // 移入回收站(status=2): 与用户端行为一致,拥有者可在回收站恢复或彻底删除
        file.setStatus(2);
        fileMapper.updateById(file);
        log(operatorId, "删除文件", "文件[" + file.getFilename() + "] 已被管理员移入回收站");
    }

    @Override
    public Page<OperationLog> listLogs(int page, int size) {
        return logMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<OperationLog>().orderByDesc(OperationLog::getId));
    }

    @Override
    public void log(Long operatorId, String action, String detail) {
        User operator = userMapper.selectById(operatorId);
        OperationLog log = new OperationLog();
        log.setUserId(operatorId);
        log.setUsername(operator != null ? operator.getUsername() : "未知");
        log.setAction(action);
        log.setDetail(detail);
        // 记录客户端IP,满足"操作人/动作/IP/时间"审计四要素
        log.setIp(UserContext.getIp());
        logMapper.insert(log);
    }
}
