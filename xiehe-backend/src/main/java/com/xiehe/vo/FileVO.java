package com.xiehe.vo;

import com.xiehe.entity.FileInfo;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件视图对象: 在文件基本信息基础上,
 * 附带 拥有者昵称 和 当前登录用户对该文件的权限等级
 */
@Data
public class FileVO {

    private Long id;
    private Long userId;            // 拥有者ID
    private String ownerName;       // 拥有者昵称
    private String filename;        // 显示名称
    private String originalName;    // 原始名称
    private String fileType;        // 类型
    private Long size;              // 大小(字节)
    private String fileUrl;         // 访问路径
    private Integer isPublic;       // 是否公开
    private Integer isFavorite;     // 是否收藏(星标)
    private Integer downloadCount;  // 下载次数
    private LocalDateTime createTime;

    /**
     * 当前用户对该文件的权限等级:
     * OWNER 拥有者 / EDIT 可编辑 / VIEW 可查看 / NONE 无权限
     */
    private String myPermission;

    /** 从实体转换(默认权限NONE,由Service补充计算) */
    public static FileVO from(FileInfo f) {
        FileVO vo = new FileVO();
        vo.setId(f.getId());
        vo.setUserId(f.getUserId());
        vo.setFilename(f.getFilename());
        vo.setOriginalName(f.getOriginalName());
        vo.setFileType(f.getFileType());
        vo.setSize(f.getSize());
        vo.setFileUrl(f.getFileUrl());
        vo.setIsPublic(f.getIsPublic());
        vo.setIsFavorite(f.getIsFavorite());
        vo.setDownloadCount(f.getDownloadCount());
        vo.setCreateTime(f.getCreateTime());
        vo.setMyPermission("NONE");
        return vo;
    }
}
