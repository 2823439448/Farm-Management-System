package com.example.farm_management_system.controller;

import com.example.farm_management_system.model.LoginRequest; // 注意这里的包名
import com.example.farm_management_system.service.MQTTController; // ⭐️ 导入 MQTT Controller
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

    // ⭐️ 自动注入 MQTTController
    @Autowired
    private MQTTController mqttController;

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

    // ⭐️ 设置活跃设备，解决最新数据无数据的问题
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

    /**
     * ⭐️ 新增接口：发送控制指令到设备
     * 接口路径：/api/control
     * 请求体：{ "type": "heat" | "humid", "value": 25.0 }
     */
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

        String type = (String) requestBody.get("type"); // "heat" 或 "humid"
        Object value = requestBody.get("value"); // 目标值

        if (type == null || value == null) {
            return new ResponseEntity<>(Collections.singletonMap("message", "缺少控制类型或目标值"), HttpStatus.BAD_REQUEST);
        }

        // 构造 MQTT 消息体
        Map<String, Object> message = new HashMap<>();
        message.put("type", type);
        message.put("value", value);
        message.put("timestamp", System.currentTimeMillis()); // 添加时间戳

        try {
            // 校验设备归属 (防止未设置活跃设备但 activeDeviceId 被篡改)
            String checkSql = "SELECT COUNT(*) FROM devices WHERE device_unique_id = ? AND user_id = ?";
            Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, activeDeviceId, userId);

            if (count == null || count == 0) {
                return new ResponseEntity<>(Collections.singletonMap("message", "该设备不属于您或不存在"), HttpStatus.FORBIDDEN);
            }

            // 发送 MQTT 消息
            mqttController.publish(activeDeviceId, new JSONObject(message).toString());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "控制指令已发送到设备: " + activeDeviceId);
            return ResponseEntity.ok(response);

        } catch (MqttException e) {
            System.err.println("MQTT 发送失败: " + e.getMessage());
            return new ResponseEntity<>(Collections.singletonMap("message", "系统错误：MQTT 消息发送失败"), HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(Collections.singletonMap("message", "系统错误"), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * ⭐️ 新增接口：检查用户是否已注册设备
     * 接口路径：/api/checkDeviceBinding
     * 返回：{ "isBound": true/false }
     *
     * @param userId 从 login.js 中传递过来的用户 ID
     */
    @GetMapping("/api/checkDeviceBinding")
    public ResponseEntity<Map<String, Boolean>> checkDeviceBinding(@RequestParam Integer userId) {
        if (userId == null) {
            // 如果 userId 为空，返回错误请求
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        // 查询该用户拥有的设备数量
        String checkSql = "SELECT COUNT(*) FROM devices WHERE user_id = ?";

        try {
            Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, userId);
            // 如果 count > 0，则认为已绑定设备
            boolean isBound = (count != null && count > 0);

            Map<String, Boolean> response = new HashMap<>();
            response.put("isBound", isBound);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            // 发生错误时，为了防止用户卡在登录页面，默认返回已绑定，让其跳转主页
            // 实际项目中应考虑更好的错误处理
            return ResponseEntity.ok(Collections.singletonMap("isBound", true));
        }
    }
}