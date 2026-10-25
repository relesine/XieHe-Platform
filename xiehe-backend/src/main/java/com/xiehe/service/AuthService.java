package com.xiehe.service;

import com.xiehe.dto.LoginDTO;
import com.xiehe.dto.RegisterDTO;
import com.xiehe.vo.LoginVO;

/**
 * 认证服务: 注册 / 登录 / 登出
 */
public interface AuthService {

    /** 注册新用户(默认普通用户5GB空间) */
    void register(RegisterDTO dto);

    /** 登录,返回令牌+用户信息(clientIp用于防暴力破解计数) */
    LoginVO login(LoginDTO dto, String clientIp);

    /** 登出(使令牌在Redis中失效) */
    void logout(String token);
}
