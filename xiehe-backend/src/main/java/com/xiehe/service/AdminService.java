package com.xiehe.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiehe.entity.FileInfo;
import com.xiehe.entity.OperationLog;
import com.xiehe.entity.User;
import com.xiehe.vo.AdminStatsVO;

/**
 * 管理后台服务
 */
public interface AdminService {

    /** 平台统计数据(Redis缓存30秒; refresh=true 强制刷新) */
    AdminStatsVO stats(boolean refresh);

    /** 分页查询用户 */
    Page<User> listUsers(int page, int size, String keyword);

    /** 启用/禁用用户(不能禁用自己) */
    void updateUserStatus(Long operatorId, Long targetId, Integer status);

    /** 调整用户类型 NORMAL/VIP(升级存储空间) */
    void updateUserType(Long operatorId, Long targetId, String userType);

    /** 分页查询文件 */
    Page<FileInfo> listFiles(int page, int size, String keyword);

    /** 管理员删除文件(逻辑删除) */
    void deleteFile(Long operatorId, Long fileId);

    /** 分页查询操作日志 */
    Page<OperationLog> listLogs(int page, int size);

    /** 写操作日志 */
    void log(Long operatorId, String action, String detail);
}
