package com.fountainhub.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 健康检查控制器（R-004 从 HomeController 搬过来的）。
 *
 * 【为什么单独一个类】
 *   /ping 是给运维/监控用的探活接口，和「首页」没有任何关系。
 *   搬出来之后 HomeController 只管首页，类名和职责对得上。
 *   **URL 完全没变**，还是 /ping。
 */
@Controller
public class HealthController {

    /**
     * 健康检查接口：用来确认服务活着。
     *
     * ⚠️ `@ResponseBody` 很关键，少了它这个接口必然报 500：
     *   `@Controller` 的默认规则是「方法返回的字符串 = 要渲染的页面名字」，
     *   所以返回 "pong" 时，Spring 会去找 templates/pong.html，找不到就报错。
     *   加上 `@ResponseBody` 后，Spring 才知道 "pong" 是要原样写进 HTTP 响应的正文。
     *   （如果整个类改用 `@RestController`，则所有方法都自带 `@ResponseBody`）
     */
    @GetMapping("/ping")
    @ResponseBody
    public String ping() {
        return "pong";
    }
}
