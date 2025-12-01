package com.example.farm_management_system.SensorDataController;
//前端获取最新数据接口
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class SensorDataController {

    private static final String JDBC_URL = "jdbc:mysql://localhost:3306/farm_manager?useSSL=false&serverTimezone=UTC";
    private static final String JDBC_USER = "root";
    private static final String JDBC_PASSWORD = "19416135";

    /**
     * URL: /api/my-device-data
     * 作用: 根据当前登录用户的 Session ID，获取其绑定的所有设备的最新传感器数据。
     */
    @GetMapping("/api/my-device-data")
    public ResponseEntity<List<Map<String, Object>>> getMySensorData(HttpSession session) {

        Integer userId = (Integer) session.getAttribute("userId");
        if (userId == null) {
            // 用户未登录，返回 401
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        // 核心 SQL: 联表查询，筛选用户 ID，并通过子查询确保只获取每个设备的最新一条数据
        String sql = "SELECT d.device_name, d.device_unique_id, s.temperature, s.humidity, s.light, s.timestamp " +
                "FROM devices d " +
                "JOIN sensor_data s ON d.device_unique_id = s.device_id " +
                "WHERE d.user_id = ? " +
                "AND s.timestamp = ( " +
                "    SELECT MAX(sub.timestamp) " +
                "    FROM sensor_data sub " +
                "    WHERE sub.device_id = d.device_unique_id" +
                ") " +
                "ORDER BY d.device_name ASC";

        List<Map<String, Object>> resultList = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                Map<String, Object> data = new HashMap<>();
                data.put("deviceName", rs.getString("device_name"));
                data.put("deviceId", rs.getString("device_unique_id"));
                data.put("temperature", rs.getFloat("temperature"));
                data.put("humidity", rs.getFloat("humidity"));
                data.put("light", rs.getFloat("light")); // ✅ 注意: 你的设备数据是 light
                data.put("timestamp", rs.getTimestamp("timestamp").toString());
                resultList.add(data);
            }

            return ResponseEntity.ok(resultList);

        } catch (SQLException e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}