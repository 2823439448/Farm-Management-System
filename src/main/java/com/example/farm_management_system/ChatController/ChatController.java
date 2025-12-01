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
                "你是一个友好的智能农场 AI 助手。你的主要职责是提供作物信息和协助农场管理。\n\n" +
                        // 强制格式要求（当且仅当用户询问温湿度时）
                        "**特殊指令**：当用户询问某作物的**最佳温湿度**时，你必须**首先**以以下严格格式输出，并且输出后**立即**向用户询问是否需要自动设置温度参数：\n" +
                        "温度: XX-YY℃\n" +
                        "湿度: ZZ-WW%\n" +
                        // 询问用户的提示
                        "你想让我帮你自动设置目标温湿度吗？（我会取建议范围的上限进行设置）\n\n" +
                        // 自由交谈要求（处理其他所有情况）
                        "**一般指令**：对于任何其他话题或后续问题，你应当以友好、自然的方式自由交谈，提供详细的解释，回答问题，或根据上下文继续对话。"
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
