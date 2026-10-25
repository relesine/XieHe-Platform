package com.xiehe.controller;

import com.xiehe.common.IpUtil;
import com.xiehe.common.Result;
import com.xiehe.dto.LoginDTO;
import com.xiehe.dto.RegisterDTO;
import com.xiehe.service.AuthService;
import com.xiehe.vo.LoginVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

/**
 * 认证接口: 注册 / 登录 / 登出
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    /** 注册: POST /api/auth/register */
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterDTO dto) {
        authService.register(dto);
        return Result.success("注册成功", null);
    }

    /** 登录: POST /api/auth/login 返回 token + 用户信息(携带客户端IP用于防暴力破解) */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto,
                                 HttpServletRequest request) {
        return Result.success("登录成功", authService.login(dto, IpUtil.getClientIp(request)));
    }

    /** 登出: POST /api/auth/logout (需携带token) */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (auth != null && auth.startsWith("Bearer ")) {
            authService.logout(auth.substring(7));
        }
        return Result.success("已退出登录", null);
    }
}
