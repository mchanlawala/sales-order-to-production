package com.production.controller;

import com.production.agent.SchedulingAgent;
import com.production.domain.Inspection;
import com.production.domain.InspectionUpdateRequest;
import com.production.domain.ParsedExcelData;
import com.production.domain.ProductionOrder;
import com.production.service.ExcelParserService;
import com.production.service.ProductionOrderScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    @PutMapping("/orders/{productionOrderId}/inspections/{inspectionId}")
    public ResponseEntity<?> updateInspectionResult(
            @PathVariable String productionOrderId,
            @PathVariable String inspectionId,
            @RequestBody InspectionUpdateRequest update) {

        ProductionOrder order = lastProductionOrders.stream()
                .filter(o -> o.getProductionOrderId().equals(productionOrderId))
                .findFirst()
                .orElse(null);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }

        Inspection insp = order.getInspections().stream()
                .filter(i -> i.getInspectionId().equals(inspectionId))
                .findFirst()
                .orElse(null);
        if (insp == null) {
            return ResponseEntity.notFound().build();
        }

        // Update fields from request
        insp.setMeasuredValue(update.getMeasuredValue());
        insp.setJudgment(update.getJudgment());
        insp.setStatus(update.getStatus());
        insp.setCompletedTime(LocalDateTime.now());
        insp.setRemarks(update.getRemarks());

        // Optional: if a required inspection fails, mark the production order as BLOCKED
        if (Boolean.TRUE.equals(insp.getRequired()) && "FAIL".equals(insp.getJudgment())) {
            order.setStatus("BLOCKED");
        }

        // You could also re-evaluate overall order status here
        return ResponseEntity.ok(insp);
    }
}