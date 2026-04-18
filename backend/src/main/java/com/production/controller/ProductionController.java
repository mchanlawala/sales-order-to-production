package com.production.controller;

import com.production.domain.*;
import com.production.service.ExcelParserService;
import com.production.service.ProductionOrderScheduler;
import com.production.agent.SchedulingAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/production")
@CrossOrigin(origins = {"http://localhost:4200", "http://localhost:3000"})
@RequiredArgsConstructor
public class ProductionController {

    private final ExcelParserService parser;
    private final ProductionOrderScheduler scheduler;
    private final SchedulingAgent aiAgent;

    private List<ProductionOrder> lastProductionOrders;
    private String lastAiInsights;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadAndSchedule(@RequestParam("file") MultipartFile file) {
        try {
            ParsedExcelData data = parser.parse(file);
            List<ProductionOrder> prodOrders = scheduler.generateProductionOrders(data);
            lastProductionOrders = prodOrders;
            lastAiInsights = aiAgent.analyseProductionOrders(prodOrders);

            Map<String, Object> response = new HashMap<>();
            response.put("productionOrders", prodOrders);
            response.put("aiInsights", lastAiInsights);
            response.put("summary", buildSummary(prodOrders));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Upload error", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/orders")
    public ResponseEntity<List<ProductionOrder>> getProductionOrders() {
        return ResponseEntity.ok(lastProductionOrders != null ? lastProductionOrders : List.of());
    }

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        if (lastProductionOrders == null) {
            return ResponseEntity.ok(Map.of());
        }
        Map<String, Object> dash = new HashMap<>();
        dash.put("ordersByStatus", lastProductionOrders.stream()
                .collect(Collectors.groupingBy(ProductionOrder::getStatus, Collectors.counting())));
        dash.put("totalOrders", lastProductionOrders.size());
        dash.put("totalPanels", lastProductionOrders.stream().mapToInt(ProductionOrder::getPlannedQuantity).sum());
        dash.put("recentOrders", lastProductionOrders.stream().limit(5).collect(Collectors.toList()));
        return ResponseEntity.ok(dash);
    }

    @GetMapping("/insights")
    public ResponseEntity<Map<String, String>> getInsights() {
        return ResponseEntity.ok(Map.of("insights", lastAiInsights != null ? lastAiInsights : "No insights"));
    }

    private Map<String, Object> buildSummary(List<ProductionOrder> orders) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalProductionOrders", orders.size());
        summary.put("totalPanels", orders.stream().mapToInt(ProductionOrder::getPlannedQuantity).sum());
        long delayed = orders.stream().filter(po -> "DELAYED".equals(po.getStatus())).count();
        summary.put("delayedOrders", delayed);
        return summary;
    }
}