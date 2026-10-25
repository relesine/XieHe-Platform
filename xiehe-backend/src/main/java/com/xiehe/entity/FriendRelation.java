package com.xiehe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 好友关系实体 (对应表 t_friend_relation)
 * 好友关系双向存储: A加B为好友 -> 存(A,B)和(B,A)两条记录
 */
@Data
@TableName("t_friend_relation")
public class FriendRelation {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发起方用户ID */
    private Long userId;

    /** 好友用户ID */
    private Long friendId;

    /** 状态: 1已添加 */
    private Integer status;

    private LocalDateTime createTime;
}
