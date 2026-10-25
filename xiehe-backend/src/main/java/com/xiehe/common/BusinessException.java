package com.xiehe.common;

/**
 * 业务异常: 在 Service 层抛出,由全局异常处理器统一捕获
 * 例如: 用户名已存在、文件不存在、无权限操作等
 */
public class BusinessException extends RuntimeException {

    /** 错误码,默认400(业务错误) */
    private final Integer code;

    public BusinessException(String message) {
        super(message);
        this.code = 400;
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }
}
