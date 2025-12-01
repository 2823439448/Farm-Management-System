package com.example.farm_management_system.LoginController;// LoginController.java (最终修正版本)

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody; // 确保这里是 RequestBody
// 导入 LoginRequest 类 (如果它不在同一个包中)
import com.example.farm_management_system.LoginRequest.LoginRequest;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;
import java.sql.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestController
public class LoginController {

    private static final String JDBC_URL = "jdbc:mysql://localhost:3306/farm_manager?useSSL=false&serverTimezone=UTC";
    private static final String JDBC_USER = "root";
    private static final String JDBC_PASSWORD = "19416135";

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> doLogin(@RequestBody LoginRequest loginRequest,
                                                       HttpSession session) {

        String username = loginRequest.getUsername();
        String password = loginRequest.getPassword();

        // ✅ 修正点 1: SQL 查询 user_id
        String sql = "SELECT user_id FROM users WHERE username = ? AND password = ?";

        try (Connection conn = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            ps.setString(2, password);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                // ✅ 修正点 2: 从结果集中获取 user_id
                int userId = rs.getInt("user_id");
                session.setAttribute("userId", userId);

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("userId", userId);
                return ResponseEntity.ok(response);

            } else {
                Map<String, Object> error = Collections.singletonMap("message", "用户名或密码错误");
                return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
            }

        } catch (SQLException e) {
            e.printStackTrace();
            Map<String, Object> error = Collections.singletonMap("message", "系统错误，请稍后再试");
            return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}