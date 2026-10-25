package com.xiehe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志实体 (对应表 t_operation_log)
 * 记录管理后台的关键操作,便于审计追溯
 */
@Data
@TableName("t_operation_log")
public class OperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作人用户ID */
    private Long userId;

    /** 操作人用户名 */
    private String username;

    /** 操作动作,如: 用户禁用/文件删除 */
    private String action;

    /** 操作详情描述 */
    private String detail;

    /** 操作人客户端IP(审计四要素之一) */
    private String ip;

    private LocalDateTime createTime;
}
