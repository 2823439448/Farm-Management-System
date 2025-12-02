package com.example.farm_management_system.SensorDataController;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
     * 作用: 获取当前登录用户 Session 中指定的活跃设备的最新传感器数据。
     */
    @GetMapping("/api/my-device-data")
    public ResponseEntity<List<Map<String, Object>>> getMySensorData(HttpSession session) {

        Integer userId = (Integer) session.getAttribute("userId");
        String activeDeviceId = (String) session.getAttribute("activeDeviceId");

        if (userId == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        if (activeDeviceId == null || activeDeviceId.trim().isEmpty()) {
            return ResponseEntity.ok(new ArrayList<>());
        }

        String sql = "SELECT d.device_name, d.device_unique_id, s.temperature, s.humidity, s.light, s.timestamp " +
                "FROM devices d " +
                "JOIN sensor_data s ON d.device_unique_id = s.device_id " +
                "WHERE d.user_id = ? AND d.device_unique_id = ? " +
                "AND s.timestamp = ( " +
                "    SELECT MAX(sub.timestamp) " +
                "    FROM sensor_data sub " +
                "    WHERE sub.device_id = d.device_unique_id" +
                ") ";


        List<Map<String, Object>> resultList = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            ps.setString(2, activeDeviceId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                Map<String, Object> data = new HashMap<>();
                data.put("deviceName", rs.getString("device_name"));
                data.put("deviceId", rs.getString("device_unique_id"));

                // ⚠️ 增加 NULL 检查：确保数据库 NULL 值不会被转为 0.0
                float tempValue = rs.getFloat("temperature");
                data.put("temperature", rs.wasNull() ? null : tempValue);

                float humidValue = rs.getFloat("humidity");
                data.put("humidity", rs.wasNull() ? null : humidValue);

                float lightValue = rs.getFloat("light");
                data.put("light", rs.wasNull() ? null : lightValue);

                data.put("timestamp", rs.getTimestamp("timestamp").toString());
                resultList.add(data);
            }

            return ResponseEntity.ok(resultList);

        } catch (SQLException e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


    /**
     * URL: /api/device-history
     * 作用: 获取指定设备在指定时间范围和粒度的历史传感器数据。
     */
    @GetMapping("/api/device-history")
    public ResponseEntity<List<Map<String, Object>>> getDeviceHistory(
            @RequestParam String deviceId,
            @RequestParam String range,
            @RequestParam String granularity,
            HttpSession session) {

        Integer userId = (Integer) session.getAttribute("userId");

        if (userId == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        if (deviceId == null || deviceId.trim().isEmpty()) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        // --- 1. 计算查询时间范围 ---
        long endTime = System.currentTimeMillis();
        long startTime;

        switch (range) {
            case "week":
                startTime = endTime - (7L * 24 * 60 * 60 * 1000);
                break;
            case "month":
                startTime = endTime - (30L * 24 * 60 * 60 * 1000);
                break;
            case "day":
                startTime = endTime - (24L * 60 * 60 * 1000);
                break;
            case "all": // 用于查询您 2023 年的数据
            default:
                startTime = endTime - (730L * 24 * 60 * 60 * 1000); // 近 2 年
                break;
        }

        Timestamp startTimestamp = new Timestamp(startTime);
        Timestamp endTimestamp = new Timestamp(endTime);

        // --- 2. 根据粒度选择 SQL 聚合逻辑 ---
        String sql;
        String groupingClause = "";

        if ("minute".equalsIgnoreCase(granularity) || "hour".equalsIgnoreCase(granularity)) {
            // 分钟/小时粒度：直接查询所有原始数据
            sql = "SELECT s.temperature, s.humidity, s.light, s.timestamp " +
                    "FROM sensor_data s " +
                    "JOIN devices d ON s.device_id = d.device_unique_id ";
        } else {
            // day, month, year 粒度：进行聚合查询 (求平均值)
            if ("day".equalsIgnoreCase(granularity)) {
                groupingClause = "DATE(s.timestamp)";
            } else if ("month".equalsIgnoreCase(granularity)) {
                groupingClause = "YEAR(s.timestamp), MONTH(s.timestamp)";
            } else if ("year".equalsIgnoreCase(granularity)) {
                groupingClause = "YEAR(s.timestamp)";
            }

            sql = "SELECT AVG(s.temperature) as temperature, " +
                    "AVG(s.humidity) as humidity, " +
                    "AVG(s.light) as light, " +
                    "MIN(s.timestamp) as timestamp " + // 使用时间戳最小值作为分组标签
                    "FROM sensor_data s " +
                    "JOIN devices d ON s.device_id = d.device_unique_id ";

            sql += "WHERE d.user_id = ? AND s.device_id = ? " +
                    "AND s.timestamp BETWEEN ? AND ? ";

            if (!groupingClause.isEmpty()) {
                sql += "GROUP BY " + groupingClause + " ";
            }
        }

        // 重新组合 WHERE 子句 (非聚合查询也需要)
        if ("minute".equalsIgnoreCase(granularity) || "hour".equalsIgnoreCase(granularity)) {
            sql += "WHERE d.user_id = ? AND s.device_id = ? " +
                    "AND s.timestamp BETWEEN ? AND ? ";
        }

        sql += "ORDER BY s.timestamp ASC";


        List<Map<String, Object>> resultList = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            ps.setString(2, deviceId);
            ps.setTimestamp(3, startTimestamp);
            ps.setTimestamp(4, endTimestamp);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                Map<String, Object> data = new HashMap<>();

                // ⚠️ 关键修正：温度数据读取与 NULL 检查
                float tempValue = rs.getFloat("temperature");
                data.put("temperature", rs.wasNull() ? null : tempValue); // 如果 NULL，返回 JSON null

                // 湿度数据读取与 NULL 检查
                float humidValue = rs.getFloat("humidity");
                data.put("humidity", rs.wasNull() ? null : humidValue);

                // 光照数据读取与 NULL 检查
                float lightValue = rs.getFloat("light");
                data.put("light", rs.wasNull() ? null : lightValue);

                // 返回毫秒时间戳
                data.put("timestamp", rs.getTimestamp("timestamp").getTime());
                resultList.add(data);
            }

            return ResponseEntity.ok(resultList);

        } catch (SQLException e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}