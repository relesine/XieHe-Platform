package com.xiehe.vo;

import com.xiehe.entity.User;
import lombok.Data;

/**
 * 好友视图: 好友关系 + 好友用户信息
 */
@Data
public class FriendVO {

    /** 好友用户信息 */
    private User friend;

    /** 添加时间 */
    private String createTime;

    public static FriendVO of(User friend, String createTime) {
        FriendVO vo = new FriendVO();
        vo.setFriend(friend);
        vo.setCreateTime(createTime);
        return vo;
    }
}
