package com.example.farm_management_system.MQTTController;


import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.time.LocalDateTime;

@Service
public class MQTTController {

    private static final String JDBC_URL = "jdbc:mysql://localhost:3306/farm_manager?useSSL=false&serverTimezone=UTC";
    private static final String JDBC_USER = "root";
    private static final String JDBC_PASSWORD = "19416135";

    private static final String MQTT_BROKER = "tcp://broker.hivemq.com:1883";
    private static final String MQTT_TOPIC = "iot/topic/#";

    private MqttClient client;

    public void MqttService() {
        connectAndSubscribe();
    }

    private void connectAndSubscribe() {
        try {
            client = new MqttClient(MQTT_BROKER, MqttClient.generateClientId());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);

            client.connect(options);
            System.out.println("MQTT 已连接");

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


    /** 将 MQTT JSON 数据写入 MySQL */
    private void saveToDatabase(String jsonStr) {
        try {
            JSONObject json = new JSONObject(jsonStr);

            String deviceId = json.getString("deviceId");
            float temperature = json.getFloat("temperature");
            float humidity = json.getFloat("humidity");
            float light = json.getFloat("light");

            String timeStr = json.getString("timestamp");
            LocalDateTime time = LocalDateTime.parse(timeStr);

            String sql = "INSERT INTO sensor_data (device_id, temperature, humidity, light, timestamp) VALUES (?, ?, ?, ?, ?)";

            try (Connection conn = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD);
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, deviceId);
                ps.setFloat(2, temperature);
                ps.setFloat(3, humidity);
                ps.setFloat(4, light);
                ps.setTimestamp(5, Timestamp.valueOf(time));

                ps.executeUpdate();
                System.out.println("写入 MySQL 成功");
            }

        } catch (Exception e) {
            System.out.println("写入数据库失败");
            e.printStackTrace();
        }
    }
}
