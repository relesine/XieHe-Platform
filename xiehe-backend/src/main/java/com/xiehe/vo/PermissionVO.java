package com.xiehe.vo;

import lombok.Data;

/**
 * 授权记录视图: 授权记录 + 被授权用户信息
 */
@Data
public class PermissionVO {

    private Long fileId;
    private Long userId;
    private String username;      // 被授权人用户名
    private String nickname;      // 被授权人昵称
    private String permission;    // VIEW / EDIT
    private String source;        // FRIEND / MANUAL
    private String createTime;
}
