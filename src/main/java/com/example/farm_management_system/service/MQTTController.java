package com.example.farm_management_system.service;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

@Service
public class MQTTController {

    // 自动注入数据库工具 (JdbcTemplate)
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 从 application.properties 读取配置
    @Value("${mqtt.broker}")
    private String mqttBroker;

    @Value("${mqtt.topic}")
    private String mqttTopic;

    @Value("${mqtt.client-id-prefix}")
    private String clientIdPrefix;

    // ⭐️ 新增配置项：设备控制主题前缀
    @Value("${mqtt.to-device-topic-prefix}")
    private String toDeviceTopicPrefix;

    private MqttClient client;

    @PostConstruct
    public void init() {
        // 使用新线程启动连接，不阻塞主程序
        new Thread(this::connectAndSubscribe).start();
    }

    /**
     * ⭐️ 修复点：更健壮的连接和重连逻辑
     */
    private void connectAndSubscribe() {
        final long RETRY_DELAY_MS = 5000; // 每次重连间隔 5 秒

        while (true) { // 无限循环，保持连接
            try {
                if (client == null) {
                    // 创建新的 MqttClient 实例
                    client = new MqttClient(mqttBroker, clientIdPrefix + System.currentTimeMillis(), new MemoryPersistence());
                }

                if (!client.isConnected()) {
                    MqttConnectOptions options = new MqttConnectOptions();
                    options.setCleanSession(true); // 每次连接都是新的会话

                    System.out.println("⚠️ 尝试连接 MQTT...");
                    client.connect(options);
                    System.out.println("✅ MQTT 已连接: " + mqttBroker);

                    // 重新订阅
                    client.subscribe(mqttTopic, (topic, msg) -> {
                        String payload = new String(msg.getPayload());
                        saveToDatabase(payload);
                    });
                    System.out.println("✅ 已订阅主题: " + mqttTopic);
                }

                // 保持线程运行，每隔 30 秒检查一次连接是否还活跃
                Thread.sleep(30000);

            } catch (MqttException e) {
                System.err.println("❌ MQTT 连接/订阅失败: " + e.getMessage());
                System.out.println("⚠️ 正在重试连接...");

                // 等待一段时间后再次尝试连接
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    System.err.println("MQTT 线程被中断。");
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("MQTT 线程被中断。");
                break;
            }
        }
    }

    /**
     * ⭐️ 新增方法：发布消息到控制主题
     * @param deviceId 目标设备ID
     * @param jsonMessage 要发送的 JSON 消息体
     */
    public void publish(String deviceId, String jsonMessage) throws MqttException {
        if (client == null || !client.isConnected()) {
            throw new MqttException(new Throwable("MQTT 客户端未连接"));
        }

        // 目标主题：dlc/farm_todev/deviceId
        String topic = toDeviceTopicPrefix + deviceId;
        MqttMessage message = new MqttMessage(jsonMessage.getBytes());
        message.setQos(1); // 保证消息送达 (QoS 1)

        System.out.println("➡️ 发送控制指令到主题: " + topic + ", 消息: " + jsonMessage);
        client.publish(topic, message);
    }

    /**
     * 接收并保存数据到数据库 (使用 JdbcTemplate)
     */
    private void saveToDatabase(String jsonStr) {
        try {
            JSONObject json = new JSONObject(jsonStr);

            String deviceId = json.getString("deviceId");
            // 使用 getFloat 兼容浮点数
            float temperature = json.getFloat("temperature");
            float humidity = json.getFloat("humidity");
            float light = json.getFloat("light");
            String timeStr = json.getString("timestamp");

            // 解析时间
            LocalDateTime time = LocalDateTime.parse(timeStr);

            // 执行写入
            String sql = "INSERT INTO sensor_data (device_id, temperature, humidity, light, timestamp) VALUES (?, ?, ?, ?, ?)";

            // 使用 JdbcTemplate 写入数据库
            int rows = jdbcTemplate.update(sql, deviceId, temperature, humidity, light, Timestamp.valueOf(time));

            if (rows > 0) {
                System.out.println("💾 数据已存入数据库 (设备: " + deviceId + ")");
            }

        } catch (DateTimeParseException e) {
            System.err.println("❌ 时间格式错误，无法解析时间戳: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("❌ 写入数据库失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}