package com.example.farm_management_system.controller;

import com.example.farm_management_system.model.LoginRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@RestController
public class RegisterController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> doRegister(@RequestBody LoginRequest registerRequest) {
        String username = registerRequest.getUsername();
        String password = registerRequest.getPassword();

        // 1. 检查用户名是否存在
        String checkSql = "SELECT COUNT(*) FROM users WHERE username = ?";
        Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, username);

        if (count != null && count > 0) {
            return new ResponseEntity<>(Collections.singletonMap("message", "注册失败：用户名已存在"), HttpStatus.CONFLICT);
        }

        // 2. 插入新用户
        String insertSql = "INSERT INTO users (username, password) VALUES (?, ?)";
        try {
            int rows = jdbcTemplate.update(insertSql, username, password);
            if (rows > 0) {
                return ResponseEntity.ok(Collections.singletonMap("success", true));
            } else {
                return new ResponseEntity<>(Collections.singletonMap("message", "注册失败：无法创建用户"), HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(Collections.singletonMap("message", "系统错误"), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}