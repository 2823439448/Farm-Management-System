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

@RestController // 使用 RestController 代替 @Controller
public class LoginController {

    private static final String JDBC_URL = "jdbc:mysql://localhost:3306/farm_manager?useSSL=false&serverTimezone=UTC";
    private static final String JDBC_USER = "root";
    private static final String JDBC_PASSWORD = "19416135";

    @PostMapping("/login")
    // 返回 ResponseEntity<Map<String, Object>> 确保返回 JSON 格式
    public ResponseEntity<Map<String, Object>> doLogin(@RequestBody LoginRequest loginRequest,
                                                       HttpSession session) {

        String username = loginRequest.getUsername();
        String password = loginRequest.getPassword();

        String sql = "SELECT id FROM users WHERE username = ? AND password = ?";

        try (Connection conn = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            ps.setString(2, password);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                // ✅ 登录成功：返回包含 userId 的 JSON 响应
                int userId = rs.getInt("id");
                session.setAttribute("userId", userId);

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("userId", userId);

                // 返回 HTTP 200 OK 响应和 JSON 数据
                return ResponseEntity.ok(response);

            } else {
                // 登录失败：返回 JSON 错误信息和 HTTP 401 Unauthorized
                Map<String, Object> error = Collections.singletonMap("message", "用户名或密码错误");
                return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
            }

        } catch (SQLException e) {
            e.printStackTrace();
            // 系统错误：返回 JSON 错误信息和 HTTP 500 Internal Server Error
            Map<String, Object> error = Collections.singletonMap("message", "系统错误，请稍后再试");
            return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}