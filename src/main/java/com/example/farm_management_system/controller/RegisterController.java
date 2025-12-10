package com.example.farm_management_system.controller;

import com.example.farm_management_system.model.LoginRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@RestController
public class RegisterController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> doRegister(@RequestBody LoginRequest registerRequest) {
        String username = registerRequest.getUsername();
        String plainPassword = registerRequest.getPassword();

        // 🛡️ 安全改进 1：输入参数校验 (防止恶意长字符串或空值)
        if (username == null || username.trim().isEmpty() || plainPassword == null || plainPassword.trim().isEmpty()) {
            return new ResponseEntity<>(Collections.singletonMap("message", "用户名或密码不能为空"), HttpStatus.BAD_REQUEST);
        }
        if (username.length() < 3 || username.length() > 20) {
            return new ResponseEntity<>(Collections.singletonMap("message", "用户名长度需在3-20个字符之间"), HttpStatus.BAD_REQUEST);
        }
        // 只允许字母、数字、下划线，防止 SQL 注入边缘情况或 XSS
        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            return new ResponseEntity<>(Collections.singletonMap("message", "用户名包含非法字符"), HttpStatus.BAD_REQUEST);
        }
        if (plainPassword.length() < 6) {
            return new ResponseEntity<>(Collections.singletonMap("message", "密码长度至少需6位"), HttpStatus.BAD_REQUEST);
        }

        // 2. 密码加密
        String hashedPassword = passwordEncoder.encode(plainPassword);

        // 3. 插入用户
        // 🛡️ 安全改进 2：利用数据库唯一索引防止并发注册冲突 (TOCTOU)
        // 确保数据库 users 表的 username 字段设置了 UNIQUE 约束
        String insertSql = "INSERT INTO users (username, password, failed_attempts) VALUES (?, ?, 0)";

        try {
            int rows = jdbcTemplate.update(insertSql, username, hashedPassword);
            if (rows > 0) {
                return ResponseEntity.ok(Collections.singletonMap("success", true));
            } else {
                return new ResponseEntity<>(Collections.singletonMap("message", "注册失败"), HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } catch (DuplicateKeyException e) {
            // 捕获主键冲突异常
            return new ResponseEntity<>(Collections.singletonMap("message", "注册失败：用户名已存在"), HttpStatus.CONFLICT);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(Collections.singletonMap("message", "系统错误"), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}