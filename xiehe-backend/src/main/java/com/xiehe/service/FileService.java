package com.xiehe.service;

import com.xiehe.dto.BatchDeleteDTO;
import com.xiehe.dto.BatchGrantDTO;
import com.xiehe.dto.GrantPermissionDTO;
import com.xiehe.vo.FileVO;
import com.xiehe.vo.PageResult;
import com.xiehe.vo.PermissionVO;
import com.xiehe.vo.ShareVO;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * 文件服务: 上传/列表/详情/下载/重命名/公开/删除/回收站/收藏/分享 + 权限管理
 */
public interface FileService {

    /** 上传文件(校验存储空间) */
    FileVO upload(Long userId, MultipartFile file);

    /** 我的文件列表(正常状态,分页) */
    PageResult<FileVO> listMyFiles(Long userId, String keyword, long page, long size);

    /** 我的收藏列表(分页) */
    PageResult<FileVO> listFavorites(Long userId, long page, long size);

    /** 回收站列表(分页) */
    PageResult<FileVO> listTrash(Long userId, long page, long size);

    /** 公开文件列表(任何登录用户可浏览,分页) */
    PageResult<FileVO> listPublicFiles(Long userId, String keyword, long page, long size);

    /** 与我共享的文件列表(别人授权给我的,分页) */
    PageResult<FileVO> listSharedFiles(Long userId, long page, long size);

    /** 文件详情(校验查看权限) */
    FileVO getDetail(Long userId, Long fileId);

    /** 下载文件(校验查看权限,下载次数+1) */
    void download(Long userId, Long fileId, HttpServletResponse response);

    /** 文本预览(仅文本类文件,返回前50KB内容) */
    String previewText(Long userId, Long fileId);

    /** 重命名(拥有者或拥有EDIT权限者) */
    FileVO rename(Long userId, Long fileId, String newName);

    /** 替换文件内容(拥有者或拥有EDIT权限者,权限模型闭环) */
    FileVO replaceContent(Long userId, Long fileId, MultipartFile file);

    /** 设置公开/私有(仅拥有者) */
    FileVO setPublic(Long userId, Long fileId, Integer isPublic);

    /** 收藏/取消收藏(仅拥有者) */
    FileVO toggleFavorite(Long userId, Long fileId, Integer isFavorite);

    /** 删除文件->进回收站(仅拥有者) */
    void deleteFile(Long userId, Long fileId);

    /** 批量删除->进回收站(仅能删自己拥有的) */
    void batchDelete(Long userId, BatchDeleteDTO dto);

    /** 从回收站恢复(仅拥有者) */
    FileVO restoreFile(Long userId, Long fileId);

    /** 从回收站彻底删除(仅拥有者,物理删除+清授权+回收空间) */
    void deleteForever(Long userId, Long fileId);

    /** 授权给某用户(仅拥有者) */
    void grantPermission(Long userId, Long fileId, GrantPermissionDTO dto);

    /** 批量授权: 把同一批文件授权给同一用户(仅拥有者,逐文件审计) */
    int batchGrantPermission(Long userId, BatchGrantDTO dto);

    /** 批量生成分享链接(仅拥有者,逐文件复用createShare,返回每个文件的分享结果) */
    List<ShareVO> batchShare(Long userId, List<Long> fileIds, Integer expireDays, String password);

    /** 查看某文件的授权列表(仅拥有者) */
    List<PermissionVO> listPermissions(Long userId, Long fileId);

    /** 取消某用户对该文件的授权(仅拥有者) */
    void removePermission(Long userId, Long fileId, Long targetUserId);

    /** 创建分享链接(仅拥有者): expireDays为有效期天数(空/0=永久),password为访问密码(空=免密) */
    ShareVO createShare(Long userId, Long fileId, Integer expireDays, String password);

    /** 查询某文件的分享链接(仅拥有者) */
    List<ShareVO> listShares(Long userId, Long fileId);

    /** 取消分享(仅拥有者) */
    void deleteShare(Long userId, Long fileId);

    /** 根据分享token查询文件信息(免登录,校验有效期与访问密码) */
    FileVO getShareFile(String token, String password);

    /** 分享token下载(免登录,校验有效期与访问密码) */
    void downloadShare(String token, String password, HttpServletResponse response);
}
