package com.example.farm_management_system.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class WeatherController {

    @Value("${weather.api.key}")
    private String weatherApiKey;

    @Value("${weather.api.url:https://api.weatherapi.com/v1}")
    private String weatherApiUrl;

    /**
     * 获取城市天气信息
     * @param city 城市名称（中文或英文）
     * @return 天气数据
     */
    @GetMapping("/api/weather")
    public ResponseEntity<?> getWeather(@RequestParam String city) {
        // 输入验证
        if (city == null || city.trim().isEmpty()) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "城市名称不能为空");
            return ResponseEntity.badRequest().body(error);
        }

        // 限制城市名称长度
        if (city.length() > 50) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "城市名称过长");
            return ResponseEntity.badRequest().body(error);
        }

        try {
            String url = String.format("%s/current.json?key=%s&q=%s&lang=zh",
                    weatherApiUrl, weatherApiKey, city);

            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "天气服务返回错误");
                return ResponseEntity.status(response.getStatusCode()).body(error);
            }

            // 返回天气数据
            return ResponseEntity.ok(response.getBody());

        } catch (Exception e) {
            System.err.println("天气API调用失败: " + e.getMessage());

            // 根据异常类型返回不同的错误信息
            String errorMsg = "获取天气信息失败";
            if (e.getMessage() != null && e.getMessage().contains("404")) {
                errorMsg = "未找到该城市，请检查城市名称";
            } else if (e.getMessage() != null && e.getMessage().contains("401")) {
                errorMsg = "天气服务认证失败";
            }

            Map<String, String> error = new HashMap<>();
            error.put("error", errorMsg);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}