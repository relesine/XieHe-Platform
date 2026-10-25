package com.xiehe.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分享链接视图
 */
@Data
public class ShareVO {

    private Long id;

    /** 文件ID */
    private Long fileId;

    /** 文件名 */
    private String filename;

    /** 分享令牌 */
    private String token;

    /** 完整访问链接 */
    private String url;

    /** 是否有访问密码 */
    private Boolean hasPassword;

    /** 过期时间(为空=永久有效) */
    private LocalDateTime expiresAt;

    /** 是否已过期 */
    private Boolean expired;

    private LocalDateTime createTime;
}
