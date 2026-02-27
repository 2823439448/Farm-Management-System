package com.example.farm_management_system.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.*;
import java.text.Collator;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FileService {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final String ROOT_PATH = "/home/data";

    public void ensureUserDir(String username) {
        File userDir = new File(ROOT_PATH, username);
        if (!userDir.exists()) userDir.mkdirs();
    }

    public Map<String, Object> getDiskStats() {
        File root = new File(ROOT_PATH);
        if (!root.exists()) root.mkdirs();
        long total = root.getTotalSpace();
        long used = total - root.getFreeSpace();
        return Map.of(
                "percent", (total > 0) ? (used * 100 / total) : 0,
                "used_gb", String.format("%.2f", used / 1073741824.0),
                "total_gb", String.format("%.2f", total / 1073741824.0)
        );
    }

    // 上传文件并写入数据库
    public long saveFile(MultipartFile file, int userId, String username, Long parentId) throws Exception {
        ensureUserDir(username);

        // 获取父目录路径
        String parentPath = ROOT_PATH + "/" + username;
        if (parentId != null && parentId > 0) {
            Map<String, Object> parent = jdbcTemplate.queryForMap(
                    "SELECT storage_path FROM user_files WHERE file_id=? AND owner_id=?",
                    parentId, userId
            );
            parentPath = (String) parent.get("storage_path");
        }

        Path path = Paths.get(parentPath, file.getOriginalFilename());
        Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);

        jdbcTemplate.update(
                "INSERT INTO user_files(owner_id,filename,storage_path,file_size,is_public,can_write,is_directory,parent_id,created_at,updated_at) VALUES(?,?,?,?,0,0,0,?,NOW(),NOW())",
                userId, file.getOriginalFilename(), path.toString(), file.getSize(), parentId);

        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    // 创建文件夹
    public long createFolder(String folderName, int userId, String username, Long parentId) throws Exception {
        ensureUserDir(username);

        // 获取父目录路径
        String parentPath = ROOT_PATH + "/" + username;
        if (parentId != null && parentId > 0) {
            Map<String, Object> parent = jdbcTemplate.queryForMap(
                    "SELECT storage_path FROM user_files WHERE file_id=? AND owner_id=?",
                    parentId, userId
            );
            parentPath = (String) parent.get("storage_path");
        }

        Path folderPath = Paths.get(parentPath, folderName);
        Files.createDirectories(folderPath);

        jdbcTemplate.update(
                "INSERT INTO user_files(owner_id,filename,storage_path,file_size,is_public,can_write,is_directory,parent_id,created_at,updated_at) VALUES(?,?,?,0,0,0,1,?,NOW(),NOW())",
                userId, folderName, folderPath.toString(), parentId);

        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    // 删除文件或文件夹（权限检查在 Controller 中完成）
    public void deleteFile(long fileId, int userId) throws Exception {
        Map<String, Object> file = jdbcTemplate.queryForMap(
                "SELECT storage_path, is_directory FROM user_files WHERE file_id=?",
                fileId
        );

        String storagePath = (String) file.get("storage_path");
        // 修复：处理 TINYINT(1) 可能返回 Boolean 或 Number
        Object isDirObj = file.get("is_directory");
        int isDirectory = (isDirObj instanceof Boolean) ? ((Boolean) isDirObj ? 1 : 0) : ((Number) isDirObj).intValue();

        File targetFile = new File(storagePath);

        if (isDirectory == 1) {
            // 递归删除子文件
            deleteChildFiles(fileId);
            // 删除物理文件夹
            deleteDirectory(targetFile);
        } else {
            // 删除物理文件
            if (targetFile.exists()) {
                targetFile.delete();
            }
        }

        // 删除数据库记录
        jdbcTemplate.update("DELETE FROM user_files WHERE file_id=?", fileId);
        jdbcTemplate.update("DELETE FROM file_permissions WHERE file_id=?", fileId);
    }

    private void deleteChildFiles(long parentId) {
        List<Map<String, Object>> children = jdbcTemplate.queryForList(
                "SELECT file_id, storage_path, is_directory FROM user_files WHERE parent_id=?",
                parentId
        );

        for (Map<String, Object> child : children) {
            long childId = ((Number) child.get("file_id")).longValue();
            // 修复：处理 TINYINT(1) 可能返回 Boolean 或 Number
            Object isDirObj = child.get("is_directory");
            int isDirectory = (isDirObj instanceof Boolean) ? ((Boolean) isDirObj ? 1 : 0) : ((Number) isDirObj).intValue();
            String storagePath = (String) child.get("storage_path");

            if (isDirectory == 1) {
                deleteChildFiles(childId);
                deleteDirectory(new File(storagePath));
            } else {
                new File(storagePath).delete();
            }

            jdbcTemplate.update("DELETE FROM user_files WHERE file_id=?", childId);
            jdbcTemplate.update("DELETE FROM file_permissions WHERE file_id=?", childId);
        }
    }

    private void deleteDirectory(File dir) {
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            dir.delete();
        }
    }

    // 重命名文件或文件夹（权限检查在 Controller 中完成）
    public void renameFile(long fileId, int userId, String newName) throws Exception {
        Map<String, Object> file = jdbcTemplate.queryForMap(
                "SELECT storage_path FROM user_files WHERE file_id=?",
                fileId
        );

        String oldPath = (String) file.get("storage_path");
        File oldFile = new File(oldPath);
        File newFile = new File(oldFile.getParent(), newName);

        if (oldFile.exists()) {
            oldFile.renameTo(newFile);
        }

        jdbcTemplate.update(
                "UPDATE user_files SET filename=?, storage_path=?, updated_at=NOW() WHERE file_id=?",
                newName, newFile.getAbsolutePath(), fileId
        );
    }

    // 获取文件列表（支持目录浏览）
    public List<Map<String, Object>> getFiles(int userId, Long parentId, String viewType) {
        String sql;
        List<Map<String, Object>> result;

        if ("mine".equals(viewType)) {
            sql = """
            SELECT f.file_id as id,
                   f.filename,
                   f.storage_path as file_path,
                   f.is_directory,
                   f.file_size,
                   f.is_public,
                   f.can_write,
                   u.username as owner_name,
                   f.created_at,
                   'mine' as source
            FROM user_files f
            JOIN users u ON f.owner_id=u.user_id
            WHERE f.owner_id=? AND IFNULL(f.parent_id,0)=?
            """;
            result = jdbcTemplate.queryForList(sql, userId, parentId == null ? 0 : parentId);

        } else if ("shared".equals(viewType)) {
            sql = """
            SELECT f.file_id as id,
                   f.filename,
                   f.storage_path as file_path,
                   f.is_directory,
                   f.file_size,
                   f.is_public,
                   f.can_write,
                   u.username as owner_name,
                   f.created_at,
                   CASE 
                       WHEN f.is_public=1 AND f.can_write=1 THEN 'public_write'
                       WHEN f.is_public=1 THEN 'public_read'
                       WHEN p.can_write=1 THEN 'shared_write'
                       ELSE 'shared_read'
                   END as source
            FROM user_files f
            JOIN users u ON f.owner_id=u.user_id
            LEFT JOIN file_permissions p ON p.file_id=f.file_id AND p.user_id=?
            WHERE f.owner_id!=? 
              AND (f.is_public=1 OR p.user_id IS NOT NULL)
              AND IFNULL(f.parent_id,0)=?
            """;
            result = jdbcTemplate.queryForList(sql, userId, userId, parentId == null ? 0 : parentId);

        } else {
            sql = """
            SELECT f.file_id as id,
                   f.filename,
                   f.storage_path as file_path,
                   f.is_directory,
                   f.file_size,
                   f.is_public,
                   f.can_write,
                   u.username as owner_name,
                   f.created_at,
                   CASE
                       WHEN f.owner_id=? THEN 'mine'
                       WHEN f.is_public=1 AND f.can_write=1 THEN 'public_write'
                       WHEN f.is_public=1 THEN 'public_read'
                       WHEN p.can_write=1 THEN 'shared_write'
                       WHEN p.can_read=1 THEN 'shared_read'
                       ELSE 'private'
                   END as source
            FROM user_files f
            JOIN users u ON f.owner_id=u.user_id
            LEFT JOIN file_permissions p ON p.file_id=f.file_id AND p.user_id=?
            WHERE (f.owner_id=? OR f.is_public=1 OR p.user_id IS NOT NULL)
              AND IFNULL(f.parent_id,0)=?
            """;
            result = jdbcTemplate.queryForList(sql, userId, userId, userId, parentId == null ? 0 : parentId);
        }

        // 用中文感知排序：文件夹在前，然后按拼音/字母顺序排
        Collator collator = Collator.getInstance(Locale.CHINESE);
        collator.setStrength(Collator.PRIMARY);
        result.sort((a, b) -> {
            Object aDirObj = a.get("is_directory");
            Object bDirObj = b.get("is_directory");
            int aDir = (aDirObj instanceof Boolean) ? ((Boolean) aDirObj ? 1 : 0) : ((Number) aDirObj).intValue();
            int bDir = (bDirObj instanceof Boolean) ? ((Boolean) bDirObj ? 1 : 0) : ((Number) bDirObj).intValue();
            if (aDir != bDir) return bDir - aDir; // 文件夹在前
            String aName = (String) a.get("filename");
            String bName = (String) b.get("filename");
            return collator.compare(aName == null ? "" : aName, bName == null ? "" : bName);
        });

        return result;
    }

    // 移动文件/文件夹到新的父目录
    public void moveFile(long fileId, Long targetParentId, int userId, String username) throws Exception {
        // 查询被移动的文件信息
        Map<String, Object> file = jdbcTemplate.queryForMap(
                "SELECT owner_id, filename, storage_path, is_directory FROM user_files WHERE file_id=?", fileId);

        int ownerId = ((Number) file.get("owner_id")).intValue();

        // 检查权限：所有者直接允许；非所有者需要有写权限
        if (ownerId != userId) {
            List<Map<String, Object>> perm = jdbcTemplate.queryForList(
                    "SELECT can_write FROM file_permissions WHERE file_id=? AND user_id=?", fileId, userId);
            boolean hasWrite = !perm.isEmpty() && ((Number) perm.get(0).get("can_write")).intValue() == 1;

            // 也检查 is_public + can_write
            Map<String, Object> fileInfo = jdbcTemplate.queryForMap(
                    "SELECT is_public, can_write FROM user_files WHERE file_id=?", fileId);
            Object isPubObj = fileInfo.get("is_public");
            Object canWrObj = fileInfo.get("can_write");
            int isPub = (isPubObj instanceof Boolean) ? ((Boolean) isPubObj ? 1 : 0) : ((Number) isPubObj).intValue();
            int canWr = (canWrObj instanceof Boolean) ? ((Boolean) canWrObj ? 1 : 0) : ((Number) canWrObj).intValue();
            boolean publicWrite = isPub == 1 && canWr == 1;

            if (!hasWrite && !publicWrite) {
                throw new SecurityException("无权限移动此文件");
            }
        }

        // 防止把文件夹移动到自身或子孙目录中
        Object isDirObj = file.get("is_directory");
        int isDirectory = (isDirObj instanceof Boolean) ? ((Boolean) isDirObj ? 1 : 0) : ((Number) isDirObj).intValue();
        if (isDirectory == 1 && targetParentId != null) {
            if (isDescendantOf(targetParentId, fileId)) {
                throw new IllegalArgumentException("不能将文件夹移动到其子目录中");
            }
        }

        // 计算目标物理路径
        String targetDirPath = ROOT_PATH + "/" + username;
        if (targetParentId != null && targetParentId > 0) {
            Map<String, Object> targetParent = jdbcTemplate.queryForMap(
                    "SELECT storage_path FROM user_files WHERE file_id=?", targetParentId);
            targetDirPath = (String) targetParent.get("storage_path");
        }

        String filename = (String) file.get("filename");
        String oldPath = (String) file.get("storage_path");
        Path source = Paths.get(oldPath);
        Path target = Paths.get(targetDirPath, filename);

        // 如果目标路径已存在同名文件，自动重命名
        if (Files.exists(target) && !source.equals(target)) {
            String baseName = filename.contains(".") && isDirectory == 0
                    ? filename.substring(0, filename.lastIndexOf('.'))
                    : filename;
            String ext = filename.contains(".") && isDirectory == 0
                    ? filename.substring(filename.lastIndexOf('.'))
                    : "";
            int i = 1;
            while (Files.exists(target)) {
                target = Paths.get(targetDirPath, baseName + "(" + i + ")" + ext);
                i++;
            }
        }

        // 物理移动
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);

        String newPath = target.toString();
        String newFilename = target.getFileName().toString();

        // 更新数据库：当前文件
        jdbcTemplate.update(
                "UPDATE user_files SET parent_id=?, storage_path=?, filename=?, updated_at=NOW() WHERE file_id=?",
                targetParentId, newPath, newFilename, fileId);

        // 如果是文件夹，递归更新所有子文件的 storage_path
        if (isDirectory == 1) {
            updateChildPaths(fileId, oldPath, newPath);
        }
    }

    // 递归更新子文件的 storage_path（文件夹移动后子文件路径前缀变了）
    private void updateChildPaths(long parentId, String oldParentPath, String newParentPath) {
        List<Map<String, Object>> children = jdbcTemplate.queryForList(
                "SELECT file_id, storage_path, is_directory FROM user_files WHERE parent_id=?", parentId);
        for (Map<String, Object> child : children) {
            long childId = ((Number) child.get("file_id")).longValue();
            String oldChildPath = (String) child.get("storage_path");
            String newChildPath = newParentPath + oldChildPath.substring(oldParentPath.length());
            Object isDirObj = child.get("is_directory");
            int isDir = (isDirObj instanceof Boolean) ? ((Boolean) isDirObj ? 1 : 0) : ((Number) isDirObj).intValue();
            jdbcTemplate.update("UPDATE user_files SET storage_path=?, updated_at=NOW() WHERE file_id=?",
                    newChildPath, childId);
            if (isDir == 1) {
                updateChildPaths(childId, oldChildPath, newChildPath);
            }
        }
    }

    // 判断 targetId 是否是 ancestorId 的子孙目录
    private boolean isDescendantOf(long targetId, long ancestorId) {
        List<Map<String, Object>> children = jdbcTemplate.queryForList(
                "SELECT file_id FROM user_files WHERE parent_id=?", ancestorId);
        for (Map<String, Object> child : children) {
            long childId = ((Number) child.get("file_id")).longValue();
            if (childId == targetId) return true;
            if (isDescendantOf(targetId, childId)) return true;
        }
        return false;
    }

    // 获取面包屑导航
    public List<Map<String, Object>> getBreadcrumb(Long fileId) {
        List<Map<String, Object>> breadcrumb = new ArrayList<>();

        if (fileId == null || fileId == 0) {
            return breadcrumb;
        }

        Long currentId = fileId;
        while (currentId != null && currentId > 0) {
            try {
                Map<String, Object> file = jdbcTemplate.queryForMap(
                        "SELECT file_id, filename, parent_id FROM user_files WHERE file_id=?",
                        currentId
                );

                Map<String, Object> crumb = new HashMap<>();
                crumb.put("id", file.get("file_id"));
                crumb.put("name", file.get("filename"));
                breadcrumb.add(0, crumb);

                Object parentId = file.get("parent_id");
                currentId = (parentId != null) ? ((Number) parentId).longValue() : null;
            } catch (Exception e) {
                break;
            }
        }

        return breadcrumb;
    }

    // 设置文件权限
    public void setPermission(long fileId, int userId, boolean isPublic, boolean canWrite) {
        Map<String, Object> file = jdbcTemplate.queryForMap(
                "SELECT owner_id FROM user_files WHERE file_id=?",
                fileId
        );

        int ownerId = (int) file.get("owner_id");
        if (ownerId != userId) {
            throw new SecurityException("只有文件所有者可以设置权限");
        }

        jdbcTemplate.update(
                "UPDATE user_files SET is_public=?, can_write=?, updated_at=NOW() WHERE file_id=?",
                isPublic ? 1 : 0, canWrite ? 1 : 0, fileId
        );
    }

    // 分享给指定用户
    public void shareToUser(long fileId, int ownerId, int targetUserId, boolean canRead, boolean canWrite) {
        Map<String, Object> file = jdbcTemplate.queryForMap(
                "SELECT owner_id FROM user_files WHERE file_id=?",
                fileId
        );

        int fileOwnerId = (int) file.get("owner_id");
        if (fileOwnerId != ownerId) {
            throw new SecurityException("只有文件所有者可以分享");
        }

        // 检查是否已存在权限记录
        List<Map<String, Object>> existing = jdbcTemplate.queryForList(
                "SELECT id FROM file_permissions WHERE file_id=? AND user_id=?",
                fileId, targetUserId
        );

        if (existing.isEmpty()) {
            jdbcTemplate.update(
                    "INSERT INTO file_permissions(file_id, user_id, can_read, can_write) VALUES(?,?,?,?)",
                    fileId, targetUserId, canRead ? 1 : 0, canWrite ? 1 : 0
            );
        } else {
            jdbcTemplate.update(
                    "UPDATE file_permissions SET can_read=?, can_write=? WHERE file_id=? AND user_id=?",
                    canRead ? 1 : 0, canWrite ? 1 : 0, fileId, targetUserId
            );
        }
    }

    // 取消分享
    public void removeShare(long fileId, int ownerId, int targetUserId) {
        Map<String, Object> file = jdbcTemplate.queryForMap(
                "SELECT owner_id FROM user_files WHERE file_id=?",
                fileId
        );

        int fileOwnerId = (int) file.get("owner_id");
        if (fileOwnerId != ownerId) {
            throw new SecurityException("只有文件所有者可以取消分享");
        }

        jdbcTemplate.update(
                "DELETE FROM file_permissions WHERE file_id=? AND user_id=?",
                fileId, targetUserId
        );
    }

    // 获取所有用户列表（用于分享）
    public List<Map<String, Object>> getAllUsers(int currentUserId) {
        return jdbcTemplate.queryForList(
                "SELECT user_id, username FROM users WHERE user_id!=?",
                currentUserId
        );
    }

    // 获取文件的分享列表
    public List<Map<String, Object>> getFileShares(long fileId, int ownerId) {
        Map<String, Object> file = jdbcTemplate.queryForMap(
                "SELECT owner_id FROM user_files WHERE file_id=?",
                fileId
        );

        int fileOwnerId = (int) file.get("owner_id");
        if (fileOwnerId != ownerId) {
            throw new SecurityException("只有文件所有者可以查看分享列表");
        }

        return jdbcTemplate.queryForList(
                """
                SELECT u.user_id, u.username, p.can_read, p.can_write
                FROM file_permissions p
                JOIN users u ON p.user_id = u.user_id
                WHERE p.file_id=?
                """,
                fileId
        );
    }
}