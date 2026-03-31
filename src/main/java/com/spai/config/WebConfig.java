package com.spai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 配置/uploads/路径访问项目根目录下的 uploads 文件夹
        String uploadDir = System.getProperty("user.dir") + "/uploads";
        Path uploadPath = Paths.get(uploadDir);
        
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadPath.toAbsolutePath() + "/");
    }
}
