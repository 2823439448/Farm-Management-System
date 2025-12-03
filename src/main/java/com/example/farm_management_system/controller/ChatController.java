package com.example.farm_management_system.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class ChatController {

    @Value("${deepseek.api.url}")
    private String deepseekApiUrl;

    @Value("${deepseek.api.key}")
    private String deepseekApiKey;

    @PostMapping(value = "/api/chat", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> handleChat(@RequestBody String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("消息不能为空");
        }
        try {
            String aiResponse = callDeepSeekAPI(userMessage.trim());
            return ResponseEntity.ok(aiResponse);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("服务器错误：" + e.getMessage());
        }
    }

    private String callDeepSeekAPI(String userMessage) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        // 这里自动读取配置文件里的 key
        headers.set("Authorization", "Bearer " + deepseekApiKey);
        headers.set("Content-Type", "application/json");

        Map<String, Object> systemPrompt = new HashMap<>();
        systemPrompt.put("role", "system");
        systemPrompt.put("content", "你是一个友好的智能农场 AI 助手。你的主要职责是提供作物信息和协助农场管理。\n\n" +
                "**特殊指令**：当用户询问某作物的**最佳温湿度**时，你必须**首先**以以下严格格式输出，并且输出后**立即**向用户询问是否需要自动设置温度参数：\n" +
                "温度: XX-YY℃\n" +
                "湿度: ZZ-WW%\n" +
                "你想让我帮你自动设置目标温湿度吗？（我会取建议范围的上限进行设置）\n\n" +
                "**一般指令**：对于任何其他话题或后续问题，你应当以友好、自然的方式自由交谈，提供详细的解释，回答问题，或根据上下文继续对话。");

        Map<String, Object> userPrompt = new HashMap<>();
        userPrompt.put("role", "user");
        userPrompt.put("content", userMessage);

        Map<String, Object>[] messages = new Map[]{systemPrompt, userPrompt};

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "deepseek-chat");
        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 300);
        requestBody.put("stream", false);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            // 这里自动读取配置文件里的 url
            Map response = restTemplate.postForObject(deepseekApiUrl, entity, Map.class);
            if (response == null) return "AI 无响应";

            Object choicesObj = response.get("choices");
            if (!(choicesObj instanceof List)) return "AI 返回格式异常";
            List choices = (List) choicesObj;
            if (choices.isEmpty()) return "AI 未返回数据";

            Object msgObj = ((Map) choices.get(0)).get("message");
            if (!(msgObj instanceof Map)) return "AI 内容异常";
            Object contentObj = ((Map) msgObj).get("content");
            return contentObj == null ? "AI 内容为空" : contentObj.toString().trim();

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