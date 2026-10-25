package com.xiehe.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 批量授权请求参数: 把一批文件同时授权给同一个用户
 */
@Data
public class BatchGrantDTO {

    /** 待授权文件ID列表 */
    @NotEmpty(message = "请至少选择一个文件")
    private List<Long> fileIds;

    /** 被授权用户ID */
    @NotNull(message = "被授权用户ID不能为空")
    private Long userId;

    /** 权限: VIEW查看 / EDIT编辑 */
    @NotBlank(message = "权限类型不能为空")
    private String permission;
}
