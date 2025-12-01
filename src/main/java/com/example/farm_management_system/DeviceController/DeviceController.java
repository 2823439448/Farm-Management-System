package com.example.farm_management_system.DeviceController;

import com.example.farm_management_system.LoginRequest.LoginRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*; // ⚠️ 新增导入 @GetMapping 和 @RequestParam

import javax.servlet.http.HttpSession;
import java.sql.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestController
public class DeviceController {

    // 数据库配置，与 LoginController 保持一致
    private static final String JDBC_URL = "jdbc:mysql://localhost:3306/farm_manager?useSSL=false&serverTimezone=UTC";
    private static final String JDBC_USER = "root";
    private static final String JDBC_PASSWORD = "19416135";

    /**
     * 根据设备唯一ID (device_unique_id) 注册或修改设备信息
     * 要求：用户必须登录 (session 中有 userId)
     * 使用 LoginRequest 接收数据：username -> device_unique_id, password -> device_name
     */
    @PostMapping("/device/save")
    public ResponseEntity<Map<String, Object>> saveDevice(@RequestBody LoginRequest deviceRequest,
                                                          HttpSession session) {

        // 1. 检查登录状态 (必须先登录)
        Integer userId = (Integer) session.getAttribute("userId");
        if (userId == null) {
            Map<String, Object> error = Collections.singletonMap("message", "未登录：请先登录后才能操作设备");
            return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
        }

        // ⚠️ 字段映射：使用 LoginRequest 的字段来接收设备数据
        String deviceUniqueId = deviceRequest.getUsername();
        String deviceName = deviceRequest.getPassword();

        // 2. 检查数据合法性
        if (deviceUniqueId == null || deviceUniqueId.trim().isEmpty() ||
                deviceName == null || deviceName.trim().isEmpty()) {
            Map<String, Object> error = Collections.singletonMap("message", "设备唯一ID和设备名称不能为空");
            return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
        }

        // 3. 检查设备是否存在，以决定是 INSERT 还是 UPDATE
        boolean exists = isDeviceExists(deviceUniqueId);
        String sql;

        if (exists) {
            // UPDATE: 修改现有设备 (要求 user_id 匹配，只能修改设备名称)
            sql = "UPDATE devices SET device_name = ? WHERE device_unique_id = ? AND user_id = ?";
        } else {
            // INSERT: 注册新设备
            sql = "INSERT INTO devices (user_id, device_unique_id, device_name) VALUES (?, ?, ?)";
        }

        try (Connection conn = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            if (exists) {
                // UPDATE 语句的参数设置: device_name, device_unique_id, user_id
                ps.setString(1, deviceName);
                ps.setString(2, deviceUniqueId);
                ps.setInt(3, userId);
            } else {
                // INSERT 语句的参数设置: user_id, device_unique_id, device_name
                ps.setInt(1, userId);
                ps.setString(2, deviceUniqueId);
                ps.setString(3, deviceName);
            }

            int rowsAffected = ps.executeUpdate();

            if (rowsAffected > 0) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("action", exists ? "update" : "register");
                response.put("message", exists ? "设备信息修改成功" : "设备注册成功");
                return ResponseEntity.ok(response);
            } else {
                // 注册失败/修改失败 (可能是 UPDATE 时用户ID不匹配)
                String failMessage = exists ? "设备修改失败，您可能不是该设备所有者" : "设备注册失败，该设备ID可能已被注册";
                Map<String, Object> error = Collections.singletonMap("message", failMessage);
                return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
            }

        } catch (SQLException e) {
            e.printStackTrace();
            Map<String, Object> error = Collections.singletonMap("message", "系统错误，无法处理设备操作");
            return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 辅助方法：检查设备是否已存在 (仅检查是否存在，不检查归属)
     */
    private boolean isDeviceExists(String deviceUniqueId) {
        String sql = "SELECT id FROM devices WHERE device_unique_id = ?";
        try (Connection conn = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, deviceUniqueId);
            ResultSet rs = ps.executeQuery();
            return rs.next();

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // =========================================================================
    // ↓↓↓↓↓↓ 新增：登录后设备绑定状态检查接口 ↓↓↓↓↓↓
    // =========================================================================

    /**
     * 检查用户是否绑定了任何设备
     * 对应前端的 GET /api/checkDeviceBinding?userId=xxx 请求
     */
    @GetMapping("/api/checkDeviceBinding")
    public Map<String, Boolean> checkDeviceBinding(@RequestParam Integer userId) {

        boolean isBound = hasBoundDevices(userId);

        // 返回格式为 {"isBound": true/false}
        Map<String, Boolean> response = new HashMap<>();
        response.put("isBound", isBound);
        return response;
    }

    /**
     * 辅助方法：查询指定用户ID是否有绑定的设备
     */
    private boolean hasBoundDevices(Integer userId) {
        // 检查 devices 表中是否存在 user_id 匹配的记录
        String sql = "SELECT id FROM devices WHERE user_id = ?";
        try (Connection conn = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                // 如果能找到一条记录，则表示已绑定
                return rs.next();
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}