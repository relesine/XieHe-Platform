package com.xiehe.controller;

import com.xiehe.common.Result;
import com.xiehe.common.UserContext;
import com.xiehe.dto.ChangePasswordDTO;
import com.xiehe.dto.UpdateProfileDTO;
import com.xiehe.entity.User;
import com.xiehe.service.UserService;
import com.xiehe.vo.UserStatsVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.util.List;

/**
 * 用户接口: 个人资料 / 密码 / 存储空间 / 用户搜索 / 用户统计
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    /** 当前登录用户信息 */
    @GetMapping("/info")
    public Result<User> info() {
        return Result.success(userService.getProfile(UserContext.getUserId()));
    }

    /** 修改个人资料 */
    @PutMapping("/profile")
    public Result<User> updateProfile(@Valid @RequestBody UpdateProfileDTO dto) {
        return Result.success(userService.updateProfile(UserContext.getUserId(), dto));
    }

    /** 上传本地头像(仅图片格式) */
    @PostMapping("/avatar")
    public Result<User> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return Result.success("头像已更新", userService.uploadAvatar(UserContext.getUserId(), file));
    }

    /** 修改密码 */
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        userService.changePassword(UserContext.getUserId(), dto);
        return Result.success("密码修改成功,请重新登录", null);
    }

    /** 存储空间使用情况 */
    @GetMapping("/storage")
    public Result<User> storage() {
        return Result.success(userService.getStorage(UserContext.getUserId()));
    }

    /** 用户中心统计 */
    @GetMapping("/stats")
    public Result<UserStatsVO> stats() {
        return Result.success(userService.getStats(UserContext.getUserId()));
    }

    /** 搜索用户(用于添加好友/授权) */
    @GetMapping("/search")
    public Result<List<User>> search(@RequestParam(required = false) String keyword) {
        return Result.success(userService.searchUsers(UserContext.getUserId(), keyword));
    }
}
