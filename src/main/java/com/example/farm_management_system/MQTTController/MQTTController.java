package com.example.farm_management_system.MQTTController;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence; // ✅ 新增：导入内存持久化类
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.sql.*;
import java.time.LocalDateTime;

@Service
public class MQTTController {

    // 数据库配置
    private static final String JDBC_URL = "jdbc:mysql://localhost:3306/farm_manager?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    private static final String JDBC_USER = "root";
    private static final String JDBC_PASSWORD = "19416135"; // 请检查密码是否正确

    // MQTT 配置
    private static final String MQTT_BROKER = "tcp://broker.emqx.io:1883";
    private static final String MQTT_TOPIC = "dlc/farm_manager/#"; // ⚠️ 请替换为你独有的前缀！

    private MqttClient client;

    // @PostConstruct 确保应用启动时自动连接
    @PostConstruct
    public void init() {
        // 以此开启新线程，防止阻塞主程序的启动
        new Thread(this::connectAndSubscribe).start();
    }

    private void connectAndSubscribe() {
        try {
            // ✅ 修正点：使用 MemoryPersistence 替代默认的文件持久化，取消在本地创建文件
            client = new MqttClient(
                    MQTT_BROKER,
                    "SpringBootServer_" + System.currentTimeMillis(),
                    new MemoryPersistence() // 👈 使用内存持久化
            );

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true); // 开启自动重连

            client.connect(options);
            System.out.println("MQTT 已连接");

            // 订阅主题，并使用 Lambda 表达式处理收到的消息
            client.subscribe(MQTT_TOPIC, (topic, msg) -> {
                String payload = new String(msg.getPayload());
                System.out.println("收到 MQTT 数据：" + payload);
                saveToDatabase(payload);
            });

            System.out.println("已订阅: " + MQTT_TOPIC);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * 将 MQTT JSON 数据写入 MySQL
     * 期望的 JSON 格式: {"deviceId": "A001", "temperature": 25.5, "humidity": 60.0, "light": 800, "timestamp": "2025-12-01T10:00:00"}
     */
    private void saveToDatabase(String jsonStr) {
        try {
            JSONObject json = new JSONObject(jsonStr);

            // 1. 解析数据
            String deviceId = json.getString("deviceId");
            float temperature = json.getFloat("temperature");
            float humidity = json.getFloat("humidity");
            float light = json.getFloat("light");
            String timeStr = json.getString("timestamp");

            // 2. 处理时间格式 (支持标准 ISO 格式)
            LocalDateTime time = LocalDateTime.parse(timeStr);

            // 3. 执行写入
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
            e.printStackTrace();
        }
    }
}