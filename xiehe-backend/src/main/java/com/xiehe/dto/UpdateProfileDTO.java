package com.xiehe.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 修改个人资料请求参数
 */
@Data
public class UpdateProfileDTO {

    @NotBlank(message = "昵称不能为空")
    @Size(max = 20, message = "昵称最长20个字符")
    private String nickname;

    /** 头像URL(可为空) */
    private String avatar;
}
