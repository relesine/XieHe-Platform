package com.xiehe.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 批量操作请求参数
 */
@Data
public class BatchDeleteDTO {

    /** 待处理文件ID列表 */
    @NotEmpty(message = "请至少选择一个文件")
    private List<Long> ids;
}
