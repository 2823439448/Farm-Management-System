package com.example.farm_management_system.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class SensorDataController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 获取最新数据
    @GetMapping("/api/my-device-data")
    public ResponseEntity<List<Map<String, Object>>> getMySensorData(HttpSession session) {
        Integer userId = (Integer) session.getAttribute("userId");
        String activeDeviceId = (String) session.getAttribute("activeDeviceId");

        if (userId == null) return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        // 如果没有活跃设备，返回空列表，前端渲染无数据
        if (activeDeviceId == null || activeDeviceId.trim().isEmpty()) return ResponseEntity.ok(new ArrayList<>());

        String sql = "SELECT d.device_name, d.device_unique_id, s.temperature, s.humidity, s.light, s.timestamp " +
                "FROM devices d " +
                "JOIN sensor_data s ON d.device_unique_id = s.device_id " +
                "WHERE d.user_id = ? AND d.device_unique_id = ? " +
                "AND s.timestamp = (SELECT MAX(sub.timestamp) FROM sensor_data sub WHERE sub.device_id = d.device_unique_id)";

        try {
            List<Map<String, Object>> list = jdbcTemplate.query(sql, (rs, rowNum) -> {
                Map<String, Object> data = new HashMap<>();
                data.put("deviceName", rs.getString("device_name"));
                data.put("deviceId", rs.getString("device_unique_id"));

                // 使用 getObject 并判断是否为空，确保能返回 null
                Object temp = rs.getObject("temperature");
                data.put("temperature", temp);

                Object humid = rs.getObject("humidity");
                data.put("humidity", humid);

                Object light = rs.getObject("light");
                data.put("light", light);

                // 兼容性更好的时间格式
                data.put("timestamp", rs.getTimestamp("timestamp").toString());
                return data;
            }, userId, activeDeviceId);

            return ResponseEntity.ok(list);

        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // 获取历史数据
    @GetMapping("/api/device-history")
    public ResponseEntity<List<Map<String, Object>>> getDeviceHistory(
            @RequestParam String deviceId,
            @RequestParam String range,
            @RequestParam String granularity,
            HttpSession session) {

        Integer userId = (Integer) session.getAttribute("userId");
        if (userId == null) return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);

        // --- 计算时间范围 ---
        long endTime = System.currentTimeMillis();
        long startTime;
        switch (range) {
            case "week": startTime = endTime - (7L * 24 * 3600 * 1000); break;
            case "month": startTime = endTime - (30L * 24 * 3600 * 1000); break;
            case "day": startTime = endTime - (24L * 3600 * 1000); break;
            default: startTime = endTime - (730L * 24 * 3600 * 1000); break;
        }

        String sql;
        String groupingClause = "";
        boolean isRaw = "minute".equalsIgnoreCase(granularity) || "hour".equalsIgnoreCase(granularity);

        if (isRaw) {
            // 原始数据查询
            sql = "SELECT s.temperature, s.humidity, s.light, s.timestamp FROM sensor_data s JOIN devices d ON s.device_id = d.device_unique_id ";
        } else {
            // 聚合数据查询 (日,月,年)
            if ("day".equalsIgnoreCase(granularity)) groupingClause = "DATE(s.timestamp)";
            else if ("month".equalsIgnoreCase(granularity)) groupingClause = "YEAR(s.timestamp), MONTH(s.timestamp)";
            else if ("year".equalsIgnoreCase(granularity)) groupingClause = "YEAR(s.timestamp)";

            sql = "SELECT AVG(s.temperature) as temperature, AVG(s.humidity) as humidity, AVG(s.light) as light, MIN(s.timestamp) as timestamp " +
                    "FROM sensor_data s JOIN devices d ON s.device_id = d.device_unique_id ";
        }

        sql += "WHERE d.user_id = ? AND s.device_id = ? AND s.timestamp BETWEEN ? AND ? ";

        // ⭐️ 修正点：聚合查询时，使用聚合函数对时间进行排序，避免 only_full_group_by 错误
        if (!isRaw && !groupingClause.isEmpty()) {
            sql += "GROUP BY " + groupingClause + " ";
            sql += "ORDER BY MIN(s.timestamp) ASC"; // 修正后的排序
        } else {
            sql += "ORDER BY s.timestamp ASC"; // 原始数据排序
        }

        try {
            List<Map<String, Object>> list = jdbcTemplate.query(sql, (rs, rowNum) -> {
                Map<String, Object> data = new HashMap<>();

                // 使用 getObject 并判断是否为空，确保能返回 null
                Object temp = rs.getObject("temperature");
                data.put("temperature", temp);

                Object humid = rs.getObject("humidity");
                data.put("humidity", humid);

                Object light = rs.getObject("light");
                data.put("light", light);

                // 返回毫秒时间戳，供前端 Chart.js 使用
                data.put("timestamp", rs.getTimestamp("timestamp").getTime());
                return data;
            }, userId, deviceId, new Timestamp(startTime), new Timestamp(endTime));

            return ResponseEntity.ok(list);

        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}