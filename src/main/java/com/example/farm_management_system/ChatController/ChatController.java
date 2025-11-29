package com.example.farm_management_system.ChatController;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class ChatController {

    private static final String DEEPSEEK_API_URL = "https://api.deepseek.com/v1/chat/completions";
    private static final String DEEPSEEK_API_KEY = "sk-ead245fae4e1477aa394e2d64e63f558";

    @PostMapping(
            value = "/api/chat",
            consumes = MediaType.TEXT_PLAIN_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE
    )
    public ResponseEntity<String> handleChat(@RequestBody String userMessage) {

        if (userMessage == null || userMessage.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("消息不能为空");
        }

        try {
            String aiResponse = callDeepSeekAPI(userMessage.trim());
            return ResponseEntity.ok(aiResponse);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("服务器错误：" + e.getMessage());
        }
    }

    private String callDeepSeekAPI(String userMessage) {

        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + DEEPSEEK_API_KEY);
        headers.set("Content-Type", "application/json");

        // -------- 系统提示词（固定格式输出）--------
        Map<String, Object> systemPrompt = new HashMap<String, Object>();
        systemPrompt.put("role", "system");
        systemPrompt.put(
                "content",
                "你是智能农场 AI 助手。当用户询问作物温湿度时，你必须仅输出：" +
                        "\n温度: XX℃\n湿度: XX%\n" +
                        "禁止输出解释内容。"
        );

        Map<String, Object> userPrompt = new HashMap<String, Object>();
        userPrompt.put("role", "user");
        userPrompt.put("content", userMessage);

        Map<String, Object>[] messages = new Map[2];
        messages[0] = systemPrompt;
        messages[1] = userPrompt;

        // 请求体
        Map<String, Object> requestBody = new HashMap<String, Object>();
        requestBody.put("model", "deepseek-chat");
        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 300);
        requestBody.put("stream", false);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<Map<String, Object>>(requestBody, headers);

        try {
            Map response = restTemplate.postForObject(DEEPSEEK_API_URL, entity, Map.class);

            if (response == null) return "AI 无响应";

            Object choicesObj = response.get("choices");
            if (!(choicesObj instanceof List)) return "AI 返回格式异常";

            List choices = (List) choicesObj;
            if (choices.isEmpty()) return "AI 未返回数据";

            Object msgObj = ((Map) choices.get(0)).get("message");
            if (!(msgObj instanceof Map)) return "AI 内容异常";

            Map msg = (Map) msgObj;
            Object contentObj = msg.get("content");
            if (contentObj == null) return "AI 内容为空";

            return contentObj.toString().trim();

        } catch (Exception e) {
            e.printStackTrace();
            return "调用 DeepSeek 出错: " + e.getMessage();
        }
    }

    @GetMapping("/api/health")
    public String health() {
        return "AI Chat Service Running!";
    }
}
