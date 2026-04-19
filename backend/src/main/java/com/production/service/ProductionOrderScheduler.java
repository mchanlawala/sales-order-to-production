package com.production.service;

import com.production.domain.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ProductionOrderScheduler {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final Pattern TIME_PATTERN = Pattern.compile("\\b([01]?[0-9]|2[0-3]):[0-5][0-9]\\b");
    private static final LocalTime DEFAULT_START = LocalTime.of(8, 0);
    private static final long LUNCH_BREAK_MINUTES = 30;

    public List<ProductionOrder> generateProductionOrders(ParsedExcelData data) {
        List<ProductionOrder> allProdOrders = new ArrayList<>();

        // Build maps
        Map<String, MaterialMaster> materialMap = new HashMap<>();
        for (MaterialMaster mm : data.getMaterialMasters()) materialMap.put(mm.getMaterial(), mm);

        Map<String, List<RoutingOperation>> routingMap = new HashMap<>();
        for (RoutingOperation ro : data.getRoutingOperations()) {
            routingMap.computeIfAbsent(ro.getMaterial(), k -> new ArrayList<>()).add(ro);
        }

        List<RoutingOperation> defaultRouting = routingMap.values().stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No routing data"));
        defaultRouting.sort(Comparator.comparingInt(RoutingOperation::getOperationId));

        Map<String, List<BillOfMaterial>> bomMap = new HashMap<>();
        for (BillOfMaterial bom : data.getBillsOfMaterial()) {
            bomMap.computeIfAbsent(bom.getMaterial(), k -> new ArrayList<>()).add(bom);
        }

        Map<String, WorkCenter> wcMap = data.getWorkCenters();

        // Sort sales orders
        List<SalesOrder> sortedSales = new ArrayList<>(data.getSalesOrders());
        sortedSales.sort(Comparator.comparingInt(SalesOrder::getPriority)
                .thenComparing(SalesOrder::getOrderReceived));

        // Next available time per work center (parallel capacity = 1)
        Map<String, LocalDateTime> wcNextAvailable = new HashMap<>();
        for (WorkCenter wc : wcMap.values()) {
            wcNextAvailable.put(wc.getWorkCenter(), LocalDateTime.now());
        }

        int prodCounter = 12501; // start from 1000012501

        for (SalesOrder so : sortedSales) {
            MaterialMaster mm = materialMap.get(so.getMaterial());
            if (mm == null) continue;

            List<RoutingOperation> routing = routingMap.get(so.getMaterial());
            if (routing == null) routing = defaultRouting;
            routing.sort(Comparator.comparingInt(RoutingOperation::getOperationId));

            List<Integer> splits = splitByLotAndCapacity(so.getQuantity(), mm);
            for (Integer qty : splits) {
                ProductionOrder po = new ProductionOrder();
                po.setProductionOrderId(String.format("10000%04d", prodCounter++));
                po.setSalesOrderId(so.getSalesOrderId());
                po.setMaterial(so.getMaterial());
                po.setPlannedQuantity(qty);
                po.setStatus("CREATED");

                scheduleOrder(po, so, routing, wcMap, wcNextAvailable);

                // Add components
                List<ProductionOrderComponent> components = new ArrayList<>();
                List<BillOfMaterial> boms = bomMap.get(so.getMaterial());
                if (boms != null) {
                    for (BillOfMaterial bom : boms) {
                        ProductionOrderComponent comp = new ProductionOrderComponent();
                        comp.setComponentCode(bom.getComponent());
                        comp.setComponentDescription(bom.getComponentDescription());
                        double required = qty * bom.getQuantityPerPanel() * (1 + bom.getScrapPercentage() / 100);
                        comp.setRequiredQuantity(required);
                        comp.setReservedQuantity(required);
                        comp.setUnit(bom.getUnit());
                        comp.setBatch(null);
                        comp.setWithdrawn(false);
                        components.add(comp);
                    }
                }
                po.setComponents(components);
                allProdOrders.add(po);
            }
        }
        return allProdOrders;
    }

    private List<Integer> splitByLotAndCapacity(int qty, MaterialMaster mm) {
        List<Integer> splits = new ArrayList<>();
        int lotSize = ("FO".equals(mm.getLotSize()) && mm.getFixedLotSize() != null)
                ? mm.getFixedLotSize() : qty;
        int maxBatch = 1000; // laminator max (you can make this WC-specific later)
        int effectiveSize = Math.min(lotSize, maxBatch);

        int fullLots = qty / effectiveSize;
        int remainder = qty % effectiveSize;

        for (int i = 0; i < fullLots; i++) splits.add(effectiveSize);
        if (remainder > 0) splits.add(remainder);
        return splits;
    }

    private List<Integer> splitByLotSize(int qty, MaterialMaster mm) {
        List<Integer> splits = new ArrayList<>();
        if ("FO".equals(mm.getLotSize()) && mm.getFixedLotSize() != null && mm.getFixedLotSize() > 0) {
            int lot = mm.getFixedLotSize();
            int full = qty / lot;
            int rem = qty % lot;
            for (int i = 0; i < full; i++) splits.add(lot);
            if (rem > 0) splits.add(rem);
        } else {
            splits.add(qty);
        }
        return splits;
    }

    /*private void scheduleOrder(ProductionOrder po, SalesOrder so,
                               List<RoutingOperation> routing,
                               Map<String, WorkCenter> wcMap,
                               Map<String, LocalDateTime> wcNextAvailable) {
        LocalDateTime current = so.getOrderReceived();
        List<ProductionOrderOperation> ops = new ArrayList<>();
        boolean anyDelayed = false;

        for (RoutingOperation op : routing) {
            WorkCenter wc = wcMap.get(op.getWorkCenter());
            if (wc == null) continue;

            LocalDateTime wcAvail = wcNextAvailable.getOrDefault(op.getWorkCenter(), current);
            LocalDateTime candidate = current.isAfter(wcAvail) ? current : wcAvail;
            LocalDateTime start = adjustToWorkingTime(candidate, wc);

            double procHours = po.getPlannedQuantity() * op.getProcessingHoursPerPanel();
            double totalHours = op.getSetupHours() + procHours + op.getTeardownHours();
            long totalMins = Math.round(totalHours * 60);

            LocalDateTime finish = addWorkingMinutes(start, totalMins, wc);
            finish = applyDowntime(start, finish, wc);

            boolean delayed = false;
            if (so.getRequestedDeliveryDate() != null && finish.isAfter(so.getRequestedDeliveryDate())) {
                delayed = true;
                anyDelayed = true;
            }

            ProductionOrderOperation o = new ProductionOrderOperation();
            o.setOperationId(op.getOperationId());
            o.setWorkCenter(op.getWorkCenter());
            o.setDescription(op.getDescription());
            o.setSetupHours(op.getSetupHours());
            o.setProcessingHours(procHours);
            o.setTotalHours(totalHours);
            o.setPlannedStart(start);
            o.setPlannedFinish(finish);
            o.setIsDelayed(delayed);
            o.setDelayReason(delayed ? "Due date breach at op " + op.getOperationId() : null);
            ops.add(o);

            wcNextAvailable.put(op.getWorkCenter(), finish.plusMinutes(5));
            current = finish;
        }

        po.setOperations(ops);
        if (!ops.isEmpty()) {
            po.setBasicStartDate(ops.get(0).getPlannedStart());
            po.setBasicFinishDate(ops.get(ops.size()-1).getPlannedFinish());
            po.setActualStartDate(po.getBasicStartDate());
            po.setActualFinishDate(po.getBasicFinishDate());
        }
        po.setStatus(anyDelayed ? "DELAYED" : "RELEASED");
        if (anyDelayed) po.setDelayReason("One or more operations delayed");
        po.setTotalLeadTimeMinutes(Duration.between(po.getBasicStartDate(), po.getBasicFinishDate()).toMinutes());
    }*/

    private void scheduleOrder(ProductionOrder po, SalesOrder so, List<RoutingOperation> routing,
                               Map<String, WorkCenter> wcMap, Map<String, LocalDateTime> wcNextAvailable) {

        LocalDateTime today = LocalDateTime.now(); // or hard-code 2026-04-18 if testing
        LocalDateTime requested = so.getRequestedDeliveryDate() != null
                ? so.getRequestedDeliveryDate()
                : today.plusDays(30);

        // Calculate total hours for this order (for initial backward estimate)
        double totalHours = routing.stream()
                .mapToDouble(op -> op.getSetupHours() +
                        (po.getPlannedQuantity() * op.getProcessingHoursPerPanel()) +
                        op.getTeardownHours())
                .sum();
        long totalMins = Math.round(totalHours * 60);

        // Backward schedule to find ideal start
        LocalDateTime idealFinish = requested;
        //log.warn("start subtractWorkingMinutes {}", idealFinish);
        LocalDateTime idealStart = subtractWorkingMinutes(idealFinish, totalMins, wcMap); // implement below
        //log.warn("end subtractWorkingMinutes {}", idealStart);

        // Forward if needed
        LocalDateTime effectiveStart = idealStart.isBefore(today) ? today : idealStart;
        String firstWc = routing.get(0).getWorkCenter();
        LocalDateTime wcAvail = wcNextAvailable.getOrDefault(firstWc, effectiveStart);
        effectiveStart = effectiveStart.isBefore(wcAvail) ? wcAvail : effectiveStart;

        LocalDateTime current = effectiveStart;
        List<ProductionOrderOperation> ops = new ArrayList<>();
        boolean anyDelayed = false;

        for (RoutingOperation op : routing) {
            WorkCenter wc = wcMap.get(op.getWorkCenter());
            if (wc == null) continue;

            LocalDateTime wcAvailOp = wcNextAvailable.getOrDefault(op.getWorkCenter(), current);
            LocalDateTime candidate = current.isAfter(wcAvailOp) ? current : wcAvailOp;
            LocalDateTime start = adjustToWorkingTime(candidate, wc);

            double procHours = po.getPlannedQuantity() * op.getProcessingHoursPerPanel();
            double totalOpHours = op.getSetupHours() + procHours + op.getTeardownHours();
            long opMins = Math.round(totalOpHours * 60);

            LocalDateTime finish = addWorkingMinutes(start, opMins, wc);
            finish = applyDowntime(start, finish, wc);

            boolean delayed = finish.isAfter(requested);
            if (delayed) anyDelayed = true;

            ProductionOrderOperation o = new ProductionOrderOperation();
            o.setOperationId(op.getOperationId());
            o.setWorkCenter(op.getWorkCenter());
            o.setDescription(op.getDescription());
            o.setSetupHours(op.getSetupHours());
            o.setProcessingHours(procHours);
            o.setTotalHours(totalOpHours);
            o.setPlannedStart(start);
            o.setPlannedFinish(finish);
            o.setIsDelayed(delayed);
            o.setDelayReason(delayed ? "Due date breach at op " + op.getOperationId() : null);
            ops.add(o);

            wcNextAvailable.put(op.getWorkCenter(), finish.plusMinutes(5));
            current = finish;
        }

        po.setOperations(ops);
        List<Inspection> inspections = generateInspections(ops, po.getPlannedQuantity());
        po.setInspections(inspections);
        po.setBasicStartDate(idealStart);
        po.setBasicFinishDate(idealFinish);
        po.setActualStartDate(effectiveStart);
        po.setActualFinishDate(current);
        po.setStatus(anyDelayed ? "DELAYED" : "RELEASED");
        po.setTotalLeadTimeMinutes(Duration.between(effectiveStart, current).toMinutes());
        if (anyDelayed) po.setDelayReason("One or more operations delayed");
    }

    // NEW METHOD - Backward scheduling helper
    // ==================== BACKWARD SCHEDULING - subtractWorkingMinutes ====================
    // ==================== FIXED subtractWorkingMinutes ====================
    private LocalDateTime subtractWorkingMinutes(LocalDateTime endTime, long minutesToSubtract,
                                                 Map<String, WorkCenter> wcMap) {
        if (minutesToSubtract <= 0) return endTime;

        WorkCenter wc = wcMap.values().iterator().next();
        if (wc == null) return endTime;

        log.warn("start subtractWorkingMinutes from {}", endTime);

        LocalDateTime cur = adjustToWorkingTime(endTime, wc);
        long remaining = minutesToSubtract;
        int safety = 0;

        while (remaining > 0 && safety++ < 20000) {   // higher limit for safety during debug
            LocalTime t = cur.toLocalTime();
            LocalTime shiftStart = getShiftStart(cur.toLocalDate(), wc);
            LocalTime shiftEnd = getShiftEnd(cur.toLocalDate(), wc);

            // Find the latest point we can start counting backward from (shift start or after last break)
            LocalTime segmentStart = shiftStart;

            // Look for the most recent break END that is BEFORE current time
            for (Break brk : wc.getBreaks()) {
                LocalTime breakEnd = parseTime(brk.getBreakEnd());
                if (breakEnd.isBefore(t) && breakEnd.isAfter(segmentStart)) {
                    segmentStart = breakEnd;   // latest possible start point
                }
            }

            long availableInSegment = Duration.between(segmentStart, t).toMinutes();

            if (availableInSegment <= 5) {   // small threshold to avoid tiny segments
                // No usable time left today → move to previous day's shift END
                cur = cur.minusDays(1).with(getShiftEnd(cur.minusDays(1).toLocalDate(), wc));
                cur = adjustToWorkingTime(cur, wc);
                continue;
            }

            // Consume minutes
            if (remaining <= availableInSegment) {
                cur = cur.minusMinutes(remaining);
                remaining = 0;
            } else {
                remaining -= availableInSegment;
                cur = cur.with(segmentStart);

                // If we landed on a break end, jump before the break
                boolean skippedBreak = false;
                for (Break brk : wc.getBreaks()) {
                    if (segmentStart.equals(parseTime(brk.getBreakEnd()))) {
                        cur = cur.with(parseTime(brk.getBreakStart()));
                        skippedBreak = true;
                        break;
                    }
                }
                if (!skippedBreak) {
                    // Reached shift start → go to previous day
                    cur = cur.minusDays(1).with(getShiftEnd(cur.minusDays(1).toLocalDate(), wc));
                }
                cur = adjustToWorkingTime(cur, wc);
            }
        }

        if (safety >= 20000) {
            log.warn("subtractWorkingMinutes hit safety limit for {} minutes", minutesToSubtract);
        }

        log.warn("end subtractWorkingMinutes at {}", cur);
        return cur;
    }

    // ---------- Time helpers ----------
    private LocalTime parseTime(String s) {
        if (s == null || s.isBlank()) return DEFAULT_START;
        var m = TIME_PATTERN.matcher(s);
        if (m.find()) {
            try {
                return LocalTime.parse(m.group(), TIME_FMT);
            } catch (Exception ignored) {
            }
        }
        try {
            return LocalTime.parse(s, TIME_FMT);
        } catch (Exception e) {
            return DEFAULT_START;
        }
    }

    /*private LocalDateTime adjustToWorkingTime(LocalDateTime dt, WorkCenter wc) {
        LocalDateTime cur = dt;
        int iter = 0;
        while (iter++ < 20) {
            LocalTime t = cur.toLocalTime();
            LocalTime start = getShiftStart(cur.toLocalDate(), wc);
            LocalTime end = getShiftEnd(cur.toLocalDate(), wc);

            if (t.isBefore(start)) {
                cur = cur.with(start);
                continue;
            }
            if (!t.isBefore(end)) {
                cur = cur.plusDays(1).with(start);
                continue;
            }
            boolean inBreak = false;
            for (Break brk : wc.getBreaks()) {
                LocalTime bs = parseTime(brk.getBreakStart());
                LocalTime be = parseTime(brk.getBreakEnd());
                if (!t.isBefore(bs) && t.isBefore(be)) {
                    cur = cur.with(be);
                    inBreak = true;
                    break;
                }
            }
            if (!inBreak) break;
        }
        return cur;
    }*/

    // ==================== adjustToWorkingTime ====================
    private LocalDateTime adjustToWorkingTime(LocalDateTime dt, WorkCenter wc) {
        LocalDateTime cur = dt;
        int iter = 0;
        while (iter++ < 100) {
            LocalTime t = cur.toLocalTime();
            log.warn("start adjustToWorkingTime getShiftStart at {} , {}", cur, wc);
            LocalTime shiftStart = getShiftStart(cur.toLocalDate(), wc);
            log.warn("end shiftStart at {}", shiftStart);
            log.warn("start adjustToWorkingTime getShiftEnd at {} , {}", cur, wc);
            LocalTime shiftEnd = getShiftEnd(cur.toLocalDate(), wc);
            log.warn("end getShiftEnd at {}", shiftEnd);

            if (t.isBefore(shiftStart)) {
                cur = cur.with(shiftStart);
                continue;
            }
            if (t.isAfter(shiftEnd)) {
                cur = cur.minusDays(1).with(shiftEnd);
                continue;
            }

            if (isInBreak(t, wc)) {
                for (Break brk : wc.getBreaks()) {
                    LocalTime bs = parseTime(brk.getBreakStart());
                    LocalTime be = parseTime(brk.getBreakEnd());
                    if (!t.isBefore(bs) && t.isBefore(be)) {
                        cur = cur.with(be);
                        break;
                    }
                }
                continue;
            }
            break; // valid working time
        }
        return cur;
    }

    private boolean isInBreak(LocalTime t, WorkCenter wc) {
        for (Break brk : wc.getBreaks()) {
            LocalTime bs = parseTime(brk.getBreakStart());
            LocalTime be = parseTime(brk.getBreakEnd());
            if (!t.isBefore(bs) && t.isBefore(be)) return true;
        }
        return false;
    }

    /*private LocalDateTime addWorkingMinutes(LocalDateTime start, long minutes, WorkCenter wc) {
        if (minutes <= 0) return start;
        LocalDateTime cur = adjustToWorkingTime(start, wc);
        long remaining = minutes;
        int safety = 0;
        while (remaining > 0 && safety++ < 10000) {
            LocalTime t = cur.toLocalTime();
            LocalTime shiftEnd = getShiftEnd(cur.toLocalDate(), wc);
            LocalTime nextBreakStart = null;
            LocalTime nextBreakEnd = null;
            for (Break brk : wc.getBreaks()) {
                LocalTime bs = parseTime(brk.getBreakStart());
                if (t.isBefore(bs) && (nextBreakStart == null || bs.isBefore(nextBreakStart))) {
                    nextBreakStart = bs;
                    nextBreakEnd = parseTime(brk.getBreakEnd());
                }
            }
            LocalTime segmentEnd = (nextBreakStart != null && nextBreakStart.isBefore(shiftEnd)) ? nextBreakStart : shiftEnd;
            long segMinutes = Duration.between(t, segmentEnd).toMinutes();
            if (segMinutes <= 0) {
                cur = cur.plusDays(1).with(getShiftStart(cur.plusDays(1).toLocalDate(), wc));
                cur = adjustToWorkingTime(cur, wc);
                continue;
            }
            if (remaining <= segMinutes) {
                cur = cur.plusMinutes(remaining);
                remaining = 0;
            } else {
                remaining -= segMinutes;
                cur = cur.with(segmentEnd);
                if (nextBreakStart != null && t.isBefore(nextBreakStart)) {
                    cur = cur.with(nextBreakEnd);
                } else {
                    cur = cur.plusDays(1).with(getShiftStart(cur.plusDays(1).toLocalDate(), wc));
                }
                cur = adjustToWorkingTime(cur, wc);
            }
        }
        return cur;
    }*/

    // ==================== MAIN METHOD - addWorkingMinutes ====================
    private LocalDateTime addWorkingMinutes(LocalDateTime start, long minutes, WorkCenter wc) {
        if (minutes <= 0) return start;

        //log.warn("start addWorkingMinutes LocalDateTime {} minutes at {}", start, minutes);
        LocalDateTime cur = adjustToWorkingTime(start, wc);
        long remaining = minutes;
        int safety = 0;

        while (remaining > 0 && safety++ < 10000) {   // increased safety for debugging
            LocalTime t = cur.toLocalTime();
            LocalTime shiftEndToday = getShiftEnd(cur.toLocalDate(), wc);

            // Find next break after current time
            LocalTime nextBreakStart = null;
            LocalTime nextBreakEnd = null;
            for (Break brk : wc.getBreaks()) {
                LocalTime bs = parseTime(brk.getBreakStart());
                if (!t.isAfter(bs) && (nextBreakStart == null || bs.isBefore(nextBreakStart))) {
                    nextBreakStart = bs;
                    nextBreakEnd = parseTime(brk.getBreakEnd());
                }
            }

            LocalTime segmentEnd = (nextBreakStart != null && nextBreakStart.isBefore(shiftEndToday))
                    ? nextBreakStart : shiftEndToday;

            long availableInSegment = Duration.between(t, segmentEnd).toMinutes();

            if (availableInSegment <= 0) {
                // No more work today → move to next working day
                cur = cur.plusDays(1).with(getShiftStart(cur.plusDays(1).toLocalDate(), wc));
                cur = adjustToWorkingTime(cur, wc);
                continue;
            }

            if (remaining <= availableInSegment) {
                cur = cur.plusMinutes(remaining);
                remaining = 0;
            } else {
                remaining -= availableInSegment;
                cur = cur.with(segmentEnd);

                // If we hit a break, skip over it
                if (nextBreakStart != null && t.isBefore(nextBreakStart) && segmentEnd.equals(nextBreakStart)) {
                    cur = cur.with(nextBreakEnd);
                } else {
                    // Hit shift end → go to next day
                    cur = cur.plusDays(1).with(getShiftStart(cur.plusDays(1).toLocalDate(), wc));
                }
                cur = adjustToWorkingTime(cur, wc);
            }
        }

        /*if (safety >= 10000) {
            log.warn("addWorkingMinutes hit safety limit for {} minutes at {}", minutes, start);
        }*/

        //log.warn("end addWorkingMinutes LocalDateTime {} ", cur);
        return cur;
    }

    private LocalDateTime applyDowntime(LocalDateTime start, LocalDateTime end, WorkCenter wc) {
        LocalDateTime result = end;
        for (DowntimeEvent dt : wc.getDowntimeEvents()) {
            if (dt.getStartDateTime().isBefore(result) && dt.getEndDateTime().isAfter(start)) {
                LocalDateTime effStart = dt.getStartDateTime().isAfter(start) ? dt.getStartDateTime() : start;
                long delay = Duration.between(effStart, dt.getEndDateTime()).toMinutes();
                result = result.plusMinutes(delay);
            }
        }
        return result;
    }

    // ==================== Shift Helpers ====================
    private LocalTime getShiftStart(LocalDate date, WorkCenter wc) {
        for (ShiftOverride ov : wc.getShiftOverrides()) {
            if (ov.getDate().equals(date)) return parseTime(ov.getStartTime());
        }
        return wc.getDefaultStart() != null ? parseTime(wc.getDefaultStart()) : LocalTime.of(6, 0);
    }

    private LocalTime getShiftEnd(LocalDate date, WorkCenter wc) {
        for (ShiftOverride ov : wc.getShiftOverrides()) {
            if (ov.getDate().equals(date)) return parseTime(ov.getEndTime());
        }

        int hours = wc.getAvailableHoursPerDay() != null && wc.getAvailableHoursPerDay() > 0
                ? wc.getAvailableHoursPerDay() : 16;

        return getShiftStart(date, wc).plusHours(hours);
    }

    private List<Inspection> generateInspections(List<ProductionOrderOperation> operations, int plannedQty) {
        List<Inspection> list = new ArrayList<>();
        int inspCounter = 1;
        for (ProductionOrderOperation op : operations) {
            Inspection insp = null;
            switch (op.getOperationId()) {
                case 25: // Pre-Lamination EL Test
                    insp = new Inspection();
                    insp.setInspectionId("INSP-" + String.format("%03d", inspCounter++));
                    insp.setOperationId(op.getOperationId());
                    insp.setType("EL_TEST");
                    insp.setDescription("Pre-Lamination Electroluminescence Test");
                    insp.setRequired(true);
                    insp.setSpecification(Map.of(
                            "criteria", "No micro-cracks, no broken cells, no soldering defects",
                            "acceptanceThreshold", "0 defects"
                    ));
                    insp.setScheduledTime(op.getPlannedFinish());
                    insp.setStatus("PENDING");
                    break;
                case 45: // Flash Test
                    insp = new Inspection();
                    insp.setInspectionId("INSP-" + String.format("%03d", inspCounter++));
                    insp.setOperationId(op.getOperationId());
                    insp.setType("FLASH_TEST");
                    insp.setDescription("Performance Flash Test (I-V curve)");
                    insp.setRequired(true);
                    // extract wattage from material? For simplicity, use generic range
                    insp.setSpecification(Map.of(
                            "minPower", 540,
                            "maxPower", 550,
                            "unit", "W",
                            "tolerancePercent", 2
                    ));
                    insp.setScheduledTime(op.getPlannedFinish());
                    insp.setStatus("PENDING");
                    break;
                case 50: // Final EL Test
                    insp = new Inspection();
                    insp.setInspectionId("INSP-" + String.format("%03d", inspCounter++));
                    insp.setOperationId(op.getOperationId());
                    insp.setType("EL_TEST_FINAL");
                    insp.setDescription("Final EL Test after framing");
                    insp.setRequired(true);
                    insp.setSpecification(Map.of(
                            "criteria", "No new micro-cracks, no handling damage"
                    ));
                    insp.setScheduledTime(op.getPlannedFinish());
                    insp.setStatus("PENDING");
                    break;
                default:
                    // no inspection
                    break;
            }
            if (insp != null) {
                list.add(insp);
            }
        }
        return list;
    }
}