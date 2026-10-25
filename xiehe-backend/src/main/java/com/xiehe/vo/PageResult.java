package com.xiehe.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 通用分页返回结构
 * 前端分页组件(el-pagination)直接消费该结构
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    /** 当前页数据 */
    private List<T> records;

    /** 满足条件的总记录数 */
    private long total;

    /** 当前页码(从1开始) */
    private long page;

    /** 每页大小 */
    private long size;
}
