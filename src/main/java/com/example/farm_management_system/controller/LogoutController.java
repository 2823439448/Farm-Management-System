package com.example.farm_management_system.controller;

import com.example.farm_management_system.model.LoginRequest; // 引入用于设备操作的Model
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder; // ⭐️ 新增：引入密码编码器

import jakarta.servlet.http.HttpSession;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class LogoutController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ⭐️ 新增：引入密码编码器 Bean，用于注销时的密码验证
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 【新 API】获取用户绑定的设备列表
     */
    @GetMapping("/api/my-devices-for-logout")
    public ResponseEntity<List<Map<String, Object>>> getMyDevicesForLogout(HttpSession session) {
        Integer userId = (Integer) session.getAttribute("userId");
        if (userId == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        String sql = "SELECT device_unique_id, device_name FROM devices WHERE user_id = ?";
        try {
            List<Map<String, Object>> devices = jdbcTemplate.queryForList(sql, userId);
            return ResponseEntity.ok(devices);
        } catch (Exception e) {
            System.err.println("获取设备列表失败: " + e.getMessage());
            return new ResponseEntity<>(Collections.emptyList(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 【新 API】安全退出登录
     */
    @PostMapping("/api/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpSession session) {
        // 清除所有 Session 属性，安全退出
        session.invalidate();
        return ResponseEntity.ok(Collections.singletonMap("message", "退出成功"));
    }

    /**
     * 【新 API】删除指定设备及其所有数据
     */
    @Transactional
    @DeleteMapping("/api/delete-device/{deviceId}")
    public ResponseEntity<Map<String, Object>> deleteDevice(@PathVariable String deviceId, HttpSession session) {
        Integer userId = (Integer) session.getAttribute("userId");
        if (userId == null) {
            return new ResponseEntity<>(Collections.singletonMap("message", "未登录或登录过期"), HttpStatus.UNAUTHORIZED);
        }

        try {
            // 1. 删除该设备的所有历史数据（必须先删）
            String deleteSensorDataSql = "DELETE s FROM sensor_data s JOIN devices d ON s.device_id = d.device_unique_id WHERE d.user_id = ? AND d.device_unique_id = ?";
            int dataRows = jdbcTemplate.update(deleteSensorDataSql, userId, deviceId);

            // 2. 删除设备信息本身
            String deleteDeviceSql = "DELETE FROM devices WHERE user_id = ? AND device_unique_id = ?";
            int deviceRows = jdbcTemplate.update(deleteDeviceSql, userId, deviceId);

            if (deviceRows > 0) {
                // 如果删除的是当前活跃设备，则清除活跃设备ID
                if (deviceId.equals(session.getAttribute("activeDeviceId"))) {
                    session.removeAttribute("activeDeviceId");
                }
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "设备 [" + deviceId + "] 及其 " + dataRows + " 条数据已成功删除。");
                return ResponseEntity.ok(response);
            } else {
                return new ResponseEntity<>(Collections.singletonMap("message", "删除失败：设备ID不存在或不属于当前用户"), HttpStatus.NOT_FOUND);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(Collections.singletonMap("message", "系统错误，删除设备失败"), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 【新 API】注销用户账户（删除所有数据），增加密码验证
     * 极度危险操作，需要用户再次确认并验证密码。
     */
    @Transactional
    @DeleteMapping("/api/delete-user")
    public ResponseEntity<Map<String, Object>> deleteUser(@RequestBody Map<String, String> requestBody, HttpSession session) {
        Integer userId = (Integer) session.getAttribute("userId");
        String plainPassword = requestBody.get("password"); // 接收用户输入的明文密码

        if (userId == null) {
            return new ResponseEntity<>(Collections.singletonMap("message", "未登录或登录过期"), HttpStatus.UNAUTHORIZED);
        }

        if (plainPassword == null || plainPassword.isEmpty()) {
            return new ResponseEntity<>(Collections.singletonMap("message", "请输入密码进行确认"), HttpStatus.BAD_REQUEST);
        }

        // 1. 验证用户密码 (查询 username 和 password 字段)
        // 🚨 修正点：使用 user_id 列进行查询，以解决 Unknown column 'id' 错误
        String userCheckSql = "SELECT password, username FROM users WHERE user_id = ?";
        String hashedPassword;
        String username;
        try {
            Map<String, Object> userMap = jdbcTemplate.queryForMap(userCheckSql, userId);
            hashedPassword = (String) userMap.get("password");
            username = (String) userMap.get("username");
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(Collections.singletonMap("message", "用户不存在或数据库错误"), HttpStatus.INTERNAL_SERVER_ERROR);
        }

        // 密码验证
        if (!passwordEncoder.matches(plainPassword, hashedPassword)) {
            return new ResponseEntity<>(Collections.singletonMap("message", "注销失败：密码不正确"), HttpStatus.FORBIDDEN);
        }

        // 2. 密码验证通过，开始级联删除
        try {
            // 2.1. 删除该用户所有设备的历史数据
            String deleteSensorDataSql = "DELETE FROM sensor_data WHERE device_id IN (SELECT device_unique_id FROM devices WHERE user_id = ?)";
            jdbcTemplate.update(deleteSensorDataSql, userId);

            // 2.2. 删除该用户所有设备信息
            String deleteDeviceSql = "DELETE FROM devices WHERE user_id = ?";
            jdbcTemplate.update(deleteDeviceSql, userId);

            // 2.3. 删除用户账户本身
            // 🚨 修正点：使用 user_id 列进行删除，解决了之前的 SQL 语法错误
            String deleteUserSql = "DELETE FROM users WHERE user_id = ?";
            int userRows = jdbcTemplate.update(deleteUserSql, userId);

            // 3. 清理 Session
            session.invalidate();

            if (userRows > 0) {
                return ResponseEntity.ok(Collections.singletonMap("message", "用户账户 [" + username + "] 及其所有相关数据已成功注销。"));
            } else {
                return new ResponseEntity<>(Collections.singletonMap("message", "注销失败：用户账户不存在"), HttpStatus.NOT_FOUND);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(Collections.singletonMap("message", "系统错误，注销失败"), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}