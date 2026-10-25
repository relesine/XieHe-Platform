package com.xiehe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiehe.entity.FileInfo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 文件表 Mapper
 * 除 MyBatis-Plus 通用 CRUD 外,提供统计聚合与共享列表分页的自定义SQL。
 * 统计类查询全部下推到数据库聚合,避免全表载入内存。
 */
public interface FileMapper extends BaseMapper<FileInfo> {

    /** 公开文件数(COUNT聚合,替代全表扫描) */
    @Select("SELECT COUNT(*) FROM t_file WHERE is_public = 1")
    Long countPublicFiles();

    /** 文件类型分布: file_type -> 数量(所有状态,保持与原内存聚合语义一致) */
    @Select("SELECT file_type AS typeKey, COUNT(*) AS cnt FROM t_file GROUP BY file_type")
    List<Map<String, Object>> countGroupByFileType();

    /** 全平台总下载次数(SUM聚合) */
    @Select("SELECT IFNULL(SUM(download_count), 0) FROM t_file")
    Long sumDownloadCount();

    /**
     * 某用户"正常文件"的累计下载量(SUM聚合,供用户中心统计)
     */
    @Select("SELECT IFNULL(SUM(download_count), 0) FROM t_file WHERE user_id = #{userId} AND status = 1")
    Long sumMyDownloads(@Param("userId") Long userId);

    /**
     * 下载次数原子自增(数据库层面 +1),避免"读-改-写"在高并发下丢失计数
     */
    @Update("UPDATE t_file SET download_count = download_count + 1 WHERE id = #{id}")
    int incrementDownloadCount(@Param("id") Long id);

    /**
     * "与我共享"列表 SQL 分页: 授权表 JOIN 文件表
     * 条件: 授权给了当前用户 + 文件正常 + 排除自己拥有的文件
     */
    @Select("SELECT f.* FROM t_file f INNER JOIN t_file_permission p ON p.file_id = f.id " +
            "WHERE p.user_id = #{userId} AND f.status = 1 AND f.user_id != #{userId} " +
            "ORDER BY f.create_time DESC LIMIT #{offset}, #{size}")
    List<FileInfo> selectSharedFiles(@Param("userId") Long userId,
                                     @Param("offset") long offset,
                                     @Param("size") long size);

    /** "与我共享"列表总数(供分页组件使用) */
    @Select("SELECT COUNT(*) FROM t_file f INNER JOIN t_file_permission p ON p.file_id = f.id " +
            "WHERE p.user_id = #{userId} AND f.status = 1 AND f.user_id != #{userId}")
    Long countSharedFiles(@Param("userId") Long userId);
}
