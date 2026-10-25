package com.xiehe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体 (对应表 t_user)
 */
@Data
@TableName("t_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户名(登录账号) */
    private String username;

    /** 密码(MD5加盐加密,序列化为JSON时忽略,防止泄露) */
    @JsonIgnore
    private String password;

    /**
     * 密码盐(UUID前8位,与password配合: 存储值 = MD5(salt + 明文))
     * 历史数据为null表示兼容旧的无盐MD5;序列化时忽略
     */
    @JsonIgnore
    private String passwordSalt;

    /** 昵称 */
    private String nickname;

    /** 头像URL */
    private String avatar;

    /** 角色: USER普通用户 / ADMIN超级管理员 */
    private String role;

    /** 用户类型: NORMAL普通(5GB) / VIP高级(20GB) */
    private String userType;

    /** 存储空间上限(字节) */
    private Long storageLimit;

    /** 已用存储空间(字节) */
    private Long usedStorage;

    /** 状态: 1正常 / 0禁用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
