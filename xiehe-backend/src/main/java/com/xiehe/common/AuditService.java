package com.xiehe.common;

import com.xiehe.entity.OperationLog;
import com.xiehe.mapper.OperationLogMapper;
import com.xiehe.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 操作审计服务
 * 记录用户在平台上的关键操作(登录/上传/删除/授权/分享等)到 t_operation_log,
 * 由管理后台"操作日志"页面统一查看,实现用户端操作留痕。
 *
 * 审计日志包含客户端 IP(取自 UserContext,由 AuthInterceptor 写入),
 * 满足"操作人/操作内容/时间/IP"四要素审计标准。
 */
@Component
public class AuditService {

    @Autowired
    private OperationLogMapper logMapper;

    @Autowired
    private UserMapper userMapper;

    /**
     * 记录一条操作日志(自动携带当前请求IP)
     *
     * @param userId 操作人用户ID(可为null,如匿名场景)
     * @param action 操作动作(如: 上传文件 / 删除文件)
     * @param detail 操作详情描述
     */
    public void log(Long userId, String action, String detail) {
        log(userId, action, detail, UserContext.getIp());
    }

    /**
     * 记录一条操作日志(显式指定IP,用于登录等拦截器白名单场景)
     *
     * @param userId 操作人用户ID(可为null,如匿名场景)
     * @param action 操作动作(如: 上传文件 / 删除文件)
     * @param detail 操作详情描述
     * @param ip     客户端IP(可为null)
     */
    public void log(Long userId, String action, String detail, String ip) {
        try {
            OperationLog log = new OperationLog();
            log.setUserId(userId);
            if (userId != null) {
                com.xiehe.entity.User user = userMapper.selectById(userId);
                log.setUsername(user != null ? user.getUsername() : "未知");
            } else {
                log.setUsername("匿名");
            }
            log.setAction(action);
            log.setDetail(detail);
            log.setIp(ip);
            logMapper.insert(log);
        } catch (Exception e) {
            // 审计日志失败不影响主业务流程
        }
    }
}
