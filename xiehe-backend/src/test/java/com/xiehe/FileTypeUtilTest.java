package com.xiehe;

import com.xiehe.common.BusinessException;
import com.xiehe.common.FileTypeUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 文件类型工具单元测试
 * 验证上传白名单/类型识别/文本预览判断的核心逻辑,
 * 确保 .html/.js/.svg 等"活跃内容文件"始终被拒绝(存储型XSS防护)。
 */
@DisplayName("文件类型工具测试")
class FileTypeUtilTest {

    @Test
    @DisplayName("白名单: 常见文档/图片/压缩包/视频均允许")
    void allowedExtensionsPass() {
        assertDoesNotThrow(() -> FileTypeUtil.checkAllowedExtension("报告.docx"));
        assertDoesNotThrow(() -> FileTypeUtil.checkAllowedExtension("photo.JPG"));
        assertDoesNotThrow(() -> FileTypeUtil.checkAllowedExtension("archive.zip"));
        assertDoesNotThrow(() -> FileTypeUtil.checkAllowedExtension("movie.mp4"));
        assertDoesNotThrow(() -> FileTypeUtil.checkAllowedExtension("code.java"));
        assertDoesNotThrow(() -> FileTypeUtil.checkAllowedExtension("config.yaml"));
    }

    @Test
    @DisplayName("安全: html/js/svg/exe 等可执行或危险文件一律拒绝")
    void dangerousExtensionsRejected() {
        assertThrows(BusinessException.class, () -> FileTypeUtil.checkAllowedExtension("index.html"));
        assertThrows(BusinessException.class, () -> FileTypeUtil.checkAllowedExtension("script.js"));
        assertThrows(BusinessException.class, () -> FileTypeUtil.checkAllowedExtension("virus.exe"));
        assertThrows(BusinessException.class, () -> FileTypeUtil.checkAllowedExtension("img.svg"));
        assertThrows(BusinessException.class, () -> FileTypeUtil.checkAllowedExtension("noext"));
        assertThrows(BusinessException.class, () -> FileTypeUtil.checkAllowedExtension("name."));
    }

    @Test
    @DisplayName("类型识别: DOC/IMAGE/ZIP/VIDEO 四类正确映射")
    void detectTypeMapping() {
        assertEquals(FileTypeUtil.TYPE_DOC, FileTypeUtil.detectType("说明.txt"));
        assertEquals(FileTypeUtil.TYPE_DOC, FileTypeUtil.detectType("设计.pdf"));
        assertEquals(FileTypeUtil.TYPE_IMAGE, FileTypeUtil.detectType("合影.png"));
        assertEquals(FileTypeUtil.TYPE_IMAGE, FileTypeUtil.detectType("照片.JPEG"));
        assertEquals(FileTypeUtil.TYPE_ZIP, FileTypeUtil.detectType("资料.rar"));
        assertEquals(FileTypeUtil.TYPE_VIDEO, FileTypeUtil.detectType("会议.mp4"));
    }

    @Test
    @DisplayName("文本预览判断: 白名单与前端 isTextExt 保持一致")
    void isTextFileWhitelist() {
        // 允许文本预览
        assertTrue(FileTypeUtil.isTextFile("笔记.txt"));
        assertTrue(FileTypeUtil.isTextFile("README.md"));
        assertTrue(FileTypeUtil.isTextFile("配置.yml"));
        assertTrue(FileTypeUtil.isTextFile("配置.yaml"));
        assertTrue(FileTypeUtil.isTextFile("main.java"));
        assertTrue(FileTypeUtil.isTextFile("app.py"));
        assertTrue(FileTypeUtil.isTextFile("init.sql"));
        // 明确不允许(与前端对齐): 活跃内容文件与二进制文件
        assertFalse(FileTypeUtil.isTextFile("page.html"));
        assertFalse(FileTypeUtil.isTextFile("script.js"));
        assertFalse(FileTypeUtil.isTextFile("style.css"));
        assertFalse(FileTypeUtil.isTextFile("photo.png"));
        assertFalse(FileTypeUtil.isTextFile("video.mp4"));
    }

    @Test
    @DisplayName("大小格式化: 1024 进制换算正确")
    void formatSize() {
        assertEquals("500B", FileTypeUtil.formatSize(500));
        assertEquals("1.0KB", FileTypeUtil.formatSize(1024));
        assertEquals("1.5MB", FileTypeUtil.formatSize(1024L * 1024L * 3 / 2));
        assertTrue(FileTypeUtil.formatSize(1024L * 1024L * 1024L).endsWith("GB"));
    }
}
