package com.xiehe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件实体 (对应表 t_file)
 * status 语义: 1=正常 / 2=回收站(手动管理,不使用MyBatis Plus逻辑删除,便于回收站查询)
 */
@Data
@TableName("t_file")
public class FileInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 文件所属用户ID(拥有者) */
    private Long userId;

    /** 文件显示名称(可被重命名) */
    private String filename;

    /** 原始文件名(上传时) */
    private String originalName;

    /** 文件类型: DOC文档 / IMAGE图片 / ZIP压缩包 / VIDEO视频 */
    private String fileType;

    /** 文件大小(字节) */
    private Long size;

    /** 文件访问路径(相对URL,如 /uploads/20260910/xxx.png) */
    private String fileUrl;

    /** 是否公开: 1公开 / 0私有 */
    private Integer isPublic;

    /** 是否收藏(星标): 1收藏 / 0未收藏(拥有者视角) */
    private Integer isFavorite;

    /** 下载次数 */
    private Integer downloadCount;

    /** 状态: 1正常 / 2回收站 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
