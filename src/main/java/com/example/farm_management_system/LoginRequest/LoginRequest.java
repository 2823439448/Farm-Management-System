package com.example.farm_management_system.LoginRequest;

// 新建文件：LoginRequest.java
public class LoginRequest {
    private String username;
    private String password;

    // 必须提供无参构造函数
    public LoginRequest() {}

    // 必须提供 getter 和 setter 方法，供 Jackson 序列化/反序列化使用
    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }
    public String getPassword() {
        return password;
    }
    public void setPassword(String password) {
        this.password = password;
    }
}