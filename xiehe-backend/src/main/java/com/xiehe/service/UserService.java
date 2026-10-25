package com.xiehe.service;

import com.xiehe.dto.ChangePasswordDTO;
import com.xiehe.dto.UpdateProfileDTO;
import com.xiehe.entity.User;
import com.xiehe.vo.UserStatsVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 用户服务: 个人资料 / 存储空间 / 用户搜索 / 用户统计 / 头像上传
 */
public interface UserService {

    /** 获取当前用户信息 */
    User getProfile(Long userId);

    /** 修改个人资料(昵称/头像) */
    User updateProfile(Long userId, UpdateProfileDTO dto);

    /** 上传本地头像(仅图片格式,存 uploads/avatar 目录) */
    User uploadAvatar(Long userId, MultipartFile file);

    /** 修改密码 */
    void changePassword(Long userId, ChangePasswordDTO dto);

    /** 获取存储空间使用情况 */
    User getStorage(Long userId);

    /** 按关键词搜索用户(排除自己),用于添加好友/授权 */
    List<User> searchUsers(Long userId, String keyword);

    /** 用户中心统计(我的文件数/公开数/收藏数/下载量/收到的共享) */
    UserStatsVO getStats(Long userId);
}
