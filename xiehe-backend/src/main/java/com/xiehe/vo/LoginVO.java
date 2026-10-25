package com.xiehe.vo;

import com.xiehe.entity.User;
import lombok.Data;

/**
 * 登录成功返回: 令牌 + 用户信息
 */
@Data
public class LoginVO {

    /** JWT 令牌 */
    private String token;

    /** 用户信息(不含密码) */
    private User user;

    public static LoginVO of(String token, User user) {
        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setUser(user);
        return vo;
    }
}
