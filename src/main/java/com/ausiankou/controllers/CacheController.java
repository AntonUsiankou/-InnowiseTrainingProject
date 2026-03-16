package com.ausiankou.controllers;

import com.ausiankou.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/cache")
@Slf4j
public class CacheController {

    private final CacheManager cacheManager;
    private final UserService userService;

    @Autowired
    public CacheController(CacheManager cacheManager, UserService userService) {
        this.cacheManager = cacheManager;
        this.userService = userService;
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();

        cacheManager.getCacheNames().forEach(cacheName -> {
            Map<String, Object> cacheStats = new HashMap<>();
            cacheStats.put("name", cacheName);
            cacheStats.put("status", "active");
            stats.put(cacheName, cacheStats);
        });

        return ResponseEntity.ok(stats);
    }

    @DeleteMapping("/clear")
    public ResponseEntity<String> clearAllCaches() {
        userService.clearAllCaches();
        return ResponseEntity.ok("Все кэши очищены");
    }

    @DeleteMapping("/clear/{cacheName}")
    public ResponseEntity<String> clearCache(@PathVariable String cacheName) {
        var cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
            return ResponseEntity.ok("Кэш " + cacheName + " очищен");
        }
        return ResponseEntity.badRequest().body("Кэш " + cacheName + " не найден");
    }
}
