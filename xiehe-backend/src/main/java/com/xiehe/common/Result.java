package com.xiehe.common;

import lombok.Data;

/**
 * 统一接口返回结果封装
 * 所有接口都返回该结构,保证前后端数据格式一致
 */
@Data
public class Result<T> {

    /** 状态码: 200成功 / 400业务错误 / 401未登录 / 403无权限 / 500系统错误 */
    private Integer code;

    /** 提示信息 */
    private String msg;

    /** 业务数据 */
    private T data;

    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.setCode(200);
        r.setMsg("操作成功");
        r.setData(data);
        return r;
    }

    public static <T> Result<T> success(String msg, T data) {
        Result<T> r = new Result<>();
        r.setCode(200);
        r.setMsg(msg);
        r.setData(data);
        return r;
    }

    public static <T> Result<T> error(Integer code, String msg) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMsg(msg);
        return r;
    }

    public static <T> Result<T> error(String msg) {
        return error(400, msg);
    }
}
