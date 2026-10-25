package com.xiehe.vo;

import lombok.Data;

/**
 * 用户中心统计
 */
@Data
public class UserStatsVO {

    /** 我的文件总数(不含回收站) */
    private Long fileCount;

    /** 我的公开文件数 */
    private Long publicCount;

    /** 我的收藏数 */
    private Long favoriteCount;

    /** 我的文件总下载量 */
    private Long totalDownloads;

    /** 我收到的共享文件数(别人授权给我的) */
    private Long sharedCount;
}
