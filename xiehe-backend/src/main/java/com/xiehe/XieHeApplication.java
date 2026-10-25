package com.xiehe;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 协合XieHe 协作文档云协作平台 - 启动类
 * 启动后访问: http://localhost:8080
 */
@SpringBootApplication
@MapperScan("com.xiehe.mapper")   // 扫描 Mapper 接口包,自动注册到 Spring 容器
public class XieHeApplication {

    public static void main(String[] args) {
        SpringApplication.run(XieHeApplication.class, args);
    }
}
