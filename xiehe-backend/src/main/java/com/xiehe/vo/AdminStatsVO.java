package com.xiehe.vo;

import lombok.Data;

import java.util.Map;

/**
 * 管理后台统计数据
 */
@Data
public class AdminStatsVO {

    private Long userCount;        // 用户总数
    private Long vipCount;         // 高级用户数
    private Long fileCount;        // 文件总数
    private Long publicFileCount;  // 公开文件数
    private Long totalDownloads;   // 总下载次数
    private Long totalStorageUsed; // 总已用存储(字节)
    private Long totalStorageLimit;// 总存储上限(字节)

    /** 文件类型分布: DOC/IMAGE/ZIP/VIDEO -> 数量 */
    private Map<String, Long> fileTypeDist;

    /** 用户类型分布: NORMAL/VIP -> 数量 */
    private Map<String, Long> userTypeDist;
}
