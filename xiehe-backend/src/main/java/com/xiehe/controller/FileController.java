package com.xiehe.controller;

import com.xiehe.common.PageUtil;
import com.xiehe.common.Result;
import com.xiehe.common.UserContext;
import com.xiehe.dto.BatchDeleteDTO;
import com.xiehe.dto.BatchGrantDTO;
import com.xiehe.dto.GrantPermissionDTO;
import com.xiehe.service.FileService;
import com.xiehe.vo.FileVO;
import com.xiehe.vo.PageResult;
import com.xiehe.vo.PermissionVO;
import com.xiehe.vo.ShareVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.util.List;

/**
 * 文件接口: 上传 / 列表 / 详情 / 下载 / 预览 / 编辑 / 回收站 / 收藏 / 授权 / 分享
 */
@RestController
@RequestMapping("/api/file")
public class FileController {

    @Autowired
    private FileService fileService;

    /** 上传文件: POST /api/file/upload (multipart/form-data) */
    @PostMapping("/upload")
    public Result<FileVO> upload(@RequestParam("file") MultipartFile file) {
        return Result.success("上传成功", fileService.upload(UserContext.getUserId(), file));
    }

    /** 替换文件内容: POST /api/file/{id}/replace (拥有者或EDIT权限,权限模型闭环) */
    @PostMapping("/{id}/replace")
    public Result<FileVO> replace(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return Result.success("替换成功", fileService.replaceContent(UserContext.getUserId(), id, file));
    }

    /** 我的文件列表(分页) */
    @GetMapping("/list/mine")
    public Result<PageResult<FileVO>> listMine(@RequestParam(required = false) String keyword,
                                               @RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "10") long size) {
        return Result.success(fileService.listMyFiles(UserContext.getUserId(), keyword, PageUtil.page(page), PageUtil.size(size)));
    }

    /** 我的收藏列表(分页) */
    @GetMapping("/list/favorites")
    public Result<PageResult<FileVO>> listFavorites(@RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(defaultValue = "10") long size) {
        return Result.success(fileService.listFavorites(UserContext.getUserId(), PageUtil.page(page), PageUtil.size(size)));
    }

    /** 回收站列表(分页) */
    @GetMapping("/list/trash")
    public Result<PageResult<FileVO>> listTrash(@RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "10") long size) {
        return Result.success(fileService.listTrash(UserContext.getUserId(), PageUtil.page(page), PageUtil.size(size)));
    }

    /** 公开文件列表(分页) */
    @GetMapping("/list/public")
    public Result<PageResult<FileVO>> listPublic(@RequestParam(required = false) String keyword,
                                                 @RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "10") long size) {
        return Result.success(fileService.listPublicFiles(UserContext.getUserId(), keyword, PageUtil.page(page), PageUtil.size(size)));
    }

    /** 与我共享的文件列表(分页) */
    @GetMapping("/list/shared")
    public Result<PageResult<FileVO>> listShared(@RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "10") long size) {
        return Result.success(fileService.listSharedFiles(UserContext.getUserId(), PageUtil.page(page), PageUtil.size(size)));
    }

    /** 文件详情(校验查看权限) */
    @GetMapping("/{id}")
    public Result<FileVO> detail(@PathVariable Long id) {
        return Result.success(fileService.getDetail(UserContext.getUserId(), id));
    }

    /** 下载文件(校验查看权限) */
    @GetMapping("/{id}/download")
    public void download(@PathVariable Long id, HttpServletResponse response) {
        fileService.download(UserContext.getUserId(), id, response);
    }

    /** 文本预览(校验查看权限,仅文本类) */
    @GetMapping("/{id}/preview")
    public Result<String> preview(@PathVariable Long id) {
        return Result.success(fileService.previewText(UserContext.getUserId(), id));
    }

    /** 重命名(拥有者或EDIT权限) */
    @PutMapping("/{id}/rename")
    public Result<FileVO> rename(@PathVariable Long id, @RequestParam String filename) {
        return Result.success(fileService.rename(UserContext.getUserId(), id, filename));
    }

    /** 设置公开/私有(仅拥有者) */
    @PutMapping("/{id}/public")
    public Result<FileVO> setPublic(@PathVariable Long id, @RequestParam Integer isPublic) {
        return Result.success(fileService.setPublic(UserContext.getUserId(), id, isPublic));
    }

    /** 收藏/取消收藏(仅拥有者) */
    @PutMapping("/{id}/favorite")
    public Result<FileVO> favorite(@PathVariable Long id, @RequestParam Integer isFavorite) {
        return Result.success(fileService.toggleFavorite(UserContext.getUserId(), id, isFavorite));
    }

    /** 删除文件->进回收站(仅拥有者) */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        fileService.deleteFile(UserContext.getUserId(), id);
        return Result.success("已移入回收站", null);
    }

    /** 批量删除->进回收站(仅能删自己拥有的) */
    @PostMapping("/batch-delete")
    public Result<Void> batchDelete(@Valid @RequestBody BatchDeleteDTO dto) {
        fileService.batchDelete(UserContext.getUserId(), dto);
        return Result.success("已批量移入回收站", null);
    }

    /** 从回收站恢复(仅拥有者) */
    @PutMapping("/{id}/restore")
    public Result<FileVO> restore(@PathVariable Long id) {
        return Result.success(fileService.restoreFile(UserContext.getUserId(), id));
    }

    /** 从回收站彻底删除(仅拥有者) */
    @DeleteMapping("/{id}/forever")
    public Result<Void> deleteForever(@PathVariable Long id) {
        fileService.deleteForever(UserContext.getUserId(), id);
        return Result.success("已彻底删除", null);
    }

    // ==================== 权限管理 ====================

    /** 授权(仅拥有者): 好友或非好友均可被手动授权 */
    @PostMapping("/{id}/permission")
    public Result<Void> grant(@PathVariable Long id, @Valid @RequestBody GrantPermissionDTO dto) {
        fileService.grantPermission(UserContext.getUserId(), id, dto);
        return Result.success("授权成功", null);
    }

    /** 批量授权(仅拥有者): 一批文件同时授权给同一个用户 */
    @PostMapping("/batch-permission")
    public Result<Integer> batchGrant(@Valid @RequestBody BatchGrantDTO dto) {
        int count = fileService.batchGrantPermission(UserContext.getUserId(), dto);
        return Result.success("已批量授权 " + count + " 个文件", count);
    }

    /** 授权列表(仅拥有者) */
    @GetMapping("/{id}/permission")
    public Result<List<PermissionVO>> listPermission(@PathVariable Long id) {
        return Result.success(fileService.listPermissions(UserContext.getUserId(), id));
    }

    /** 取消授权(仅拥有者) */
    @DeleteMapping("/{id}/permission/{userId}")
    public Result<Void> removePermission(@PathVariable Long id, @PathVariable Long userId) {
        fileService.removePermission(UserContext.getUserId(), id, userId);
        return Result.success("已取消授权", null);
    }

    // ==================== 分享链接 ====================

    /** 创建分享链接(仅拥有者): expireDays有效期天数(空/0=永久), password访问密码(空=免密) */
    @PostMapping("/{id}/share")
    public Result<ShareVO> createShare(@PathVariable Long id,
                                       @RequestParam(required = false) Integer expireDays,
                                       @RequestParam(required = false) String password) {
        return Result.success("分享链接已生成",
                fileService.createShare(UserContext.getUserId(), id, expireDays, password));
    }

    /** 批量生成分享链接(仅拥有者): 请求体为文件ID数组 [1,2,3] */
    @PostMapping("/batch-share")
    public Result<List<ShareVO>> batchShare(@RequestBody List<Long> fileIds,
                                            @RequestParam(required = false) Integer expireDays,
                                            @RequestParam(required = false) String password) {
        return Result.success("已批量生成分享链接",
                fileService.batchShare(UserContext.getUserId(), fileIds, expireDays, password));
    }

    /** 查询分享链接(仅拥有者) */
    @GetMapping("/{id}/share")
    public Result<List<ShareVO>> listShare(@PathVariable Long id) {
        return Result.success(fileService.listShares(UserContext.getUserId(), id));
    }

    /** 取消分享(仅拥有者) */
    @DeleteMapping("/{id}/share")
    public Result<Void> deleteShare(@PathVariable Long id) {
        fileService.deleteShare(UserContext.getUserId(), id);
        return Result.success("已取消分享", null);
    }
}
