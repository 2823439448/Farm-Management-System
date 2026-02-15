package com.example.farm_management_system.controller; // 建议使用你当前项目的包名

import com.example.farm_management_system.model.LoginRequest;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;


import java.sql.Timestamp;
import java.util.*;

@RestController
public class LoginController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private com.example.farm_management_system.service.FileService fileService; // 合并点：注入文件服务

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // 保持 Old 版定义的常量
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCK_TIME_DURATION = 15 * 60 * 1000;

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> doLogin(@RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        String username = loginRequest.getUsername();
        String plainPassword = loginRequest.getPassword();

        // 1. 查询用户及安全状态（保持 Old 版字段）
        String sql = "SELECT user_id, password, failed_attempts, locked_until FROM users WHERE username = ?";

        try {
            List<Map<String, Object>> users = jdbcTemplate.query(sql, (rs, rowNum) -> {
                Map<String, Object> map = new HashMap<>();
                map.put("user_id", rs.getInt("user_id"));
                map.put("hashedPassword", rs.getString("password"));
                map.put("failedAttempts", rs.getInt("failed_attempts"));
                map.put("lockedUntil", rs.getTimestamp("locked_until"));
                return map;
            }, username);

            if (users.isEmpty()) {
                return new ResponseEntity<>(Collections.singletonMap("message", "用户名或密码错误"), HttpStatus.UNAUTHORIZED);
            }

            Map<String, Object> user = users.get(0);
            int userId = (int) user.get("user_id");
            String hashedPassword = (String) user.get("hashedPassword");
            int failedAttempts = user.get("failedAttempts") != null ? (int) user.get("failedAttempts") : 0;
            Timestamp lockedUntil = (Timestamp) user.get("lockedUntil");

            // 2. 检查锁定状态（保持 Old 版详细提示逻辑）
            if (lockedUntil != null && lockedUntil.after(new Date())) {
                long remainingMinutes = (lockedUntil.getTime() - System.currentTimeMillis()) / 60000;
                return new ResponseEntity<>(Collections.singletonMap("message", "账户已锁定，请在 " + (remainingMinutes + 1) + " 分钟后重试"), HttpStatus.FORBIDDEN);
            }

            // 3. 验证密码
            if (passwordEncoder.matches(plainPassword, hashedPassword)) {
                // --- 登录成功逻辑 ---

                // A. 重置安全计数器 (包含 Old 版的 last_failed 清理)
                jdbcTemplate.update("UPDATE users SET failed_attempts = 0, locked_until = NULL, last_failed = NULL WHERE user_id = ?", userId);

                // B. Session 管理（防固定攻击）
                HttpSession oldSession = request.getSession(false);
                if (oldSession != null) oldSession.invalidate();
                HttpSession newSession = request.getSession(true);
                newSession.setAttribute("userId", userId);
                newSession.setAttribute("username", username); // 保持新版的 username 存储，方便后续使用

                // C. 合并新版逻辑：确保用户目录存在
                fileService.ensureUserDir(username);

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("userId", userId);
                return ResponseEntity.ok(response);

            } else {
                // --- 登录失败逻辑 (完全遵循 Old 版) ---
                int newAttempts = failedAttempts + 1;

                if (newAttempts >= MAX_FAILED_ATTEMPTS) {
                    Timestamp unlockTime = new Timestamp(System.currentTimeMillis() + LOCK_TIME_DURATION);
                    // 包含 last_failed = NOW()
                    jdbcTemplate.update("UPDATE users SET failed_attempts = ?, locked_until = ?, last_failed = NOW() WHERE user_id = ?",
                            newAttempts, unlockTime, userId);
                    return new ResponseEntity<>(Collections.singletonMap("message", "密码错误次数过多，账户已锁定15分钟"), HttpStatus.FORBIDDEN);
                } else {
                    jdbcTemplate.update("UPDATE users SET failed_attempts = ?, last_failed = NOW() WHERE user_id = ?", newAttempts, userId);
                    return new ResponseEntity<>(Collections.singletonMap("message", "用户名或密码错误"), HttpStatus.UNAUTHORIZED);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(Collections.singletonMap("message", "系统错误"), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}