package com.example.farm_management_system.controller;

import com.example.farm_management_system.service.FileService;
import com.example.farm_management_system.service.PermissionService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


import java.io.File;
import java.nio.file.*;
import java.util.*;

@RestController
@RequestMapping("/api/disk")
public class DiskController {
    @Autowired private FileService fileService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PermissionService permissionService;

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        return fileService.getDiskStats();
    }

    // 获取文件列表（支持目录和视图切换）
    @GetMapping("/list")
    public ResponseEntity<?> listFiles(
            HttpSession session,
            @RequestParam(required = false) Long parentId,
            @RequestParam(defaultValue = "all") String viewType) {

        Integer uid = (Integer) session.getAttribute("userId");
        if (uid == null) {
            return ResponseEntity.status(401).body("未登录");
        }

        try {
            List<Map<String, Object>> files = fileService.getFiles(uid, parentId, viewType);
            List<Map<String, Object>> breadcrumb = fileService.getBreadcrumb(parentId);

            Map<String, Object> result = new HashMap<>();
            result.put("files", files);
            result.put("breadcrumb", breadcrumb);
            result.put("currentParentId", parentId);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("获取文件列表失败：" + e.getMessage());
        }
    }

    // 上传文件
    @PostMapping("/upload")
    public ResponseEntity<String> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long parentId,
            HttpSession session) {

        Integer uid = (Integer) session.getAttribute("userId");
        String username = (String) session.getAttribute("username");

        if (uid == null || username == null) {
            return ResponseEntity.status(401).body("未登录");
        }

        try {
            fileService.saveFile(file, uid, username, parentId);
            return ResponseEntity.ok("上传成功");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("上传失败：" + e.getMessage());
        }
    }

    // 创建文件夹
    @PostMapping("/createFolder")
    public ResponseEntity<String> createFolder(
            @RequestBody Map<String, Object> request,
            HttpSession session) {

        Integer uid = (Integer) session.getAttribute("userId");
        String username = (String) session.getAttribute("username");

        if (uid == null || username == null) {
            return ResponseEntity.status(401).body("未登录");
        }

        try {
            String folderName = (String) request.get("folderName");
            Long parentId = request.get("parentId") != null ?
                    ((Number) request.get("parentId")).longValue() : null;

            fileService.createFolder(folderName, uid, username, parentId);
            return ResponseEntity.ok("文件夹创建成功");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("创建失败：" + e.getMessage());
        }
    }

    // 删除文件/文件夹
    @DeleteMapping("/delete/{fileId}")
    public ResponseEntity<String> delete(@PathVariable long fileId, HttpSession session) {
        Integer uid = (Integer) session.getAttribute("userId");
        if (uid == null) {
            return ResponseEntity.status(401).body("未登录");
        }

        // 检查权限：所有者或有写权限的用户可以删除
        var p = permissionService.check(uid, fileId);
        if (!(p == PermissionService.Permission.OWNER ||
                p == PermissionService.Permission.PUBLIC_WRITE ||
                p == PermissionService.Permission.SHARED_WRITE)) {
            return ResponseEntity.status(403).body("无权限删除此文件");
        }

        try {
            fileService.deleteFile(fileId, uid);
            return ResponseEntity.ok("删除成功");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("删除失败：" + e.getMessage());
        }
    }

    // 重命名文件/文件夹
    @PutMapping("/rename/{fileId}")
    public ResponseEntity<String> rename(
            @PathVariable long fileId,
            @RequestBody Map<String, String> request,
            HttpSession session) {

        Integer uid = (Integer) session.getAttribute("userId");
        if (uid == null) {
            return ResponseEntity.status(401).body("未登录");
        }

        // 检查权限：所有者或有写权限的用户可以重命名
        var p = permissionService.check(uid, fileId);
        if (!(p == PermissionService.Permission.OWNER ||
                p == PermissionService.Permission.PUBLIC_WRITE ||
                p == PermissionService.Permission.SHARED_WRITE)) {
            return ResponseEntity.status(403).body("无权限重命名此文件");
        }

        try {
            String newName = request.get("newName");
            fileService.renameFile(fileId, uid, newName);
            return ResponseEntity.ok("重命名成功");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("重命名失败：" + e.getMessage());
        }
    }

    // 覆盖上传（需要写权限）
    @PostMapping("/upload/{fileId}")
    public ResponseEntity<?> replace(
            @PathVariable long fileId,
            @RequestParam("file") MultipartFile file,
            HttpSession session) {

        Integer uid = (Integer) session.getAttribute("userId");
        if (uid == null) {
            return ResponseEntity.status(401).body("未登录");
        }

        var p = permissionService.check(uid, fileId);
        if (!(p == PermissionService.Permission.OWNER ||
                p == PermissionService.Permission.PUBLIC_WRITE ||
                p == PermissionService.Permission.SHARED_WRITE)) {
            return ResponseEntity.status(403).body("无修改权限");
        }

        try {
            Map<String, Object> f = jdbcTemplate.queryForMap(
                    "SELECT storage_path FROM user_files WHERE file_id=?", fileId);
            Path path = Paths.get((String) f.get("storage_path"));
            Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);
            jdbcTemplate.update(
                    "UPDATE user_files SET file_size=?, updated_at=NOW() WHERE file_id=?",
                    file.getSize(), fileId);
            return ResponseEntity.ok("上传新版本成功");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("上传失败：" + e.getMessage());
        }
    }

    // 下载文件
    @GetMapping("/download/{fileId}")
    public ResponseEntity<?> download(@PathVariable long fileId, HttpSession session) {
        Integer uid = (Integer) session.getAttribute("userId");

        try {
            // 检查权限
            var p = permissionService.check(uid, fileId);
            if (p == PermissionService.Permission.DENY) {
                return ResponseEntity.status(403)
                        .body("您没有权限下载此文件。请联系文件所有者获取权限。");
            }

            // 获取文件信息
            Map<String, Object> f = jdbcTemplate.queryForMap(
                    "SELECT filename, storage_path, is_directory FROM user_files WHERE file_id=?",
                    fileId);

            // 检查是否是文件夹 - 修复：处理 TINYINT(1) 可能返回 Boolean 或 Number
            Object isDirObj = f.get("is_directory");
            int isDirectory = (isDirObj instanceof Boolean) ? ((Boolean) isDirObj ? 1 : 0) : ((Number) isDirObj).intValue();
            if (isDirectory == 1) {
                return ResponseEntity.status(400).body("不能下载文件夹");
            }

            File file = new File((String) f.get("storage_path"));
            if (!file.exists()) {
                return ResponseEntity.status(404).body("文件不存在");
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + f.get("filename") + "\"")
                    .body(new FileSystemResource(file));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("下载失败：" + e.getMessage());
        }
    }

    // 设置文件权限（公开/私有）
    @PutMapping("/permission/{fileId}")
    public ResponseEntity<String> setPermission(
            @PathVariable long fileId,
            @RequestBody Map<String, Object> request,
            HttpSession session) {

        Integer uid = (Integer) session.getAttribute("userId");
        if (uid == null) {
            return ResponseEntity.status(401).body("未登录");
        }

        try {
            boolean isPublic = (boolean) request.get("isPublic");
            boolean canWrite = (boolean) request.get("canWrite");

            fileService.setPermission(fileId, uid, isPublic, canWrite);
            return ResponseEntity.ok("权限设置成功");
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("设置失败：" + e.getMessage());
        }
    }

    // 分享给指定用户
    @PostMapping("/share/{fileId}")
    public ResponseEntity<String> shareToUser(
            @PathVariable long fileId,
            @RequestBody Map<String, Object> request,
            HttpSession session) {

        Integer uid = (Integer) session.getAttribute("userId");
        if (uid == null) {
            return ResponseEntity.status(401).body("未登录");
        }

        try {
            int targetUserId = (int) request.get("targetUserId");
            boolean canRead = (boolean) request.get("canRead");
            boolean canWrite = (boolean) request.get("canWrite");

            fileService.shareToUser(fileId, uid, targetUserId, canRead, canWrite);
            return ResponseEntity.ok("分享成功");
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("分享失败：" + e.getMessage());
        }
    }

    // 取消分享
    @DeleteMapping("/share/{fileId}/{targetUserId}")
    public ResponseEntity<String> removeShare(
            @PathVariable long fileId,
            @PathVariable int targetUserId,
            HttpSession session) {

        Integer uid = (Integer) session.getAttribute("userId");
        if (uid == null) {
            return ResponseEntity.status(401).body("未登录");
        }

        try {
            fileService.removeShare(fileId, uid, targetUserId);
            return ResponseEntity.ok("取消分享成功");
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("取消失败：" + e.getMessage());
        }
    }

    // 获取所有用户（用于分享）
    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers(HttpSession session) {
        Integer uid = (Integer) session.getAttribute("userId");
        if (uid == null) {
            return ResponseEntity.status(401).body("未登录");
        }

        try {
            List<Map<String, Object>> users = fileService.getAllUsers(uid);
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("获取用户列表失败");
        }
    }

    // 获取文件分享列表
    @GetMapping("/shares/{fileId}")
    public ResponseEntity<?> getFileShares(@PathVariable long fileId, HttpSession session) {
        Integer uid = (Integer) session.getAttribute("userId");
        if (uid == null) {
            return ResponseEntity.status(401).body("未登录");
        }

        try {
            List<Map<String, Object>> shares = fileService.getFileShares(fileId, uid);
            return ResponseEntity.ok(shares);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("获取分享列表失败");
        }
    }

    // 退出登录
    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok("退出成功");
    }
}