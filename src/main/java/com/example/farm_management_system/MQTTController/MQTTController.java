package com.example.farm_management_system.MQTTController;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct; // Spring Boot 3.x 可能需要 jakarta.annotation
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class MQTTController {

    // 数据库配置
    private static final String JDBC_URL = "jdbc:mysql://localhost:3306/farm_manager?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    private static final String JDBC_USER = "root";
    private static final String JDBC_PASSWORD = "19416135"; // 请替换为你的实际密码

    private static final String MQTT_BROKER = "tcp://broker.hivemq.com:1883";
    private static final String MQTT_TOPIC = "iot/topic/#";

    private MqttClient client;

    // @PostConstruct 保证了 Spring Boot 启动后立即运行此方法
    @PostConstruct
    public void init() {
        //以此开启新线程，防止阻塞主程序的启动
        new Thread(this::connectAndSubscribe).start();
    }

    private void connectAndSubscribe() {
        try {
            // client ID 加个随机数，避免测试时冲突
            client = new MqttClient(MQTT_BROKER, "SpringBootServer_" + System.currentTimeMillis());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true); // 开启断线重连

            client.connect(options);
            System.out.println("✅ MQTT 已连接到 Broker");

            // 设置回调处理接收到的消息
            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    System.out.println("❌ MQTT 连接断开: " + cause.getMessage());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    String payload = new String(message.getPayload());
                    System.out.println("📥 收到设备消息 [" + topic + "]: " + payload);
                    // 只要收到消息，就尝试写入数据库（不管用户是否在线）
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
     * 逻辑：只负责存。数据归属谁，由 devices 表的绑定关系决定，这里不关心。
     */
    private void saveToDatabase(String jsonStr) {
        try {
            JSONObject json = new JSONObject(jsonStr);

            // 1. 解析数据
            String deviceId = json.getString("deviceId"); // 对应 devices 表的 device_unique_id
            float temperature = json.getFloat("temperature");
            float humidity = json.getFloat("humidity");
            float light = json.getFloat("light");
            String timeStr = json.getString("timestamp");

            // 2. 处理时间格式 (支持 ISO 格式)
            LocalDateTime time = LocalDateTime.parse(timeStr);

            // 3. 执行写入
            // 注意：这里不需要 user_id，我们只存 "这个设备在什么时间产生了什么数据"
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
            // 可以在这里打印 jsonStr 看看是不是格式发错了
        }
    }
}