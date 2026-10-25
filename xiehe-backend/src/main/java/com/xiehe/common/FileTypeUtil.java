package com.xiehe.common;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 文件类型与大小工具类
 *
 * 职责:
 * 1. 扩展名白名单校验(上传/替换共用,杜绝 .html/.js/.svg 等活跃内容文件 -> 存储型XSS防护)
 * 2. 按扩展名识别业务类型: DOC文档 / IMAGE图片 / ZIP压缩包 / VIDEO视频
 * 3. 判断是否为可文本预览的文件
 * 4. 字节数转人类可读大小
 *
 * 白名单与前端 accept 保持一致,避免两处规则漂移。
 */
public final class FileTypeUtil {

    /** 允许上传的扩展名白名单(不含点号,小写)。注意: 与前端 FileTable accept 保持一致 */
    public static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList(
            // 文档
            "doc", "docx", "txt", "pdf", "md", "xls", "xlsx", "ppt", "pptx", "log",
            // 图片(不含svg,svg可内嵌脚本,存在XSS风险)
            "png", "jpg", "jpeg", "gif", "bmp", "webp", "ico",
            // 压缩包
            "zip", "rar", "7z", "tar", "gz",
            // 视频
            "mp4", "avi", "mov", "mkv", "flv", "wmv", "webm",
            // 纯文本/代码(用于文本预览;html/js/css等可执行内容一律禁止)
            "java", "py", "c", "cpp", "h", "sql", "json", "yml", "yaml", "xml"
    ));

    /** 业务类型常量 */
    public static final String TYPE_DOC = "DOC";
    public static final String TYPE_IMAGE = "IMAGE";
    public static final String TYPE_ZIP = "ZIP";
    public static final String TYPE_VIDEO = "VIDEO";

    private FileTypeUtil() {
        // 工具类禁止实例化
    }

    /** 校验扩展名是否在白名单内,不合法抛出业务异常(上传与内容替换共用) */
    public static void checkAllowedExtension(String filename) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            throw new BusinessException("不支持无扩展名的文件,请为文件添加扩展名");
        }
        String ext = name.substring(dot + 1);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException("不支持的文件类型: ." + ext
                    + "(仅允许文档/图片/压缩包/视频及常见代码文本文件)");
        }
    }

    /** 根据文件扩展名判断业务类型: DOC/IMAGE/ZIP/VIDEO */
    public static String detectType(String filename) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (name.matches(".*\\.(doc|docx|txt|pdf|md|xls|xlsx|ppt|pptx|log)$")) return TYPE_DOC;
        if (name.matches(".*\\.(png|jpg|jpeg|gif|bmp|webp|svg|ico)$")) return TYPE_IMAGE;
        if (name.matches(".*\\.(zip|rar|7z|tar|gz)$")) return TYPE_ZIP;
        if (name.matches(".*\\.(mp4|avi|mov|mkv|flv|wmv|webm)$")) return TYPE_VIDEO;
        return TYPE_DOC;   // 其他类型默认归为文档
    }

    /** 判断是否文本类文件(可内容预览): 与前端 utils/format.js isTextExt 保持一致 */
    public static boolean isTextFile(String filename) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        return name.matches(".*\\.(txt|md|log|java|py|c|cpp|h|json|yml|yaml|xml|sql)$");
    }

    /** 提取扩展名(不含点号,小写);无扩展名返回空串 */
    public static String getExtension(String filename) {
        String name = filename == null ? "" : filename;
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** 字节数转可读大小(用于日志/提示文案): 1024 -> 1.0KB */
    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1fKB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format(Locale.ROOT, "%.1fMB", bytes / 1024.0 / 1024.0);
        return String.format(Locale.ROOT, "%.2fGB", bytes / 1024.0 / 1024.0 / 1024.0);
    }
}
