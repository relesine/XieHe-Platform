package com.xiehe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分享链接实体 (对应表 t_share)
 * 拥有者生成分享链接,任何人凭 token 可免登录访问/下载
 */
@Data
@TableName("t_share")
public class Share {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 被分享的文件ID */
    private Long fileId;

    /** 分享令牌(随机UUID,链接唯一标识) */
    private String token;

    /** 创建分享的用户ID(仅拥有者可创建) */
    private Long createUserId;

    /** 访问密码(MD5,为空表示无需密码) */
    private String password;

    /** 过期时间(为空表示永久有效) */
    private LocalDateTime expiresAt;

    private LocalDateTime createTime;
}
