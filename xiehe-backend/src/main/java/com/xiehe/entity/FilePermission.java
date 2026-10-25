package com.xiehe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件授权实体 (对应表 t_file_permission)
 * 记录"哪个用户"对"哪个文件"拥有什么权限
 */
@Data
@TableName("t_file_permission")
public class FilePermission {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 文件ID */
    private Long fileId;

    /** 被授权用户ID */
    private Long userId;

    /** 权限: VIEW查看(只能看/下载) / EDIT编辑(可改名/替换) */
    private String permission;

    /** 授权来源: FRIEND好友快捷授权 / MANUAL手动授权 */
    private String source;

    private LocalDateTime createTime;
}
