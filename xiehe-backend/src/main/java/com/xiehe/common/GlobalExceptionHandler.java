package com.xiehe.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 统一捕获 Controller 抛出的所有异常,转成标准 Result 结构返回
 * 避免把 Java 原始异常堆栈直接暴露给前端
 *
 * 覆盖路径参数缺失/类型错误/数据完整性冲突/非法参数等分支
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 文件上传大小上限(从配置读取,避免硬编码与spring配置不同步) */
    @Value("${spring.servlet.multipart.max-file-size:200MB}")
    private String maxFileSize;

    /** 业务异常: 提示信息就是业务原因 */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /** @Valid 请求体参数校验异常: 收集第一条校验失败信息 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = fieldError != null ? fieldError.getDefaultMessage() : "参数校验失败";
        return Result.error(400, msg);
    }

    /** 表单对象绑定校验异常(BindException) */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBind(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = fieldError != null ? fieldError.getDefaultMessage() : "参数校验失败";
        return Result.error(400, msg);
    }

    /** 路径/查询参数校验异常(ConstraintViolationException) */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraint(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        return Result.error(400, msg.isEmpty() ? "参数校验失败" : msg);
    }

    /** 路径参数类型错误(如 /api/file/abc 的 abc 无法转 Long) */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return Result.error(400, "参数格式错误: " + e.getName());
    }

    /** 缺少路径参数 */
    @ExceptionHandler(MissingPathVariableException.class)
    public Result<Void> handleMissingPath(MissingPathVariableException e) {
        return Result.error(400, "缺少路径参数: " + e.getVariableName());
    }

    /** 请求体JSON格式错误(HttpMessageNotReadableException) */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        return Result.error(400, "请求参数格式错误,请检查JSON格式");
    }

    /** 缺少必填请求参数 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return Result.error(400, "缺少必要参数: " + e.getParameterName());
    }

    /** 请求方式不支持 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return Result.error(400, "不支持的请求方式: " + e.getMethod());
    }

    /** 数据库唯一键冲突(并发重复注册/重复插入) */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicate(DuplicateKeyException e) {
        log.warn("唯一键冲突: {}", e.getMessage());
        return Result.error(400, "数据已存在,请勿重复操作");
    }

    /** 数据完整性冲突(如超长字段、外键约束失败) */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public Result<Void> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("数据完整性冲突: {}", e.getMessage());
        return Result.error(400, "数据格式不合法(可能超出长度限制),请检查后重试");
    }

    /** 非法参数(Service层主动抛出) */
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegalArgument(IllegalArgumentException e) {
        return Result.error(400, e.getMessage() == null ? "参数不合法" : e.getMessage());
    }

    /** 文件超过大小限制(上限值从配置读取,与application.yml保持一致) */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUpload(MaxUploadSizeExceededException e) {
        return Result.error(400, "文件大小超过服务器限制(" + maxFileSize + ")");
    }

    /** 兜底异常: 记录日志,返回通用错误 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error(500, "系统繁忙,请稍后重试");
    }
}
