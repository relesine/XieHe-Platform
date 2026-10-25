package com.xiehe.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 授权请求参数: 把某个文件授权给某个用户
 */
@Data
public class GrantPermissionDTO {

    @NotNull(message = "被授权用户ID不能为空")
    private Long userId;

    /** 权限: VIEW查看 / EDIT编辑 */
    @NotBlank(message = "权限类型不能为空")
    private String permission;
}
