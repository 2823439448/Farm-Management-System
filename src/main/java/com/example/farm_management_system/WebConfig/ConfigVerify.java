package com.example.farm_management_system.WebConfig;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ConfigVerify implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor())
                // 拦截所有需要登录才能访问的路径
                .addPathPatterns("/**")
                .excludePathPatterns(
                        // 1. 登录和注册相关的HTML页面和API
                        "/login/login",
                        "/login/login.html",
                        "/register/register",
                        "/register/register.html",

                        // 2. 排除登录和注册目录下的所有内容 (CSS/JS/图片等)
                        "/login/**",
                        "/register/**",

                        // 3. 排除所有路径下的静态资源文件 (最关键的部分，确保图片被排除)
                        "/**/*.css",     // 排除所有路径下的CSS文件
                        "/**/*.js",      // 排除所有路径下的JS文件
                        "/**/*.png",     // 排除PNG图片
                        "/**/*.jpg",     // 排除JPG图片
                        "/**/*.jpeg",    // 排除JPEG图片
                        "/**/*.gif",     // 排除GIF图片
                        "/**/*.ico",     // 排除favicon

                        // 4. 排除常见的静态资源目录 (如果图片放在 /static/images/ 或 /static/css/ 这种根目录下的文件夹)
                        "/images/**",
                        "/css/**",
                        "/js/**",
                        "/static/**"
                );

    }
}