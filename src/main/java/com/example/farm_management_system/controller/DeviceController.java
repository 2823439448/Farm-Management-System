package com.example.farm_management_system.controller;

import com.example.farm_management_system.model.LoginRequest;
import com.example.farm_management_system.service.MQTTController;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.json.JSONObject;
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

    @Autowired
    private MQTTController mqttController;

    // 注册或修改设备
    @PostMapping("/device/save")
    public ResponseEntity<Map<String, Object>> saveDevice(@RequestBody LoginRequest deviceRequest, HttpSession session) {
        Integer userId = (Integer) session.getAttribute("userId");
        if (userId == null) {
            return new ResponseEntity<>(Collections.singletonMap("message", "未登录"), HttpStatus.UNAUTHORIZED);
        }

        String deviceUniqueId = deviceRequest.getUsername();
        String deviceName = deviceRequest.getPassword();

        if (deviceUniqueId == null || deviceUniqueId.trim().isEmpty() || deviceName == null || deviceName.trim().isEmpty()) {
            return new ResponseEntity<>(Collections.singletonMap("message", "ID和名称不能为空"), HttpStatus.BAD_REQUEST);
        }

        try {
            // 检查设备是否存在
            String checkSql = "SELECT COUNT(*) FROM devices WHERE device_unique_id = ?";
            Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, deviceUniqueId);
            boolean exists = (count != null && count > 0);

            int rows;
            if (exists) {
                // Update: 仅允许修改属于当前用户的设备
                String updateSql = "UPDATE devices SET device_name = ? WHERE device_unique_id = ? AND user_id = ?";
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
                // 🛡️ 模糊化错误信息，防止猜测设备归属
                return new ResponseEntity<>(Collections.singletonMap("message", "操作失败：设备可能已存在且不属于您"), HttpStatus.BAD_REQUEST);
            }
        } catch (Exception e) {
            // 🛡️ 安全日志：只在后台打印，不返回给前端
            System.err.println("设备保存失败: " + e.getMessage());
            return new ResponseEntity<>(Collections.singletonMap("message", "系统内部错误"), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/api/myDevices")
    public ResponseEntity<List<Map<String, String>>> getMyDevices(HttpSession session) {
        Integer userId = (Integer) session.getAttribute("userId");
        if (userId == null) return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);

        String sql = "SELECT device_unique_id, device_name FROM devices WHERE user_id = ?";
        // 🛡️ 即使查询出错，也应捕获异常
        try {
            List<Map<String, String>> list = jdbcTemplate.query(sql, (rs, rowNum) -> {
                Map<String, String> map = new HashMap<>();
                map.put("deviceId", rs.getString("device_unique_id"));
                map.put("deviceName", rs.getString("device_name"));
                return map;
            }, userId);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            System.err.println("获取设备列表失败: " + e.getMessage());
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/api/setActiveDevice")
    public ResponseEntity<Map<String, Object>> setActiveDevice(@RequestBody Map<String, String> requestBody, HttpSession session) {
        Integer userId = (Integer) session.getAttribute("userId");
        String activeDeviceId = requestBody.get("deviceId");

        if (userId == null) {
            return new ResponseEntity<>(Collections.singletonMap("message", "未登录"), HttpStatus.UNAUTHORIZED);
        }

        String sql = "SELECT COUNT(*) FROM devices WHERE device_unique_id = ? AND user_id = ?";

        try {
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, activeDeviceId, userId);

            if (count != null && count > 0) {
                session.setAttribute("activeDeviceId", activeDeviceId);
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "当前活跃设备已设置: " + activeDeviceId);
                return ResponseEntity.ok(response);
            } else {
                return new ResponseEntity<>(Collections.singletonMap("message", "该设备不属于您或不存在"), HttpStatus.FORBIDDEN);
            }
        } catch (Exception e) {
            System.err.println("设置活跃设备失败: " + e.getMessage());
            return new ResponseEntity<>(Collections.singletonMap("message", "系统错误"), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/api/setDefaultActiveDevice")
    public ResponseEntity<Map<String, Object>> setDefaultActiveDevice(HttpSession session) {
        Integer userId = (Integer) session.getAttribute("userId");
        if (userId == null) {
            return new ResponseEntity<>(Collections.singletonMap("message", "未登录"), HttpStatus.UNAUTHORIZED);
        }

        String activeDeviceId = (String) session.getAttribute("activeDeviceId");
        if (activeDeviceId != null && !activeDeviceId.trim().isEmpty()) {
            return ResponseEntity.ok(Collections.singletonMap("message", "活跃设备已存在，无需设置"));
        }

        String sql = "SELECT device_unique_id FROM devices WHERE user_id = ? ORDER BY id ASC LIMIT 1";

        try {
            List<String> deviceIds = jdbcTemplate.queryForList(sql, String.class, userId);

            if (!deviceIds.isEmpty()) {
                String defaultDeviceId = deviceIds.get(0);
                session.setAttribute("activeDeviceId", defaultDeviceId);
                return ResponseEntity.ok(Collections.singletonMap("message", "已成功设置默认活跃设备：" + defaultDeviceId));
            } else {
                return new ResponseEntity<>(Collections.singletonMap("message", "用户没有注册任何设备"), HttpStatus.NOT_FOUND);
            }
        } catch (Exception e) {
            System.err.println("设置默认设备失败: " + e.getMessage());
            return new ResponseEntity<>(Collections.singletonMap("message", "系统错误"), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/api/control")
    public ResponseEntity<Map<String, Object>> controlDevice(@RequestBody Map<String, Object> requestBody, HttpSession session) {
        Integer userId = (Integer) session.getAttribute("userId");
        String activeDeviceId = (String) session.getAttribute("activeDeviceId");

        if (userId == null) {
            return new ResponseEntity<>(Collections.singletonMap("message", "未登录"), HttpStatus.UNAUTHORIZED);
        }
        if (activeDeviceId == null || activeDeviceId.trim().isEmpty()) {
            return new ResponseEntity<>(Collections.singletonMap("message", "未选择活跃设备"), HttpStatus.BAD_REQUEST);
        }

        String type = (String) requestBody.get("type");
        Object value = requestBody.get("value");

        if (type == null || value == null) {
            return new ResponseEntity<>(Collections.singletonMap("message", "缺少控制类型或目标值"), HttpStatus.BAD_REQUEST);
        }

        Map<String, Object> message = new HashMap<>();
        message.put("type", type);
        message.put("value", value);
        message.put("timestamp", System.currentTimeMillis());

        try {
            // 🛡️ 二次校验设备归属，防止恶意篡改 Session 中的 activeDeviceId (虽然很难，但属于纵深防御)
            String checkSql = "SELECT COUNT(*) FROM devices WHERE device_unique_id = ? AND user_id = ?";
            Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, activeDeviceId, userId);

            if (count == null || count == 0) {
                return new ResponseEntity<>(Collections.singletonMap("message", "非法操作：设备不属于您"), HttpStatus.FORBIDDEN);
            }

            mqttController.publish(activeDeviceId, new JSONObject(message).toString());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "控制指令已发送");
            return ResponseEntity.ok(response);

        } catch (MqttException e) {
            System.err.println("MQTT 发送失败: " + e.getMessage());
            return new ResponseEntity<>(Collections.singletonMap("message", "设备连接异常，指令发送失败"), HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            System.err.println("控制接口异常: " + e.getMessage());
            return new ResponseEntity<>(Collections.singletonMap("message", "系统错误"), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/api/checkDeviceBinding")
    public ResponseEntity<Map<String, Boolean>> checkDeviceBinding(@RequestParam Integer userId) {
        if (userId == null) return new ResponseEntity<>(HttpStatus.BAD_REQUEST);

        String checkSql = "SELECT COUNT(*) FROM devices WHERE user_id = ?";
        try {
            Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, userId);
            boolean isBound = (count != null && count > 0);
            Map<String, Boolean> response = new HashMap<>();
            response.put("isBound", isBound);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("检查绑定状态失败: " + e.getMessage());
            // 发生错误时，为了用户体验，默认返回 true 让用户进入主页（降级策略）
            return ResponseEntity.ok(Collections.singletonMap("isBound", true));
        }
    }
}