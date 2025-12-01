package com.example.farm_management_system.WebConfig;

import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        Object userId = request.getSession().getAttribute("userId"); // 获取 Session 中的 userId
        if (userId == null) {
            // 未登录，重定向到登录页
            response.sendRedirect("/login/login.html");
            return false;
        }
        return true; // 已登录，放行
    }
}