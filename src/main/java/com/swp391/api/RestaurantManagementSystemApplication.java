package com.swp391.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController // Khai báo luôn nó là Controller ở đây
public class RestaurantManagementSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(RestaurantManagementSystemApplication.class, args);
    }

    // Viết luôn cái cổng ra giao diện ở đây
    @GetMapping("/")
    public String index() {
        return "Hello";
    }
}