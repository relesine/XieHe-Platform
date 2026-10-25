package com.xiehe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiehe.common.AuditService;
import com.xiehe.common.BusinessException;
import com.xiehe.common.Constants;
import com.xiehe.common.FileTypeUtil;
import com.xiehe.common.PasswordUtil;
import com.xiehe.dto.BatchDeleteDTO;
import com.xiehe.dto.BatchGrantDTO;
import com.xiehe.dto.GrantPermissionDTO;
import com.xiehe.entity.FileInfo;
import com.xiehe.entity.FilePermission;
import com.xiehe.entity.Share;
import com.xiehe.entity.User;
import com.xiehe.mapper.FileMapper;
import com.xiehe.mapper.FilePermissionMapper;
import com.xiehe.mapper.ShareMapper;
import com.xiehe.mapper.UserMapper;
import com.xiehe.service.FileService;
import com.xiehe.vo.FileVO;
import com.xiehe.vo.PageResult;
import com.xiehe.vo.PermissionVO;
import com.xiehe.vo.ShareVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 文件服务实现
 *
 * 核心权限规则(强制落地):
 * 1. 拥有者: 一切权限(查看/下载/编辑/删除/授权/分享/收藏)
 * 2. 公开文件: 任何登录用户可"查看/下载"; 但"编辑"仍需单独授权
 * 3. 私有文件: 只有 拥有者 或 被授权用户 可查看/下载
 * 4. 编辑权限: 必须 拥有者 或 被授予EDIT权限 的用户
 *
 * 文件生命周期: 上传(status=1) -> 删除进入回收站(status=2) -> 恢复(status=1) 或 彻底删除(物理)
 * 安全设计: 上传/替换仅允许白名单内的文件扩展名,杜绝 .html/.js/.svg 等可被浏览器
 *           直接执行的"活跃内容"文件造成存储型XSS
 */
@Slf4j
@Service
public class FileServiceImpl implements FileService {

    /** 状态常量(引用全局常量类,避免魔法数字) */
    private static final int STATUS_NORMAL = Constants.STATUS_NORMAL;
    private static final int STATUS_TRASH = Constants.STATUS_TRASH;

    @Autowired
    private FileMapper fileMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private FilePermissionMapper permissionMapper;

    @Autowired
    private ShareMapper shareMapper;

    @Value("${xiehe.upload-dir}")
    private String uploadDir;

    /** 前端访问地址(分享链接使用,可配置以适配部署环境) */
    @Value("${xiehe.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Autowired
    private AuditService auditService;

    // ==================== 工具方法 ====================

    /** 计算当前用户对某文件的权限等级: OWNER > EDIT > VIEW > NONE */
    private String myPermission(Long userId, FileInfo file) {
        if (file.getUserId().equals(userId)) {
            return "OWNER";
        }
        // 查授权记录
        FilePermission perm = permissionMapper.selectOne(
                new LambdaQueryWrapper<FilePermission>()
                        .eq(FilePermission::getFileId, file.getId())
                        .eq(FilePermission::getUserId, userId));
        if (perm != null) {
            return perm.getPermission();   // EDIT 或 VIEW
        }
        // 公开文件 -> 可浏览(查看)
        if (file.getIsPublic() != null && file.getIsPublic() == 1) {
            return "VIEW";
        }
        return "NONE";
    }

    /** 校验是否能"查看/下载" */
    private void checkViewable(Long userId, FileInfo file) {
        String perm = myPermission(userId, file);
        if ("NONE".equals(perm)) {
            throw new BusinessException(403, "无权访问该文件(私有文件,未获得授权)");
        }
    }

    /** 校验是否能"编辑"(重命名/替换): 拥有者或EDIT权限 */
    private void checkEditable(Long userId, FileInfo file) {
        String perm = myPermission(userId, file);
        if (!"OWNER".equals(perm) && !"EDIT".equals(perm)) {
            throw new BusinessException(403, "仅文件拥有者或被授予编辑权限的用户可编辑该文件");
        }
    }

    /** 校验是否拥有者 */
    private void checkOwner(Long userId, FileInfo file) {
        if (!file.getUserId().equals(userId)) {
            throw new BusinessException(403, "仅文件拥有者可执行该操作");
        }
    }

    /** 查询"正常状态"文件(回收站文件不可参与正常业务) */
    private FileInfo requireFile(Long fileId) {
        FileInfo file = fileMapper.selectById(fileId);
        if (file == null || file.getStatus() == null || file.getStatus() != STATUS_NORMAL) {
            throw new BusinessException("文件不存在或已被删除");
        }
        return file;
    }

    /** 查询"回收站"文件 */
    private FileInfo requireTrashFile(Long fileId) {
        FileInfo file = fileMapper.selectById(fileId);
        if (file == null || file.getStatus() == null || file.getStatus() != STATUS_TRASH) {
            throw new BusinessException("文件不在回收站中");
        }
        return file;
    }

    /** 实体转VO并补充拥有者昵称与当前用户权限 */
    private FileVO toVO(FileInfo file, Long currentUserId) {
        FileVO vo = FileVO.from(file);
        User owner = userMapper.selectById(file.getUserId());
        vo.setOwnerName(owner != null ? owner.getNickname() : "未知用户");
        vo.setMyPermission(myPermission(currentUserId, file));
        return vo;
    }

    /** 定位磁盘上的物理文件 */
    private File diskFile(FileInfo file) {
        String url = file.getFileUrl();
        return new File(uploadDir, url.replace("/uploads/", "").replace("/", File.separator));
    }

    /** 流式输出文件到响应(下载共用逻辑) */
    private void writeFileToResponse(FileInfo file, HttpServletResponse response) {
        File diskFile = diskFile(file);
        if (!diskFile.exists()) {
            throw new BusinessException("文件在服务器上不存在,可能已被清理");
        }
        try {
            response.setContentType("application/octet-stream");
            String encoded = URLEncoder.encode(file.getFilename(), StandardCharsets.UTF_8).replace("+", "%20");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
            response.setContentLengthLong(diskFile.length());
            try (FileInputStream in = new FileInputStream(diskFile);
                 OutputStream out = response.getOutputStream()) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
                out.flush();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("文件输出失败", e);
            throw new BusinessException("文件传输失败");
        }
    }

    /**
     * 下载次数+1(SQL原子自增)
     * 用 UPDATE ... SET download_count = download_count + 1 在数据库层面原子完成,
     * 高并发下载不会因"读-改-写"丢失计数;同时同步内存对象,避免同请求内读到旧值
     */
    private void increaseDownload(FileInfo file) {
        fileMapper.incrementDownloadCount(file.getId());
        file.setDownloadCount((file.getDownloadCount() == null ? 0 : file.getDownloadCount()) + 1);
    }

    /** 回收/返还拥有者存储空间 */
    private void changeOwnerStorage(Long userId, long delta) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }
        long used = user.getUsedStorage() == null ? 0 : user.getUsedStorage();
        user.setUsedStorage(Math.max(0, used + delta));
        userMapper.updateById(user);
    }

    // ==================== 上传 / 列表 ====================

    @Override
    public FileVO upload(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的文件");
        }
        String originalName = file.getOriginalFilename();
        // 安全: 校验扩展名白名单,禁止上传 .html/.js/.svg 等活跃内容文件
        FileTypeUtil.checkAllowedExtension(originalName);
        // 规范: 文件名长度校验(与 t_file.filename VARCHAR(255) 对齐,超长入库会报500)
        if (originalName != null && originalName.length() > Constants.FILENAME_MAX_LENGTH) {
            throw new BusinessException("文件名不能超过255个字符");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(401, "用户不存在");
        }
        // 1. 存储空间校验: 已用 + 新文件大小 <= 上限
        long newSize = file.getSize();
        long used = user.getUsedStorage() == null ? 0 : user.getUsedStorage();
        long limit = user.getStorageLimit() == null ? 0 : user.getStorageLimit();
        if (used + newSize > limit) {
            throw new BusinessException("存储空间不足: 已用 " + (used / 1024 / 1024)
                    + "MB, 可用 " + ((limit - used) / 1024 / 1024) + "MB");
        }

        // 2. 生成存储路径: uploads/日期/随机文件名.扩展名
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf("."));
        }
        String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String storedName = UUID.randomUUID().toString().replace("-", "") + ext;
        File dir = new File(uploadDir, dateDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File dest = new File(dir, storedName);
        try {
            file.transferTo(dest);
        } catch (Exception e) {
            log.error("文件保存失败", e);
            throw new BusinessException("文件保存失败,请重试");
        }

        // 3. 入库(若失败则删除磁盘文件,避免产生孤儿文件)
        try {
            FileInfo fi = new FileInfo();
            fi.setUserId(userId);
            fi.setFilename(originalName);
            fi.setOriginalName(originalName);
            fi.setFileType(FileTypeUtil.detectType(originalName));
            fi.setSize(newSize);
            fi.setFileUrl("/uploads/" + dateDir + "/" + storedName);
            fi.setIsPublic(Constants.FILE_PRIVATE);    // 默认私有
            fi.setIsFavorite(Constants.FAVORITE_NO);   // 默认未收藏
            fi.setDownloadCount(0);
            fi.setStatus(STATUS_NORMAL);
            fileMapper.insert(fi);

            // 4. 更新已用空间
            changeOwnerStorage(userId, newSize);

            auditService.log(userId, "上传文件", "上传文件[" + originalName + "],大小 "
                    + FileTypeUtil.formatSize(newSize));
            return toVO(fi, userId);
        } catch (RuntimeException e) {
            // DB写入失败: 清理磁盘孤儿文件,保证磁盘与数据库一致
            if (dest.exists() && dest.isFile()) {
                dest.delete();
            }
            log.error("文件入库失败,已清理磁盘文件", e);
            throw e;
        }
    }

    @Override
    public PageResult<FileVO> listMyFiles(Long userId, String keyword, long page, long size) {
        LambdaQueryWrapper<FileInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileInfo::getUserId, userId)
                .eq(FileInfo::getStatus, STATUS_NORMAL);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(FileInfo::getFilename, keyword);
        }
        wrapper.orderByDesc(FileInfo::getCreateTime);
        return toPage(fileMapper.selectPage(new Page<>(page, size), wrapper), userId);
    }

    @Override
    public PageResult<FileVO> listFavorites(Long userId, long page, long size) {
        LambdaQueryWrapper<FileInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileInfo::getUserId, userId)
                .eq(FileInfo::getStatus, STATUS_NORMAL)
                .eq(FileInfo::getIsFavorite, 1);
        wrapper.orderByDesc(FileInfo::getCreateTime);
        return toPage(fileMapper.selectPage(new Page<>(page, size), wrapper), userId);
    }

    @Override
    public PageResult<FileVO> listTrash(Long userId, long page, long size) {
        LambdaQueryWrapper<FileInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileInfo::getUserId, userId)
                .eq(FileInfo::getStatus, STATUS_TRASH);
        wrapper.orderByDesc(FileInfo::getUpdateTime);
        return toPage(fileMapper.selectPage(new Page<>(page, size), wrapper), userId);
    }

    @Override
    public PageResult<FileVO> listPublicFiles(Long userId, String keyword, long page, long size) {
        LambdaQueryWrapper<FileInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileInfo::getIsPublic, 1)
                .eq(FileInfo::getStatus, STATUS_NORMAL);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(FileInfo::getFilename, keyword);
        }
        wrapper.orderByDesc(FileInfo::getCreateTime);
        // 用当前登录用户计算权限: 拥有者看自己的公开文件=OWNER, 其他人=VIEW
        return toPage(fileMapper.selectPage(new Page<>(page, size), wrapper), userId);
    }

    @Override
    public PageResult<FileVO> listSharedFiles(Long userId, long page, long size) {
        // SQL JOIN 分页,避免全量查询后在内存分页
        long offset = (page - 1) * size;
        long total = fileMapper.countSharedFiles(userId);
        if (total == 0 || offset >= total) {
            return new PageResult<>(new ArrayList<>(), total, page, size);
        }
        List<FileVO> records = toVOList(fileMapper.selectSharedFiles(userId, offset, size), userId);
        return new PageResult<>(records, total, page, size);
    }

    /**
     * Page实体分页结果 -> 通用分页VO(批量预取,消除N+1查询)
     *
     * 拥有者昵称与当前用户权限统一批量预取,避免每条记录单独查询。
     */
    private PageResult<FileVO> toPage(Page<FileInfo> p, Long currentUserId) {
        List<FileVO> records = toVOList(p.getRecords(), currentUserId);
        return new PageResult<>(records, p.getTotal(), p.getCurrent(), p.getSize());
    }

    /**
     * 批量把文件实体转为VO(列表场景统一入口,服务端分页/共享列表共用)
     *
     * @param files         当前页文件
     * @param currentUserId 当前登录用户(用于计算 myPermission)
     */
    private List<FileVO> toVOList(List<FileInfo> files, Long currentUserId) {
        if (files == null || files.isEmpty()) {
            return new ArrayList<>();
        }
        // 1) 批量查询所有拥有者: ownerId -> User(替代每个文件 selectById 一次)
        Set<Long> ownerIds = files.stream().map(FileInfo::getUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, User> ownerMap = new HashMap<>();
        if (!ownerIds.isEmpty()) {
            userMapper.selectBatchIds(ownerIds).forEach(u -> ownerMap.put(u.getId(), u));
        }
        // 2) 批量查询当前用户对这些文件的授权: fileId -> FilePermission(替代每个文件 selectOne 一次)
        List<Long> fileIds = files.stream().map(FileInfo::getId).collect(Collectors.toList());
        Map<Long, FilePermission> permMap = new HashMap<>();
        permissionMapper.selectList(new LambdaQueryWrapper<FilePermission>()
                        .eq(FilePermission::getUserId, currentUserId)
                        .in(FilePermission::getFileId, fileIds))
                .forEach(p -> permMap.put(p.getFileId(), p));
        // 3) 逐个组装(权限计算逻辑与单条 myPermission 完全一致,保证结果不变)
        List<FileVO> result = new ArrayList<>(files.size());
        for (FileInfo f : files) {
            FileVO vo = FileVO.from(f);
            User owner = ownerMap.get(f.getUserId());
            vo.setOwnerName(owner != null ? owner.getNickname() : "未知用户");
            vo.setMyPermission(calcPermission(currentUserId, f, permMap.get(f.getId())));
            result.add(vo);
        }
        return result;
    }

    /**
     * 计算权限等级(复用批量预取的授权记录),规则与单条 myPermission 一致:
     * 拥有者 OWNER > 授权记录(EDIT/VIEW) > 公开文件 VIEW > NONE
     */
    private String calcPermission(Long currentUserId, FileInfo file, FilePermission perm) {
        if (file.getUserId().equals(currentUserId)) {
            return Constants.PERM_OWNER;
        }
        if (perm != null) {
            return perm.getPermission();
        }
        if (file.getIsPublic() != null && file.getIsPublic() == Constants.FILE_PUBLIC) {
            return Constants.PERM_VIEW;
        }
        return Constants.PERM_NONE;
    }

    // ==================== 详情 / 下载 / 预览 ====================

    @Override
    public FileVO getDetail(Long userId, Long fileId) {
        FileInfo file = requireFile(fileId);
        checkViewable(userId, file);
        return toVO(file, userId);
    }

    @Override
    public void download(Long userId, Long fileId, HttpServletResponse response) {
        FileInfo file = requireFile(fileId);
        checkViewable(userId, file);
        writeFileToResponse(file, response);
        increaseDownload(file);
    }

    @Override
    public String previewText(Long userId, Long fileId) {
        FileInfo file = requireFile(fileId);
        checkViewable(userId, file);
        if (!FileTypeUtil.isTextFile(file.getFilename())) {
            throw new BusinessException("该文件类型不支持文本预览");
        }
        File diskFile = diskFile(file);
        if (!diskFile.exists()) {
            throw new BusinessException("文件在服务器上不存在");
        }
        // 只读取前50KB,防止超大文本卡死
        try (FileInputStream in = new FileInputStream(diskFile)) {
            byte[] buffer = new byte[50 * 1024];
            int len = in.read(buffer);
            if (len <= 0) {
                return "(空文件)";
            }
            return new String(buffer, 0, len, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("文本预览失败", e);
            throw new BusinessException("文本预览失败,文件可能为二进制格式");
        }
    }

    // ==================== 编辑操作 ====================

    @Override
    public FileVO rename(Long userId, Long fileId, String newName) {
        if (!StringUtils.hasText(newName) || newName.length() > Constants.FILENAME_MAX_LENGTH) {
            throw new BusinessException("文件名不能为空且不超过255字符");
        }
        FileInfo file = requireFile(fileId);
        checkEditable(userId, file);   // 核心规则: 无编辑权禁止改名
        String oldName = file.getFilename();   // 先保存旧名,保证审计日志准确记录"旧名->新名"
        file.setFilename(newName);
        // 扩展名改变时同步重识别文件类型(如 .txt 改名 .png)
        file.setFileType(FileTypeUtil.detectType(newName));
        fileMapper.updateById(file);
        auditService.log(userId, "重命名文件", "文件[" + oldName + "] 重命名为[" + newName + "]");
        return toVO(file, userId);
    }

    @Override
    public FileVO replaceContent(Long userId, Long fileId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要替换的文件内容");
        }
        // 安全: 替换内容同样受扩展名白名单约束
        FileTypeUtil.checkAllowedExtension(file.getOriginalFilename());
        FileInfo fileInfo = requireFile(fileId);
        checkEditable(userId, fileInfo);   // 核心规则: 拥有者或EDIT权限可替换内容
        // 文件物理上占用"拥有者"的存储空间,而非操作者。
        // 当 EDIT 授权用户(非拥有者)替换内容时,必须校验并调整拥有者的配额。
        Long ownerId = fileInfo.getUserId();
        User owner = userMapper.selectById(ownerId);
        if (owner == null) {
            throw new BusinessException("文件拥有者不存在,无法替换");
        }
        // 存储空间校验: 新文件大小 - 旧文件大小 = 空间增量(校验拥有者配额)
        long newSize = file.getSize();
        long oldSize = fileInfo.getSize() == null ? 0 : fileInfo.getSize();
        long used = owner.getUsedStorage() == null ? 0 : owner.getUsedStorage();
        long limit = owner.getStorageLimit() == null ? 0 : owner.getStorageLimit();
        long delta = newSize - oldSize;
        if (used + delta > limit) {
            throw new BusinessException("存储空间不足: 替换后超出拥有者空间上限,请联系拥有者清理空间");
        }

        // 覆盖写入原物理文件(保持原存储路径,分享/授权链接不变)
        File dest = diskFile(fileInfo);
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try {
            file.transferTo(dest);
        } catch (Exception e) {
            log.error("文件内容替换失败", e);
            throw new BusinessException("文件替换失败,请重试");
        }

        // 更新大小与类型(updateTime 由 MyBatis-Plus 自动填充),并同步存储空间
        fileInfo.setSize(newSize);
        fileInfo.setFileType(FileTypeUtil.detectType(file.getOriginalFilename()));
        fileMapper.updateById(fileInfo);
        changeOwnerStorage(ownerId, delta);   // 空间增减作用于拥有者

        auditService.log(userId, "替换文件内容", "文件[" + fileInfo.getFilename() + "] 内容已替换,大小 "
                + FileTypeUtil.formatSize(newSize));
        return toVO(fileInfo, userId);
    }

    @Override
    public FileVO setPublic(Long userId, Long fileId, Integer isPublic) {
        FileInfo file = requireFile(fileId);
        checkOwner(userId, file);      // 仅拥有者可设置公开/私有
        boolean toPublic = isPublic != null && isPublic == Constants.FILE_PUBLIC;
        file.setIsPublic(toPublic ? Constants.FILE_PUBLIC : Constants.FILE_PRIVATE);
        fileMapper.updateById(file);
        auditService.log(userId, toPublic ? "设为公开" : "设为私有",
                "文件[" + file.getFilename() + "] 已" + (toPublic ? "公开" : "取消公开"));
        return toVO(file, userId);
    }

    @Override
    public FileVO toggleFavorite(Long userId, Long fileId, Integer isFavorite) {
        FileInfo file = requireFile(fileId);
        checkOwner(userId, file);
        boolean favorite = isFavorite != null && isFavorite == Constants.FAVORITE_YES;
        file.setIsFavorite(favorite ? Constants.FAVORITE_YES : Constants.FAVORITE_NO);
        fileMapper.updateById(file);
        auditService.log(userId, favorite ? "收藏文件" : "取消收藏", "文件[" + file.getFilename() + "]");
        return toVO(file, userId);
    }

    // ==================== 删除 / 回收站 ====================

    @Override
    public void deleteFile(Long userId, Long fileId) {
        FileInfo file = requireFile(fileId);
        checkOwner(userId, file);
        // 移入回收站: 保留授权记录与空间占用,恢复后权限仍在
        file.setStatus(STATUS_TRASH);
        fileMapper.updateById(file);
        auditService.log(userId, "删除文件", "文件[" + file.getFilename() + "] 移入回收站");
    }

    @Override
    @Transactional
    public void batchDelete(Long userId, BatchDeleteDTO dto) {
        if (dto.getIds() == null || dto.getIds().isEmpty()) {
            throw new BusinessException("请至少选择一个文件");
        }
        int count = 0;
        // 只处理"自己拥有的正常文件", 他人文件自动跳过(防御性处理,不影响本次批量)
        for (Long id : dto.getIds()) {
            FileInfo file = fileMapper.selectById(id);
            if (file != null && file.getStatus() == STATUS_NORMAL && file.getUserId().equals(userId)) {
                file.setStatus(STATUS_TRASH);
                fileMapper.updateById(file);
                count++;
            }
        }
        if (count > 0) {
            auditService.log(userId, "批量删除文件", "共 " + count + " 个文件移入回收站");
        }
    }

    @Override
    public FileVO restoreFile(Long userId, Long fileId) {
        FileInfo file = requireTrashFile(fileId);
        checkOwner(userId, file);
        file.setStatus(STATUS_NORMAL);
        fileMapper.updateById(file);
        auditService.log(userId, "恢复文件", "文件[" + file.getFilename() + "] 从回收站恢复");
        return toVO(file, userId);
    }

    @Override
    @Transactional
    public void deleteForever(Long userId, Long fileId) {
        FileInfo file = requireTrashFile(fileId);
        checkOwner(userId, file);
        // 1. 物理删除磁盘文件
        File diskFile = diskFile(file);
        if (diskFile.exists() && diskFile.isFile()) {
            diskFile.delete();
        }
        // 2. 删除数据库记录
        fileMapper.deleteById(fileId);
        // 3. 清理授权记录
        permissionMapper.delete(
                new LambdaQueryWrapper<FilePermission>().eq(FilePermission::getFileId, fileId));
        // 4. 清理分享记录
        shareMapper.delete(new LambdaQueryWrapper<Share>().eq(Share::getFileId, fileId));
        // 5. 回收存储空间
        changeOwnerStorage(userId, -(file.getSize() == null ? 0 : file.getSize()));
        auditService.log(userId, "彻底删除文件", "文件[" + file.getFilename() + "] 已从服务器彻底删除");
    }

    // ==================== 权限管理 ====================

    @Override
    public void grantPermission(Long userId, Long fileId, GrantPermissionDTO dto) {
        FileInfo file = requireFile(fileId);
        checkOwner(userId, file);      // 仅拥有者可授权
        String perm = dto.getPermission();
        if (!Constants.PERM_VIEW.equals(perm) && !Constants.PERM_EDIT.equals(perm)) {
            throw new BusinessException("权限类型只能为 VIEW(查看) 或 EDIT(编辑)");
        }
        // 目标用户必须存在且不能是自己
        User target = userMapper.selectById(dto.getUserId());
        if (target == null) {
            throw new BusinessException("被授权用户不存在");
        }
        if (target.getId().equals(userId)) {
            throw new BusinessException("不能给自己授权");
        }
        // 已存在授权记录则更新权限,否则新增
        FilePermission exist = permissionMapper.selectOne(
                new LambdaQueryWrapper<FilePermission>()
                        .eq(FilePermission::getFileId, fileId)
                        .eq(FilePermission::getUserId, dto.getUserId()));
        if (exist != null) {
            exist.setPermission(perm);
            exist.setSource("MANUAL");   // 手动调整后归为手动授权
            permissionMapper.updateById(exist);
        } else {
            FilePermission fp = new FilePermission();
            fp.setFileId(fileId);
            fp.setUserId(dto.getUserId());
            fp.setPermission(perm);
            fp.setSource("MANUAL");      // 手动授权(非好友也可被授权)
            permissionMapper.insert(fp);
        }
        auditService.log(userId, "授权文件",
                "将文件[" + file.getFilename() + "]的[" + perm + "]权限授予用户[" + target.getUsername() + "]");
    }

    @Override
    @Transactional
    public int batchGrantPermission(Long userId, BatchGrantDTO dto) {
        // 批量中"非自己拥有的文件"自动跳过;
        // 若所选文件全部非本人所有,则视为无权操作,直接拒绝(403),避免静默"假成功"
        if (dto.getFileIds() == null || dto.getFileIds().isEmpty()) {
            throw new BusinessException("请至少选择一个文件");
        }
        int owned = 0;
        for (Long fileId : dto.getFileIds()) {
            FileInfo file = fileMapper.selectById(fileId);
            if (file != null && file.getStatus() == STATUS_NORMAL && file.getUserId().equals(userId)) {
                owned++;
            }
        }
        if (owned == 0) {
            throw new BusinessException(403, "无权批量授权:所选文件均非本人所有");
        }
        int count = 0;
        for (Long fileId : dto.getFileIds()) {
            FileInfo file = fileMapper.selectById(fileId);
            if (file == null || file.getStatus() != STATUS_NORMAL || !file.getUserId().equals(userId)) {
                continue;   // 他人文件/不存在/回收站 -> 跳过
            }
            GrantPermissionDTO single = new GrantPermissionDTO();
            single.setUserId(dto.getUserId());
            single.setPermission(dto.getPermission());
            grantPermission(userId, fileId, single);
            count++;
        }
        if (count > 0) {
            auditService.log(userId, "批量授权文件", "共授权 " + count + " 个文件给用户ID=" + dto.getUserId());
        }
        return count;
    }

    @Override
    @Transactional
    public List<ShareVO> batchShare(Long userId, List<Long> fileIds, Integer expireDays, String password) {
        // 批量中"非自己拥有的文件"自动跳过;
        // 若所选文件全部非本人所有,则视为无权操作,直接拒绝(403)
        if (fileIds == null || fileIds.isEmpty()) {
            throw new BusinessException("请至少选择一个文件");
        }
        int owned = 0;
        for (Long fileId : fileIds) {
            FileInfo file = fileMapper.selectById(fileId);
            if (file != null && file.getStatus() == STATUS_NORMAL && file.getUserId().equals(userId)) {
                owned++;
            }
        }
        if (owned == 0) {
            throw new BusinessException(403, "无权批量分享:所选文件均非本人所有");
        }
        List<ShareVO> result = new ArrayList<>();
        for (Long fileId : fileIds) {
            FileInfo file = fileMapper.selectById(fileId);
            if (file == null || file.getStatus() != STATUS_NORMAL || !file.getUserId().equals(userId)) {
                continue;   // 跳过非本人文件
            }
            result.add(createShare(userId, fileId, expireDays, password));
        }
        if (!result.isEmpty()) {
            auditService.log(userId, "批量分享文件", "共生成 " + result.size() + " 个分享链接");
        }
        return result;
    }

    @Override
    public List<PermissionVO> listPermissions(Long userId, Long fileId) {
        FileInfo file = requireFile(fileId);
        checkOwner(userId, file);
        List<FilePermission> perms = permissionMapper.selectList(
                new LambdaQueryWrapper<FilePermission>().eq(FilePermission::getFileId, fileId));
        List<PermissionVO> result = new ArrayList<>();
        for (FilePermission p : perms) {
            User u = userMapper.selectById(p.getUserId());
            PermissionVO vo = new PermissionVO();
            vo.setFileId(fileId);
            vo.setUserId(p.getUserId());
            vo.setUsername(u != null ? u.getUsername() : "未知");
            vo.setNickname(u != null ? u.getNickname() : "未知");
            vo.setPermission(p.getPermission());
            vo.setSource(p.getSource());
            vo.setCreateTime(p.getCreateTime() == null ? "" : p.getCreateTime().toString());
            result.add(vo);
        }
        return result;
    }

    @Override
    public void removePermission(Long userId, Long fileId, Long targetUserId) {
        FileInfo file = requireFile(fileId);
        checkOwner(userId, file);
        permissionMapper.delete(
                new LambdaQueryWrapper<FilePermission>()
                        .eq(FilePermission::getFileId, fileId)
                        .eq(FilePermission::getUserId, targetUserId));
        auditService.log(userId, "取消授权", "收回了文件[" + file.getFilename() + "]对用户(ID="
                + targetUserId + ")的授权");
    }

    // ==================== 分享链接 ====================

    @Override
    public ShareVO createShare(Long userId, Long fileId, Integer expireDays, String password) {
        FileInfo file = requireFile(fileId);
        checkOwner(userId, file);
        // 已存在且未过期的分享则直接返回,不重复创建;已过期则删除重建
        Share exist = shareMapper.selectOne(
                new LambdaQueryWrapper<Share>().eq(Share::getFileId, fileId));
        if (exist != null) {
            if (!isExpired(exist)) {
                return toShareVO(exist, file);
            }
            shareMapper.deleteById(exist.getId());
        }
        Share share = new Share();
        share.setFileId(fileId);
        share.setToken(UUID.randomUUID().toString().replace("-", ""));
        share.setCreateUserId(userId);
        // 访问密码: 选填,存MD5摘要(分享密码由分享者自行设置,泄露面小)
        if (StringUtils.hasText(password)) {
            // 密码长度强校验,与前端弹窗提示"4-16位"保持一致
            if (password.length() < 4 || password.length() > 16) {
                throw new BusinessException("分享访问密码长度须为4-16位");
            }
            share.setPassword(PasswordUtil.md5(password));
        }
        // 有效期: 为空或<=0 表示永久有效
        if (expireDays != null && expireDays > 0) {
            share.setExpiresAt(LocalDateTime.now().plusDays(expireDays));
        }
        shareMapper.insert(share);
        auditService.log(userId, "创建分享链接", "为文件[" + file.getFilename() + "]创建了分享链接"
                + (share.getExpiresAt() != null ? ",有效期至" + share.getExpiresAt().toLocalDate() : ",永久有效"));
        return toShareVO(share, file);
    }

    @Override
    public List<ShareVO> listShares(Long userId, Long fileId) {
        FileInfo file = requireFile(fileId);
        checkOwner(userId, file);
        List<Share> shares = shareMapper.selectList(
                new LambdaQueryWrapper<Share>().eq(Share::getFileId, fileId));
        return shares.stream().map(s -> toShareVO(s, file)).collect(Collectors.toList());
    }

    @Override
    public void deleteShare(Long userId, Long fileId) {
        FileInfo file = requireFile(fileId);
        checkOwner(userId, file);
        shareMapper.delete(new LambdaQueryWrapper<Share>().eq(Share::getFileId, fileId));
        auditService.log(userId, "取消分享链接", "已删除文件[" + file.getFilename() + "]的分享链接");
    }

    @Override
    public FileVO getShareFile(String token, String password) {
        Share share = requireShare(token);
        checkShareAccess(share, password);
        FileInfo file = fileMapper.selectById(share.getFileId());
        if (file == null || file.getStatus() != STATUS_NORMAL) {
            throw new BusinessException(404, "分享的文件已被删除");
        }
        // 免登录访问: 权限记为 VIEW(只读)
        FileVO vo = toVO(file, file.getUserId());
        vo.setMyPermission("VIEW");
        return vo;
    }

    @Override
    public void downloadShare(String token, String password, HttpServletResponse response) {
        Share share = requireShare(token);
        checkShareAccess(share, password);
        FileInfo file = fileMapper.selectById(share.getFileId());
        if (file == null || file.getStatus() != STATUS_NORMAL) {
            throw new BusinessException(404, "分享的文件已被删除");
        }
        writeFileToResponse(file, response);
        increaseDownload(file);
    }

    /** 按token查询分享记录,不存在则404 */
    private Share requireShare(String token) {
        Share share = shareMapper.selectOne(
                new LambdaQueryWrapper<Share>().eq(Share::getToken, token));
        if (share == null) {
            throw new BusinessException(404, "分享链接不存在或已失效");
        }
        return share;
    }

    /** 分享是否已过期 */
    private boolean isExpired(Share share) {
        return share.getExpiresAt() != null
                && LocalDateTime.now().isAfter(share.getExpiresAt());
    }

    /** 校验分享访问条件: 有效期 + 访问密码(免登录场景的后端强制校验) */
    private void checkShareAccess(Share share, String password) {
        if (isExpired(share)) {
            throw new BusinessException(403, "分享链接已过期,请联系分享者重新生成");
        }
        if (StringUtils.hasText(share.getPassword())) {
            String inputMd5 = PasswordUtil.md5(password == null ? "" : password);
            if (!share.getPassword().equalsIgnoreCase(inputMd5)) {
                throw new BusinessException(403, "访问密码错误");
            }
        }
    }

    /** Share实体转VO,生成完整访问链接(前端地址可在配置中修改,适配部署环境) */
    private ShareVO toShareVO(Share share, FileInfo file) {
        ShareVO vo = new ShareVO();
        vo.setId(share.getId());
        vo.setFileId(share.getFileId());
        vo.setFilename(file.getFilename());
        vo.setToken(share.getToken());
        // 分享页由前端提供, /share/{token} 是前端路由
        vo.setUrl(frontendUrl + "/share/" + share.getToken());
        vo.setHasPassword(StringUtils.hasText(share.getPassword()));
        vo.setExpiresAt(share.getExpiresAt());
        vo.setExpired(isExpired(share));
        vo.setCreateTime(share.getCreateTime());
        return vo;
    }
}
