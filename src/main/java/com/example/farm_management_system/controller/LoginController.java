package com.example.farm_management_system.controller;

import com.example.farm_management_system.model.LoginRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder; // ⭐️ 引入 BCrypt

import javax.servlet.http.HttpSession;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class LoginController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ⭐️ 引入密码编码器 Bean
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> doLogin(@RequestBody LoginRequest loginRequest, HttpSession session) {
        String username = loginRequest.getUsername();
        String plainPassword = loginRequest.getPassword(); // 用户输入的明文密码

        // 1. 根据用户名查询用户的 ID 和 哈希密码
        String sql = "SELECT user_id, password FROM users WHERE username = ?";

        try {
            // 查询用户ID和存储的哈希密码
            List<Map<String, Object>> users = jdbcTemplate.query(sql, (rs, rowNum) -> {
                Map<String, Object> map = new HashMap<>();
                map.put("user_id", rs.getInt("user_id"));
                map.put("hashedPassword", rs.getString("password"));
                return map;
            }, username);

            if (!users.isEmpty()) {
                Map<String, Object> user = users.get(0);
                int userId = (int) user.get("user_id");
                String hashedPassword = (String) user.get("hashedPassword");

                // ⭐️ 2. 使用 BCrypt 比较明文密码和哈希密码
                if (passwordEncoder.matches(plainPassword, hashedPassword)) {
                    // 密码匹配，登录成功
                    session.setAttribute("userId", userId);

                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("userId", userId);
                    return ResponseEntity.ok(response);
                } else {
                    // 密码不匹配
                    return new ResponseEntity<>(Collections.singletonMap("message", "用户名或密码错误"), HttpStatus.UNAUTHORIZED);
                }
            } else {
                // 用户名不存在
                return new ResponseEntity<>(Collections.singletonMap("message", "用户名或密码错误"), HttpStatus.UNAUTHORIZED);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(Collections.singletonMap("message", "系统错误"), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}