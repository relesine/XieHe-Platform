package com.xiehe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiehe.entity.User;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 用户表 Mapper
 * 管理统计的用户维度数据使用数据库聚合,避免全表载入内存。
 */
public interface UserMapper extends BaseMapper<User> {

    /** 用户类型分布: user_type -> 数量 */
    @Select("SELECT user_type AS typeKey, COUNT(*) AS cnt FROM t_user GROUP BY user_type")
    List<Map<String, Object>> countGroupByUserType();

    /** 全平台存储使用汇总: 已用总字节 + 配额总字节(SUM聚合) */
    @Select("SELECT IFNULL(SUM(used_storage), 0) AS totalUsed, IFNULL(SUM(storage_limit), 0) AS totalLimit FROM t_user")
    Map<String, Object> sumStorage();
}
