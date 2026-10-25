package com.xiehe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiehe.common.BusinessException;
import com.xiehe.entity.FriendRelation;
import com.xiehe.entity.User;
import com.xiehe.mapper.FriendRelationMapper;
import com.xiehe.mapper.UserMapper;
import com.xiehe.service.FriendService;
import com.xiehe.vo.FriendVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 好友服务实现
 * 好友关系双向存储: 添加好友时同时写入 (A,B) 和 (B,A)
 * 好友是"快捷授权对象"——但非好友也可以被手动授权(见文件服务)
 */
@Service
public class FriendServiceImpl implements FriendService {

    @Autowired
    private FriendRelationMapper friendMapper;

    @Autowired
    private UserMapper userMapper;

    @Override
    public List<FriendVO> listFriends(Long userId) {
        List<FriendRelation> relations = friendMapper.selectList(
                new LambdaQueryWrapper<FriendRelation>()
                        .eq(FriendRelation::getUserId, userId)
                        .eq(FriendRelation::getStatus, 1));
        List<FriendVO> result = new ArrayList<>();
        for (FriendRelation r : relations) {
            User friend = userMapper.selectById(r.getFriendId());
            if (friend != null) {
                result.add(FriendVO.of(friend,
                        r.getCreateTime() == null ? "" : r.getCreateTime().toString()));
            }
        }
        return result;
    }

    @Override
    @Transactional
    public void addFriend(Long userId, Long friendId) {
        if (userId.equals(friendId)) {
            throw new BusinessException("不能添加自己为好友");
        }
        User friend = userMapper.selectById(friendId);
        if (friend == null) {
            throw new BusinessException("要添加的用户不存在");
        }
        // 已是好友则提示
        Long exists = friendMapper.selectCount(
                new LambdaQueryWrapper<FriendRelation>()
                        .eq(FriendRelation::getUserId, userId)
                        .eq(FriendRelation::getFriendId, friendId));
        if (exists > 0) {
            throw new BusinessException("你们已经是好友了");
        }
        // 双向写入
        FriendRelation a2b = new FriendRelation();
        a2b.setUserId(userId);
        a2b.setFriendId(friendId);
        a2b.setStatus(1);
        friendMapper.insert(a2b);

        FriendRelation b2a = new FriendRelation();
        b2a.setUserId(friendId);
        b2a.setFriendId(userId);
        b2a.setStatus(1);
        friendMapper.insert(b2a);
    }

    @Override
    @Transactional
    public void removeFriend(Long userId, Long friendId) {
        // 双向删除
        friendMapper.delete(new LambdaQueryWrapper<FriendRelation>()
                .eq(FriendRelation::getUserId, userId)
                .eq(FriendRelation::getFriendId, friendId));
        friendMapper.delete(new LambdaQueryWrapper<FriendRelation>()
                .eq(FriendRelation::getUserId, friendId)
                .eq(FriendRelation::getFriendId, userId));
    }
}
