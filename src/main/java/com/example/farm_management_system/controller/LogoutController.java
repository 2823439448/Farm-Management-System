package com.example.farm_management_system.controller;

import com.example.farm_management_system.model.LoginRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class LogoutController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 获取用户绑定的设备列表
     */
    @GetMapping("/api/my-devices-for-logout")
    public ResponseEntity<List<Map<String, Object>>> getMyDevicesForLogout(HttpSession session) {
        Integer userId = (Integer) session.getAttribute("userId");
        if (userId == null) return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);

        try {
            List<Map<String, Object>> devices = jdbcTemplate.queryForList(
                    "SELECT device_unique_id, device_name FROM devices WHERE user_id = ?", userId);
            return ResponseEntity.ok(devices);
        } catch (Exception e) {
            return new ResponseEntity<>(Collections.emptyList(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 安全退出登录
     */
    @PostMapping("/api/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok(Collections.singletonMap("message", "退出成功"));
    }

    /**
     * 删除指定设备及其所有数据
     */
    @Transactional
    @DeleteMapping("/api/delete-device/{deviceId}")
    public ResponseEntity<Map<String, Object>> deleteDevice(
            @PathVariable String deviceId, HttpSession session) {

        Integer userId = (Integer) session.getAttribute("userId");
        if (userId == null)
            return new ResponseEntity<>(Collections.singletonMap("message", "未登录或登录过期"), HttpStatus.UNAUTHORIZED);

        try {
            int dataRows = jdbcTemplate.update(
                    "DELETE s FROM sensor_data s JOIN devices d ON s.device_id = d.device_unique_id WHERE d.user_id = ? AND d.device_unique_id = ?",
                    userId, deviceId);

            int deviceRows = jdbcTemplate.update(
                    "DELETE FROM devices WHERE user_id = ? AND device_unique_id = ?", userId, deviceId);

            if (deviceRows > 0) {
                if (deviceId.equals(session.getAttribute("activeDeviceId")))
                    session.removeAttribute("activeDeviceId");

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "设备 [" + deviceId + "] 及其 " + dataRows + " 条数据已成功删除。");
                return ResponseEntity.ok(response);
            } else {
                return new ResponseEntity<>(Collections.singletonMap("message", "设备ID不存在或不属于当前用户"), HttpStatus.NOT_FOUND);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(Collections.singletonMap("message", "系统错误"), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 注销用户账户
     * 删除顺序：传感器数据 → 设备 → 网盘权限记录 → 网盘文件记录 → 物理目录 → 用户账户
     */
    @Transactional
    @DeleteMapping("/api/delete-user")
    public ResponseEntity<Map<String, Object>> deleteUser(
            @RequestBody Map<String, String> requestBody, HttpSession session) {

        Integer userId = (Integer) session.getAttribute("userId");
        String plainPassword = requestBody.get("password");

        if (userId == null)
            return new ResponseEntity<>(Collections.singletonMap("message", "未登录或登录过期"), HttpStatus.UNAUTHORIZED);
        if (plainPassword == null || plainPassword.isEmpty())
            return new ResponseEntity<>(Collections.singletonMap("message", "请输入密码进行确认"), HttpStatus.BAD_REQUEST);

        // 1. 验证密码，同时拿到 username（用于找物理目录）
        String hashedPassword, username;
        try {
            Map<String, Object> userMap = jdbcTemplate.queryForMap(
                    "SELECT password, username FROM users WHERE user_id = ?", userId);
            hashedPassword = (String) userMap.get("password");
            username = (String) userMap.get("username");
        } catch (Exception e) {
            return new ResponseEntity<>(Collections.singletonMap("message", "用户不存在"), HttpStatus.INTERNAL_SERVER_ERROR);
        }

        if (!passwordEncoder.matches(plainPassword, hashedPassword))
            return new ResponseEntity<>(Collections.singletonMap("message", "注销失败：密码不正确"), HttpStatus.FORBIDDEN);

        // 2. 级联删除数据库记录
        try {
            // 2.1 传感器数据
            jdbcTemplate.update(
                    "DELETE FROM sensor_data WHERE device_id IN (SELECT device_unique_id FROM devices WHERE user_id = ?)",
                    userId);

            // 2.2 设备
            jdbcTemplate.update("DELETE FROM devices WHERE user_id = ?", userId);

            // 2.3 网盘文件权限记录（该用户文件的权限、以及别人分享给该用户的权限）
            jdbcTemplate.update(
                    "DELETE FROM file_permissions WHERE file_id IN (SELECT file_id FROM user_files WHERE owner_id = ?)",
                    userId);
            jdbcTemplate.update("DELETE FROM file_permissions WHERE user_id = ?", userId);

            // 2.4 网盘文件记录（含文件夹）
            jdbcTemplate.update("DELETE FROM user_files WHERE owner_id = ?", userId);

            // 2.5 删除用户账户
            int userRows = jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", userId);

            // 3. 删除物理目录 /home/data/{username}/
            //    放在数据库操作之后、session invalidate 之前
            //    即使物理删除失败也不回滚数据库（文件孤儿可手动清理）
            String userDirPath = "/home/data/" + username;
            try {
                Path userDir = Paths.get(userDirPath);
                if (Files.exists(userDir)) {
                    deleteDirectoryRecursively(userDir);
                }
            } catch (IOException e) {
                // 物理删除失败只记录日志，不影响注销流程
                System.err.println("警告：物理目录删除失败 " + userDirPath + " - " + e.getMessage());
            }

            // 4. 清理 Session
            session.invalidate();

            if (userRows > 0) {
                return ResponseEntity.ok(Collections.singletonMap("message",
                        "账户 [" + username + "] 及所有数据已成功注销"));
            } else {
                return new ResponseEntity<>(Collections.singletonMap("message", "用户账户不存在"), HttpStatus.NOT_FOUND);
            }

        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(Collections.singletonMap("message", "系统错误：" + e.getMessage()),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 修改密码
     */
    @PostMapping("/api/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(
            @RequestBody Map<String, String> requestBody, HttpSession session) {

        Integer userId = (Integer) session.getAttribute("userId");
        if (userId == null)
            return new ResponseEntity<>(Collections.singletonMap("message", "未登录或登录过期"), HttpStatus.UNAUTHORIZED);

        String oldPassword = requestBody.get("oldPassword");
        String newPassword = requestBody.get("newPassword");

        if (oldPassword == null || newPassword == null || newPassword.length() < 6)
            return new ResponseEntity<>(Collections.singletonMap("message", "参数不合法"), HttpStatus.BAD_REQUEST);

        try {
            String hashedPassword = (String) jdbcTemplate.queryForMap(
                    "SELECT password FROM users WHERE user_id = ?", userId).get("password");

            if (!passwordEncoder.matches(oldPassword, hashedPassword))
                return new ResponseEntity<>(Collections.singletonMap("message", "当前密码不正确"), HttpStatus.FORBIDDEN);

            String newHashed = passwordEncoder.encode(newPassword);
            jdbcTemplate.update("UPDATE users SET password = ? WHERE user_id = ?", newHashed, userId);

            return ResponseEntity.ok(Collections.singletonMap("message", "密码修改成功"));

        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(Collections.singletonMap("message", "系统错误"), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 递归删除目录
     */
    private void deleteDirectoryRecursively(Path directory) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}