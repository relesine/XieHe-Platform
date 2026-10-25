package com.xiehe.controller;

import com.xiehe.common.Result;
import com.xiehe.common.UserContext;
import com.xiehe.service.FriendService;
import com.xiehe.vo.FriendVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 好友接口
 */
@RestController
@RequestMapping("/api/friend")
public class FriendController {

    @Autowired
    private FriendService friendService;

    /** 我的好友列表 */
    @GetMapping("/list")
    public Result<List<FriendVO>> list() {
        return Result.success(friendService.listFriends(UserContext.getUserId()));
    }

    /** 添加好友(按用户ID) */
    @PostMapping("/add/{friendId}")
    public Result<Void> add(@PathVariable Long friendId) {
        friendService.addFriend(UserContext.getUserId(), friendId);
        return Result.success("添加好友成功", null);
    }

    /** 删除好友 */
    @DeleteMapping("/{friendId}")
    public Result<Void> remove(@PathVariable Long friendId) {
        friendService.removeFriend(UserContext.getUserId(), friendId);
        return Result.success("已删除好友", null);
    }
}
