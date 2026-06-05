package com.swp391.api.modules.home.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/home")
public class HomeController {

    @GetMapping
    public ResponseEntity<Map<String, Object>> getHomeData() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Welcome to Restaurant Management System");
        response.put("restaurantName", "Golden Spoon");
        response.put("description", "Manage reservations, orders, and restaurant operations efficiently.");
        response.put("featuredSections", List.of("Reservations", "Menu", "Promotions", "Reports"));

        return ResponseEntity.ok(response);
    }
}
