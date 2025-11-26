package com.example.farm_management_system.ChatController;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.HashMap;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*") // 允许前端跨域访问
public class ChatController {

    // DeepSeek API配置 - 请替换为您的实际API密钥
    private static final String DEEPSEEK_API_URL = "https://api.deepseek.com/v1/chat/completions";
    private static final String DEEPSEEK_API_KEY = "sk-8beb61c3cd574763ad6622ab4e80ca31"; // 请替换为您的API密钥

    /**
     * 处理聊天请求 - 完全适配您的前端HTML代码
     * 前端发送: POST /api/chat, Content-Type: text/plain, 请求体是纯文本消息
     * 后端返回: 纯文本响应
     */
    @PostMapping(value = "/api/chat",
            consumes = MediaType.TEXT_PLAIN_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> handleChat(@RequestBody String userMessage) {
        try {
            // 验证消息
            if (userMessage == null || userMessage.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("消息内容不能为空");
            }

            if (userMessage.length() > 2000) {
                return ResponseEntity.badRequest().body("消息长度不能超过2000字符");
            }

            // 调用DeepSeek API
            String aiResponse = callDeepSeekAPI(userMessage.trim());

            // 返回纯文本响应给前端
            return ResponseEntity.ok(aiResponse);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("服务器错误: " + e.getMessage());
        }
    }

    /**
     * 调用DeepSeek API
     */
    private String callDeepSeekAPI(String userMessage) {
        try {
            RestTemplate restTemplate = new RestTemplate();

            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + DEEPSEEK_API_KEY);
            headers.set("Content-Type", "application/json");

            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "deepseek-chat");

            Map<String, String> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", userMessage);

            requestBody.put("messages", new Map[]{message});
            requestBody.put("temperature", 0.7);
            requestBody.put("max_tokens", 2000);
            requestBody.put("stream", false);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            // 发送请求
            Map response = restTemplate.postForObject(DEEPSEEK_API_URL, request, Map.class);

            // 解析响应
            if (response != null && response.containsKey("choices")) {
                java.util.List<Map> choices = (java.util.List<Map>) response.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map firstChoice = choices.get(0);
                    Map messageMap = (Map) firstChoice.get("message");
                    if (messageMap != null && messageMap.containsKey("content")) {
                        return messageMap.get("content").toString();
                    }
                }
            }

            return "抱歉，AI暂时无法响应，请稍后重试。";

        } catch (Exception e) {
            e.printStackTrace();
            return "调用AI服务失败: " + e.getMessage();
        }
    }

    /**
     * 健康检查接口
     */
    @GetMapping("/api/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("DeepSeek Chat Service is running - " + new java.util.Date());
    }

    /**
     * 测试连接接口
     */
    @GetMapping("/api/test")
    public ResponseEntity<String> testConnection() {
        return ResponseEntity.ok("Chat Controller is working!");
    }
}