package com.xiehe.config;

import com.xiehe.interceptor.AuthInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

/**
 * Web MVC 配置
 * 1. 跨域(CORS): 仅允许配置的前端来源访问(默认本地开发服务器5173),
 *    不再使用"允许所有来源",防止跨站请求滥用
 * 2. 静态资源映射: 把磁盘上的上传目录映射成 /uploads/** 网址,
 *    这样浏览器可以直接访问 http://localhost:8080/uploads/xxx 查看文件
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${xiehe.upload-dir}")
    private String uploadDir;

    /** 允许跨域访问的来源(逗号分隔),可在 application.yml 中配置 */
    @Value("${xiehe.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
    private String allowedOrigins;

    @Autowired
    private AuthInterceptor authInterceptor;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                // 仅放行配置的白名单来源,避免任意站点携带凭据跨域调用
                .allowedOriginPatterns(allowedOrigins.split(","))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")                       // 拦截所有 /api 接口
                .excludePathPatterns("/api/auth/login",           // 白名单: 登录
                                     "/api/auth/register",        // 白名单: 注册
                                     "/api/share/**");            // 白名单: 公开分享链接(免登录)
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 磁盘路径 -> URL 映射: 访问 /uploads/** 时去磁盘目录找文件
        String location = "file:" + uploadDir.replace("\\", "/") + "/";
        // 确保目录存在,否则 Spring 会报路径不存在
        File dir = new File(uploadDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location);
    }
}
