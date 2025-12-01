package com.example.farm_management_system.MQTTController;

import org.eclipse.paho.client.mqttv3.*;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.sql.*;
import java.time.LocalDateTime;

@Service
public class MQTTController {

    // 数据库配置 (请检查密码是否正确)
    private static final String JDBC_URL = "jdbc:mysql://localhost:3306/farm_manager?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    private static final String JDBC_USER = "root";
    private static final String JDBC_PASSWORD = "19416135";

    // MQTT 配置 (请将主题改为你自己的唯一主题，以避免收到不相干数据)
    private static final String MQTT_BROKER = "tcp://broker.emqx.io:1883";
    private static final String MQTT_TOPIC = "dlc/farm_manager/#"; // ⚠️ 请替换为你独有的前缀！

    private MqttClient client;

    // ✅ 修正点 1: 使用 @PostConstruct 确保应用启动时自动连接
    @PostConstruct
    public void init() {
        new Thread(this::connectAndSubscribe).start();
    }

    private void connectAndSubscribe() {
        try {
            client = new MqttClient(MQTT_BROKER, "SpringBootServer_" + System.currentTimeMillis());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);

            // ✅ 优化点: 设置 Keep Alive 间隔为 60 秒 (建议值)
            // 这会强制客户端每 60 秒向 Broker 发送一次心跳包。
            options.setKeepAliveInterval(20);

            client.connect(options);
            System.out.println("✅ MQTT 已连接到 Broker");

            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    System.out.println("❌ MQTT 连接断开: " + cause.getMessage());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    String payload = new String(message.getPayload());
                    System.out.println("📥 收到设备消息 [" + topic + "]: " + payload);
                    // ✅ 修正点 2: 确保只传入一个参数
                    saveToDatabase(payload);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {}
            });

            client.subscribe(MQTT_TOPIC);
            System.out.println("📡 已订阅主题: " + MQTT_TOPIC);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** * 将 MQTT JSON 数据写入 MySQL
     * 适配 JSON 格式: {"deviceId": "...", "temperature": 25.5, ..., "timestamp": "2023-12-02T10:00:00"}
     */
    private void saveToDatabase(String jsonStr) {
        try {
            JSONObject json = new JSONObject(jsonStr);

            // ✅ 修正点 3: 直接从 JSON 中获取单值 (修复了 getJSONArray 错误)
            String deviceId = json.getString("deviceId");
            float temperature = json.getFloat("temperature");
            float humidity = json.getFloat("humidity");
            float light = json.getFloat("light");

            String timeStr = json.getString("timestamp");

            // ✅ 修正点 4: 标准 ISO 格式（有 T）可以被 LocalDateTime.parse 直接处理
            LocalDateTime time = LocalDateTime.parse(timeStr);

            // 3. 执行写入 (你的 SQL 语句是正确的，不需要 created_at)
            String sql = "INSERT INTO sensor_data (device_id, temperature, humidity, light, timestamp) VALUES (?, ?, ?, ?, ?)";

            try (Connection conn = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD);
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, deviceId);
                ps.setFloat(2, temperature);
                ps.setFloat(3, humidity);
                ps.setFloat(4, light);
                ps.setTimestamp(5, Timestamp.valueOf(time));

                int rows = ps.executeUpdate();
                if (rows > 0) {
                    System.out.println("💾 数据已存入数据库 (设备: " + deviceId + ")");
                }
            }

        } catch (Exception e) {
            System.err.println("❌ 数据解析或写入失败: " + e.getMessage());
        }
    }
}