package com.example.farm_management_system.controller;

import com.example.farm_management_system.model.LoginRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import javax.servlet.http.HttpServletRequest; // 引入 Request 用于重置 Session
import javax.servlet.http.HttpSession;
import java.sql.Timestamp;
import java.util.*;

@RestController
public class LoginController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // 定义安全策略常量
    private static final int MAX_FAILED_ATTEMPTS = 5; // 最大失败次数
    private static final long LOCK_TIME_DURATION = 15 * 60 * 1000; // 锁定时间：15分钟 (毫秒)

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> doLogin(@RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        // 使用 HttpServletRequest 也就是为了手动管理 Session 安全

        String username = loginRequest.getUsername();
        String plainPassword = loginRequest.getPassword();

        // 🛡️ 1. 查询用户及安全状态字段
        // 注意：必须查询 failed_attempts 和 locked_until
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

            // 如果用户不存在，返回模糊错误以防止用户名枚举
            if (users.isEmpty()) {
                return new ResponseEntity<>(Collections.singletonMap("message", "用户名或密码错误"), HttpStatus.UNAUTHORIZED);
            }

            Map<String, Object> user = users.get(0);
            int userId = (int) user.get("user_id");
            String hashedPassword = (String) user.get("hashedPassword");
            int failedAttempts = user.get("failedAttempts") != null ? (int) user.get("failedAttempts") : 0;
            Timestamp lockedUntil = (Timestamp) user.get("lockedUntil");

            // 🛡️ 2. 检查账户是否被锁定
            if (lockedUntil != null && lockedUntil.after(new Date())) {
                long remainingMinutes = (lockedUntil.getTime() - System.currentTimeMillis()) / 60000;
                return new ResponseEntity<>(Collections.singletonMap("message", "账户已锁定，请在 " + (remainingMinutes + 1) + " 分钟后重试"), HttpStatus.FORBIDDEN);
            }

            // 🛡️ 3. 验证密码
            if (passwordEncoder.matches(plainPassword, hashedPassword)) {
                // =============== 登录成功逻辑 ===============

                // A. 重置安全计数器 (解锁账户，清零失败次数)
                String resetSql = "UPDATE users SET failed_attempts = 0, locked_until = NULL, last_failed = NULL WHERE user_id = ?";
                jdbcTemplate.update(resetSql, userId);

                // B. 防会话固定攻击 (Session Fixation Protection)
                // 销毁旧 session，创建新 session
                HttpSession oldSession = request.getSession(false);
                if (oldSession != null) {
                    oldSession.invalidate();
                }
                HttpSession newSession = request.getSession(true);
                newSession.setAttribute("userId", userId);

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("userId", userId);
                return ResponseEntity.ok(response);

            } else {
                // =============== 登录失败逻辑 ===============

                int newAttempts = failedAttempts + 1;

                if (newAttempts >= MAX_FAILED_ATTEMPTS) {
                    // 超过最大尝试次数，锁定账户
                    Timestamp unlockTime = new Timestamp(System.currentTimeMillis() + LOCK_TIME_DURATION);
                    String lockSql = "UPDATE users SET failed_attempts = ?, locked_until = ?, last_failed = NOW() WHERE user_id = ?";
                    jdbcTemplate.update(lockSql, newAttempts, unlockTime, userId);

                    return new ResponseEntity<>(Collections.singletonMap("message", "密码错误次数过多，账户已锁定15分钟"), HttpStatus.FORBIDDEN);
                } else {
                    // 仅增加计数
                    String failSql = "UPDATE users SET failed_attempts = ?, last_failed = NOW() WHERE user_id = ?";
                    jdbcTemplate.update(failSql, newAttempts, userId);

                    // 返回通用错误信息
                    return new ResponseEntity<>(Collections.singletonMap("message", "用户名或密码错误"), HttpStatus.UNAUTHORIZED);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(Collections.singletonMap("message", "系统错误"), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}