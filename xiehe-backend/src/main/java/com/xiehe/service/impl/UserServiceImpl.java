package com.xiehe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiehe.common.AuditService;
import com.xiehe.common.BusinessException;
import com.xiehe.common.Constants;
import com.xiehe.common.PasswordUtil;
import com.xiehe.dto.ChangePasswordDTO;
import com.xiehe.dto.UpdateProfileDTO;
import com.xiehe.entity.FileInfo;
import com.xiehe.entity.User;
import com.xiehe.mapper.FileMapper;
import com.xiehe.mapper.UserMapper;
import com.xiehe.service.UserService;
import com.xiehe.vo.UserStatsVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 用户服务实现
 */
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private FileMapper fileMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private AuditService auditService;

    /** 文件存储根目录(与文件上传共用配置) */
    @Value("${xiehe.upload-dir}")
    private String uploadDir;

    /** 头像允许的图片扩展名(不含点号,小写;同样禁止svg等可执行内容) */
    private static final Set<String> AVATAR_EXTENSIONS = new HashSet<>(Arrays.asList(
            "png", "jpg", "jpeg", "gif", "webp", "bmp", "ico"));

    @Override
    public User uploadAvatar(Long userId, MultipartFile file) {
        User user = getProfile(userId);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请选择要上传的图片");
        }
        // 1. 扩展名白名单校验(杜绝上传非图片内容)
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        }
        if (!AVATAR_EXTENSIONS.contains(ext)) {
            throw new BusinessException(400, "头像仅支持图片格式(png/jpg/jpeg/gif/webp/bmp)");
        }
        // 2. 大小限制 2MB
        if (file.getSize() > 2 * 1024 * 1024) {
            throw new BusinessException(400, "头像图片不能超过2MB");
        }
        // 3. 保存到 uploads/avatar 目录
        File dir = new File(uploadDir, "avatar");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new BusinessException(500, "头像目录创建失败");
        }
        String filename = "avatar_" + userId + "_" + System.currentTimeMillis() + "." + ext;
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, new File(dir, filename).toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException(500, "头像保存失败,请重试");
        }
        // 4. 删除旧头像(仅清理本地上传的,不影响外链URL头像)
        if (StringUtils.hasText(user.getAvatar()) && user.getAvatar().startsWith("/uploads/avatar/")) {
            File old = new File(uploadDir, user.getAvatar().substring("/uploads/".length()));
            if (old.exists()) {
                old.delete();
            }
        }
        // 5. 更新资料
        user.setAvatar("/uploads/avatar/" + filename);
        userMapper.updateById(user);
        auditService.log(userId, "修改头像", "上传了新头像");
        return user;
    }

    @Override
    public User getProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(401, "用户不存在");
        }
        return user;
    }

    @Override
    public User updateProfile(Long userId, UpdateProfileDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(401, "用户不存在");
        }
        // 安全: 头像URL协议白名单(仅允许本地上传路径或 http/https 外链,杜绝 javascript:/data: 等协议)
        String avatar = dto.getAvatar();
        if (StringUtils.hasText(avatar)
                && !avatar.startsWith("/uploads/")
                && !avatar.startsWith("http://")
                && !avatar.startsWith("https://")) {
            throw new BusinessException(400, "头像地址仅支持站内上传路径或 http/https 链接");
        }
        user.setNickname(dto.getNickname());
        user.setAvatar(avatar);
        userMapper.updateById(user);
        auditService.log(userId, "修改资料", "用户[" + user.getUsername() + "] 更新了昵称/头像");
        return user;
    }

    @Override
    public void changePassword(Long userId, ChangePasswordDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(401, "用户不存在");
        }
        // 校验原密码(自动兼容有盐/无盐两种存储格式)
        if (!PasswordUtil.verify(dto.getOldPassword(), user.getPassword(), user.getPasswordSalt())) {
            throw new BusinessException("原密码不正确");
        }
        // 新密码使用 BCrypt 存储(自带随机盐,不再需要额外 salt 字段)
        user.setPasswordSalt("");
        user.setPassword(PasswordUtil.hash(dto.getNewPassword()));
        userMapper.updateById(user);
        // 安全: 修改密码后,该用户所有已签发的旧令牌立即失效(防已泄露令牌继续使用)
        invalidateUserTokens(userId);
        auditService.log(userId, "修改密码", "用户[" + user.getUsername() + "] 修改密码,旧令牌已全部失效");
    }

    /** 批量作废某用户的所有登录令牌(改密/禁用时调用) */
    private void invalidateUserTokens(Long userId) {
        String setKey = Constants.REDIS_USER_TOKENS + userId;
        Set<Object> tokens = redisTemplate.opsForSet().members(setKey);
        if (tokens != null) {
            for (Object t : tokens) {
                redisTemplate.delete(Constants.REDIS_TOKEN_KEY + t);
            }
        }
        redisTemplate.delete(setKey);
        // 清除用户状态缓存,下次请求回源数据库
        redisTemplate.delete(Constants.REDIS_USER_STATUS + userId);
    }

    @Override
    public User getStorage(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(401, "用户不存在");
        }
        return user;
    }

    @Override
    public List<User> searchUsers(Long userId, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        // 模糊搜索用户名或昵称,排除自己
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.ne(User::getId, userId)
                .and(w -> w.like(User::getUsername, keyword)
                        .or().like(User::getNickname, keyword))
                .last("LIMIT 20");
        return userMapper.selectList(wrapper);
    }

    @Override
    public UserStatsVO getStats(Long userId) {
        UserStatsVO vo = new UserStatsVO();
        // 我的文件数(正常状态)
        vo.setFileCount(fileMapper.selectCount(
                new LambdaQueryWrapper<FileInfo>()
                        .eq(FileInfo::getUserId, userId)
                        .eq(FileInfo::getStatus, Constants.STATUS_NORMAL)));
        // 我的公开文件数
        vo.setPublicCount(fileMapper.selectCount(
                new LambdaQueryWrapper<FileInfo>()
                        .eq(FileInfo::getUserId, userId)
                        .eq(FileInfo::getStatus, Constants.STATUS_NORMAL)
                        .eq(FileInfo::getIsPublic, Constants.FILE_PUBLIC)));
        // 我的收藏数
        vo.setFavoriteCount(fileMapper.selectCount(
                new LambdaQueryWrapper<FileInfo>()
                        .eq(FileInfo::getUserId, userId)
                        .eq(FileInfo::getStatus, Constants.STATUS_NORMAL)
                        .eq(FileInfo::getIsFavorite, Constants.FAVORITE_YES)));
        // 我的文件总下载量(SQL SUM 聚合)
        vo.setTotalDownloads(fileMapper.sumMyDownloads(userId));
        // 我收到的共享文件数(别人授权给我的,排除自己拥有的;SQL 计数)
        vo.setSharedCount(fileMapper.countSharedFiles(userId));
        return vo;
    }
}
