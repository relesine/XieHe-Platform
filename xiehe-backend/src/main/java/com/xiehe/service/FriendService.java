package com.xiehe.service;

import com.xiehe.vo.FriendVO;

import java.util.List;

/**
 * 好友服务
 */
public interface FriendService {

    /** 我的好友列表 */
    List<FriendVO> listFriends(Long userId);

    /** 添加好友(按用户ID) */
    void addFriend(Long userId, Long friendId);

    /** 删除好友 */
    void removeFriend(Long userId, Long friendId);
}
