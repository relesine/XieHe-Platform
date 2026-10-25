package com.xiehe.common;

/**
 * 分页参数归一化
 * 统一收敛所有列表接口的分页入参,防止超大分页拖垮数据库。
 */
public final class PageUtil {

    /** 单页最大条数 */
    public static final long MAX_PAGE_SIZE = 100;

    private PageUtil() {
    }

    /** 页码最小为 1 */
    public static long page(long page) {
        return Math.max(page, 1);
    }

    /** 每页条数限制在 1~100 */
    public static long size(long size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
