package com.xiehe.controller;

import com.xiehe.common.Result;
import com.xiehe.service.FileService;
import com.xiehe.vo.FileVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;

/**
 * 公开分享接口: 任何人凭分享token可免登录查看/下载
 * 该控制器路径在拦截器白名单中(/api/share/**)
 */
@RestController
@RequestMapping("/api/share")
public class ShareController {

    @Autowired
    private FileService fileService;

    /** 根据token查看分享文件信息(免登录,带密码分享需传password) */
    @GetMapping("/{token}")
    public Result<FileVO> info(@PathVariable String token,
                               @RequestParam(required = false) String password) {
        return Result.success(fileService.getShareFile(token, password));
    }

    /** 根据token下载分享文件(免登录,带密码分享需传password) */
    @GetMapping("/{token}/download")
    public void download(@PathVariable String token,
                         @RequestParam(required = false) String password,
                         HttpServletResponse response) {
        fileService.downloadShare(token, password, response);
    }
}
