package com.xiehe.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiehe.common.BusinessException;
import com.xiehe.common.Constants;
import com.xiehe.common.PageUtil;
import com.xiehe.common.Result;
import com.xiehe.common.UserContext;
import com.xiehe.entity.FileInfo;
import com.xiehe.entity.OperationLog;
import com.xiehe.entity.User;
import com.xiehe.mapper.UserMapper;
import com.xiehe.service.AdminService;
import com.xiehe.vo.AdminStatsVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 管理后台接口: 仅 ADMIN 角色可访问
 *
 * checkAdmin 回源数据库校验角色,避免依赖 JWT claim 中的旧角色
 * (角色变更后旧令牌立即失去管理权限)。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @Autowired
    private UserMapper userMapper;

    /** 校验当前用户必须是管理员(回源DB,杜绝JWT角色伪造/过期) */
    private void checkAdmin() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录,请先登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null || !Constants.ROLE_ADMIN.equals(user.getRole())) {
            throw new BusinessException(403, "无管理员权限");
        }
    }

    /** 平台统计(refresh=true 强制刷新缓存) */
    @GetMapping("/stats")
    public Result<AdminStatsVO> stats(@RequestParam(defaultValue = "false") boolean refresh) {
        checkAdmin();
        return Result.success(adminService.stats(refresh));
    }

    /** 用户分页列表 */
    @GetMapping("/user/list")
    public Result<Page<User>> userList(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "10") int size,
                                       @RequestParam(required = false) String keyword) {
        checkAdmin();
        return Result.success(adminService.listUsers((int) PageUtil.page(page), (int) PageUtil.size(size), keyword));
    }

    /** 启用/禁用用户 */
    @PutMapping("/user/{id}/status")
    public Result<Void> userStatus(@PathVariable Long id, @RequestParam Integer status) {
        checkAdmin();
        adminService.updateUserStatus(UserContext.getUserId(), id, status);
        return Result.success("操作成功", null);
    }

    /** 调整用户类型 NORMAL/VIP */
    @PutMapping("/user/{id}/type")
    public Result<Void> userType(@PathVariable Long id, @RequestParam String userType) {
        checkAdmin();
        adminService.updateUserType(UserContext.getUserId(), id, userType);
        return Result.success("操作成功", null);
    }

    /** 文件分页列表 */
    @GetMapping("/file/list")
    public Result<Page<FileInfo>> fileList(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int size,
                                           @RequestParam(required = false) String keyword) {
        checkAdmin();
        return Result.success(adminService.listFiles((int) PageUtil.page(page), (int) PageUtil.size(size), keyword));
    }

    /** 管理员删除文件 */
    @DeleteMapping("/file/{id}")
    public Result<Void> fileDelete(@PathVariable Long id) {
        checkAdmin();
        adminService.deleteFile(UserContext.getUserId(), id);
        return Result.success("删除成功", null);
    }

    /** 操作日志分页 */
    @GetMapping("/log/list")
    public Result<Page<OperationLog>> logList(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "10") int size) {
        checkAdmin();
        return Result.success(adminService.listLogs((int) PageUtil.page(page), (int) PageUtil.size(size)));
    }
}
