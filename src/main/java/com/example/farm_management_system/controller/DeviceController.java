package com.example.farm_management_system.controller;

import com.example.farm_management_system.model.LoginRequest; // 注意这里的包名
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.*;

@RestController
public class DeviceController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 注册或修改设备
    @PostMapping("/device/save")
    public ResponseEntity<Map<String, Object>> saveDevice(@RequestBody LoginRequest deviceRequest, HttpSession session) {
        Integer userId = (Integer) session.getAttribute("userId");
        if (userId == null) {
            return new ResponseEntity<>(Collections.singletonMap("message", "未登录"), HttpStatus.UNAUTHORIZED);
        }

        // 复用 LoginRequest 字段
        String deviceUniqueId = deviceRequest.getUsername();
        String deviceName = deviceRequest.getPassword();

        if (deviceUniqueId == null || deviceUniqueId.trim().isEmpty() || deviceName == null || deviceName.trim().isEmpty()) {
            return new ResponseEntity<>(Collections.singletonMap("message", "ID和名称不能为空"), HttpStatus.BAD_REQUEST);
        }

        // 检查设备是否存在
        String checkSql = "SELECT COUNT(*) FROM devices WHERE device_unique_id = ?";
        Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, deviceUniqueId);
        boolean exists = (count != null && count > 0);

        try {
            int rows;
            if (exists) {
                // Update
                String updateSql = "UPDATE devices SET device_name = ? WHERE device_unique_id = ? AND user_id = ?";
                // 注意：如果设备 ID 存在但不是该用户创建的，rows=0
                rows = jdbcTemplate.update(updateSql, deviceName, deviceUniqueId, userId);
            } else {
                // Insert
                String insertSql = "INSERT INTO devices (user_id, device_unique_id, device_name) VALUES (?, ?, ?)";
                rows = jdbcTemplate.update(insertSql, userId, deviceUniqueId, deviceName);
            }

            if (rows > 0) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("action", exists ? "update" : "register");
                response.put("message", exists ? "设备信息修改成功" : "设备注册成功");
                return ResponseEntity.ok(response);
            } else {
                return new ResponseEntity<>(Collections.singletonMap("message", "操作失败，可能无权修改此设备"), HttpStatus.BAD_REQUEST);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(Collections.singletonMap("message", "系统错误"), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // 获取我的设备列表
    @GetMapping("/api/myDevices")
    public ResponseEntity<List<Map<String, String>>> getMyDevices(HttpSession session) {
        Integer userId = (Integer) session.getAttribute("userId");
        if (userId == null) return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);

        String sql = "SELECT device_unique_id, device_name FROM devices WHERE user_id = ?";
        List<Map<String, String>> list = jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, String> map = new HashMap<>();
            map.put("deviceId", rs.getString("device_unique_id"));
            map.put("deviceName", rs.getString("device_name"));
            return map;
        }, userId);

        return ResponseEntity.ok(list);
    }

    // ⭐️ 关键修复点：设置活跃设备，解决最新数据无数据的问题
    @PostMapping("/api/setActiveDevice")
    public ResponseEntity<Map<String, Object>> setActiveDevice(@RequestBody Map<String, String> requestBody, HttpSession session) {
        Integer userId = (Integer) session.getAttribute("userId");
        String activeDeviceId = requestBody.get("deviceId");

        if (userId == null) {
            return new ResponseEntity<>(Collections.singletonMap("message", "未登录"), HttpStatus.UNAUTHORIZED);
        }

        // 验证归属：确保该设备ID确实属于当前用户
        String sql = "SELECT COUNT(*) FROM devices WHERE device_unique_id = ? AND user_id = ?";

        try {
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, activeDeviceId, userId);

            if (count != null && count > 0) {
                // 成功，保存到 Session，这样 /api/my-device-data 就能拿到它了
                session.setAttribute("activeDeviceId", activeDeviceId);

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "当前活跃设备已设置: " + activeDeviceId);
                return ResponseEntity.ok(response);
            } else {
                return new ResponseEntity<>(Collections.singletonMap("message", "该设备不属于您或不存在"), HttpStatus.FORBIDDEN);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(Collections.singletonMap("message", "系统错误，无法验证设备"), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // ⭐️ 新增接口：用于主页自动设置第一个设备
    @GetMapping("/api/setDefaultActiveDevice")
    public ResponseEntity<Map<String, Object>> setDefaultActiveDevice(HttpSession session) {
        Integer userId = (Integer) session.getAttribute("userId");
        if (userId == null) {
            // 如果未登录，返回 401
            return new ResponseEntity<>(Collections.singletonMap("message", "未登录"), HttpStatus.UNAUTHORIZED);
        }

        // 1. 检查是否已经设置了活跃设备
        String activeDeviceId = (String) session.getAttribute("activeDeviceId");
        if (activeDeviceId != null && !activeDeviceId.trim().isEmpty()) {
            // 已经设置了，直接返回成功，不做任何操作
            return ResponseEntity.ok(Collections.singletonMap("message", "活跃设备已存在，无需设置"));
        }

        // 2. 如果没有，查询该用户拥有的第一个设备
        // ❗ 修正点：将 ORDER BY device_id 改为 ORDER BY id
        String sql = "SELECT device_unique_id FROM devices WHERE user_id = ? ORDER BY id ASC LIMIT 1";

        try {
            List<String> deviceIds = jdbcTemplate.queryForList(sql, String.class, userId);

            if (!deviceIds.isEmpty()) {
                String defaultDeviceId = deviceIds.get(0);
                session.setAttribute("activeDeviceId", defaultDeviceId); // 存入 Session

                return ResponseEntity.ok(Collections.singletonMap("message", "已成功设置默认活跃设备：" + defaultDeviceId));
            } else {
                return new ResponseEntity<>(Collections.singletonMap("message", "用户没有注册任何设备"), HttpStatus.NOT_FOUND);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(Collections.singletonMap("message", "系统错误，无法设置默认设备"), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}