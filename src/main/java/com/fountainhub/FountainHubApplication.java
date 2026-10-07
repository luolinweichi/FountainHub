package com.fountainhub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FountainHub 社团管理系统 · 启动类
 *
 * 运行方式：
 *   1. 在 IDEA 里右键本文件 → Run 'FountainHubApplication'
 *   2. 或命令行：mvn spring-boot:run
 *
 * 启动后访问：http://localhost:8080
 */
@SpringBootApplication
public class FountainHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(FountainHubApplication.class, args);
        System.out.println("""

                ============================================
                  FountainHub 启动成功！
                  请在浏览器打开： http://localhost:8080
                ============================================
                """);
    }
}
