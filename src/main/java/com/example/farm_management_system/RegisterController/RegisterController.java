package com.example.farm_management_system.RegisterController;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
// 假设您有一个 LoginRequest 类，我们复用它来接收注册数据
import com.example.farm_management_system.LoginRequest.LoginRequest;

import java.sql.*;
import java.util.Collections;
import java.util.Map;

@RestController
public class RegisterController {

    // 数据库配置，与 LoginController 保持一致
    private static final String JDBC_URL = "jdbc:mysql://localhost:3306/farm_manager?useSSL=false&serverTimezone=UTC";
    private static final String JDBC_USER = "root";
    private static final String JDBC_PASSWORD = "19416135";

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> doRegister(@RequestBody LoginRequest registerRequest) {

        String username = registerRequest.getUsername();
        String password = registerRequest.getPassword();

        // ⚠️ 注意: 实际应用中，密码应该在存储前进行哈希(如 bcrypt)而不是明文存储！

        // 1. 检查用户名是否已存在
        if (isUsernameExists(username)) {
            Map<String, Object> error = Collections.singletonMap("message", "注册失败：用户名已存在");
            return new ResponseEntity<>(error, HttpStatus.CONFLICT); // 409 Conflict
        }

        // 2. 插入新用户
        String sql = "INSERT INTO users (username, password) VALUES (?, ?)";

        try (Connection conn = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            ps.setString(2, password);
            int rowsAffected = ps.executeUpdate();

            if (rowsAffected > 0) {
                // 注册成功
                Map<String, Object> response = Collections.singletonMap("success", true);
                return ResponseEntity.ok(response);

            } else {
                // 插入失败 (例如数据库操作问题)
                Map<String, Object> error = Collections.singletonMap("message", "注册失败：无法创建用户");
                return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
            }

        } catch (SQLException e) {
            e.printStackTrace();
            Map<String, Object> error = Collections.singletonMap("message", "系统错误，请稍后再试");
            return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 辅助方法：检查用户名是否已存在
     */
    private boolean isUsernameExists(String username) {
        String sql = "SELECT user_id FROM users WHERE username = ?";
        try (Connection conn = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();

            // 如果能找到结果，则表示用户已存在
            return rs.next();

        } catch (SQLException e) {
            e.printStackTrace();
            // 在检查用户名时发生数据库错误，可以选择记录日志并返回 true/false
            // 为了安全起见，通常返回 true，避免将数据库内部错误信息暴露给前端
            return true;
        }
    }
}