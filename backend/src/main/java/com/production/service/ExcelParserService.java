package com.production.service;

import com.production.domain.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class ExcelParserService {

    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public ParsedExcelData parse(MultipartFile file) throws Exception {
        try (InputStream is = file.getInputStream(); Workbook wb = new XSSFWorkbook(is)) {
            ParsedExcelData data = new ParsedExcelData();
            data.setMaterialMasters(parseMaterialMasters(wb));
            data.setBillsOfMaterial(parseBillsOfMaterial(wb));
            data.setRoutingOperations(parseRoutingOperations(wb));
            data.setWorkCenters(parseWorkCenters(wb));
            enrichWorkCenters(wb, data.getWorkCenters());
            data.setSalesOrders(parseSalesOrders(wb));
            data.setCustomerMasters(parseCustomerMasters(wb));
            return data;
        }
    }

    private List<MaterialMaster> parseMaterialMasters(Workbook wb) {
        Sheet sheet = wb.getSheet("MaterialMaster");
        if (sheet == null) throw new RuntimeException("Sheet 'MaterialMaster' not found");
        List<MaterialMaster> list = new ArrayList<>();
        Iterator<Row> rows = sheet.rowIterator();
        if (rows.hasNext()) rows.next(); // header
        while (rows.hasNext()) {
            Row row = rows.next();
            MaterialMaster mm = new MaterialMaster();
            mm.setMaterial(getCellString(row, 0));
            mm.setDescription(getCellString(row, 1));
            mm.setBaseUnit(getCellString(row, 2));
            mm.setMrpType(getCellString(row, 3));
            mm.setLotSize(getCellString(row, 4));
            mm.setFixedLotSize((int) getCellNumeric(row, 5));
            mm.setProcurementType(getCellString(row, 6));
            mm.setPlant(getCellString(row, 7));
            list.add(mm);
        }
        return list;
    }

    private List<BillOfMaterial> parseBillsOfMaterial(Workbook wb) {
        Sheet sheet = wb.getSheet("BOM");
        if (sheet == null) throw new RuntimeException("Sheet 'BOM' not found");
        List<BillOfMaterial> list = new ArrayList<>();
        Iterator<Row> rows = sheet.rowIterator();
        if (rows.hasNext()) rows.next();
        while (rows.hasNext()) {
            Row row = rows.next();
            BillOfMaterial bom = new BillOfMaterial();
            bom.setMaterial(getCellString(row, 0));
            bom.setComponent(getCellString(row, 1));
            bom.setComponentDescription(getCellString(row, 2));
            bom.setQuantityPerPanel(getCellNumeric(row, 3));
            bom.setUnit(getCellString(row, 4));
            bom.setScrapPercentage(getCellNumeric(row, 5));
            list.add(bom);
        }
        return list;
    }

    private List<RoutingOperation> parseRoutingOperations(Workbook wb) {
        Sheet sheet = wb.getSheet("Routing");
        if (sheet == null) throw new RuntimeException("Sheet 'Routing' not found");
        List<RoutingOperation> list = new ArrayList<>();
        Iterator<Row> rows = sheet.rowIterator();
        if (rows.hasNext()) rows.next();
        while (rows.hasNext()) {
            Row row = rows.next();
            RoutingOperation ro = new RoutingOperation();
            ro.setMaterial(getCellString(row, 0));
            ro.setOperationId((int) getCellNumeric(row, 1));
            ro.setWorkCenter(getCellString(row, 2));
            ro.setDescription(getCellString(row, 3));
            ro.setSetupHours(getCellNumeric(row, 4));
            ro.setProcessingHoursPerPanel(getCellNumeric(row, 5));
            ro.setTeardownHours(getCellNumeric(row, 6));
            list.add(ro);
        }
        return list;
    }

    private Map<String, WorkCenter> parseWorkCenters(Workbook wb) {
        Sheet sheet = wb.getSheet("WorkCenters");
        if (sheet == null) throw new RuntimeException("Sheet 'WorkCenters' not found");

        Map<String, WorkCenter> map = new LinkedHashMap<>();
        Iterator<Row> rows = sheet.rowIterator();
        if (rows.hasNext()) rows.next(); // skip header

        while (rows.hasNext()) {
            Row row = rows.next();
            WorkCenter wc = new WorkCenter();
            wc.setWorkCenter(getCellString(row, 0));
            wc.setPlant(getCellString(row, 1));
            wc.setAvailableHoursPerDay((int) getCellNumeric(row, 2));   // Column C = 16
            wc.setShifts((int) getCellNumeric(row, 3));
            wc.setBreakHours(getCellNumeric(row, 4));

            // Set sensible defaults
            wc.setDefaultStart("06:00");
            wc.setDefaultEnd("22:00");   // or calculate from available hours

            map.put(wc.getWorkCenter(), wc);
        }
        return map;
    }

    private void enrichWorkCenters(Workbook wb, Map<String, WorkCenter> wcMap) {
        // Shifts
        Sheet shiftsSheet = wb.getSheet("Shifts");
        if (shiftsSheet != null) {
            Iterator<Row> rows = shiftsSheet.rowIterator();
            if (rows.hasNext()) rows.next();
            while (rows.hasNext()) {
                Row row = rows.next();
                String wc = getCellString(row, 0);
                if (wcMap.containsKey(wc)) {
                    ShiftOverride ov = new ShiftOverride();
                    ov.setDate(LocalDate.parse(getCellString(row, 1), DATE_FMT));
                    ov.setStartTime(getCellString(row, 2));
                    ov.setEndTime(getCellString(row, 3));
                    wcMap.get(wc).getShiftOverrides().add(ov);
                }
            }
        }
        // Breaks
        Sheet breaksSheet = wb.getSheet("Breaks");
        if (breaksSheet != null) {
            Iterator<Row> rows = breaksSheet.rowIterator();
            if (rows.hasNext()) rows.next();
            while (rows.hasNext()) {
                Row row = rows.next();
                String wc = getCellString(row, 0);
                if (wcMap.containsKey(wc)) {
                    Break brk = new Break();
                    brk.setBreakStart(getCellString(row, 1));
                    brk.setBreakEnd(getCellString(row, 2));
                    brk.setDailyOccurrence((int) getCellNumeric(row, 3));
                    wcMap.get(wc).getBreaks().add(brk);
                }
            }
        }
        // Downtime
        Sheet dtSheet = wb.getSheet("DowntimeEvents");
        if (dtSheet != null) {
            Iterator<Row> rows = dtSheet.rowIterator();
            if (rows.hasNext()) rows.next();
            while (rows.hasNext()) {
                Row row = rows.next();
                String wc = getCellString(row, 1);
                if (wcMap.containsKey(wc)) {
                    DowntimeEvent dt = new DowntimeEvent();
                    dt.setEventId(getCellString(row, 0));
                    dt.setWorkCenter(wc);
                    dt.setStartDateTime(parseLocalDateTime(getCellString(row, 2)));
                    dt.setEndDateTime(parseLocalDateTime(getCellString(row, 3)));
                    dt.setReason(getCellString(row, 4));
                    dt.setType(getCellString(row, 5));
                    wcMap.get(wc).getDowntimeEvents().add(dt);
                }
            }
        }
    }

    private List<SalesOrder> parseSalesOrders(Workbook wb) {
        Sheet sheet = wb.getSheet("SalesOrders");
        if (sheet == null) throw new RuntimeException("Sheet 'SalesOrders' not found");
        List<SalesOrder> list = new ArrayList<>();
        Iterator<Row> rows = sheet.rowIterator();
        if (rows.hasNext()) rows.next();
        while (rows.hasNext()) {
            Row row = rows.next();
            SalesOrder so = new SalesOrder();
            so.setSalesOrderId(getCellString(row, 0));
            so.setCustomerCode(getCellString(row, 1));
            so.setMaterial(getCellString(row, 2));
            so.setQuantity((int) getCellNumeric(row, 3));
            so.setRequestedDeliveryDate(parseExcelDate(row, 4));
            so.setOrderReceived(parseExcelDate(row, 5));
            so.setVariantConfig(getCellString(row, 6));
            so.setPriority((int) getCellNumeric(row, 7));
            so.setStatus("PENDING");
            list.add(so);
        }
        return list;
    }

    // Add this helper
    private LocalDateTime parseExcelDate(Row row, int col) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return LocalDateTime.now();

        if (cell.getCellType() == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue();
            }
            // Excel serial number (handles 46162.708... → 2026-05-20)
            double serial = cell.getNumericCellValue();
            return LocalDateTime.of(1899, 12, 30, 0, 0)
                    .plusDays((long) serial)
                    .plusSeconds((long) ((serial - Math.floor(serial)) * 86400));
        }

        // fallback string
        String s = getCellString(row, col);
        try {
            return LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(s, DATE_TIME_FMT);
            } catch (Exception ex) {
                return LocalDateTime.now();
            }
        }
    }

    private List<CustomerMaster> parseCustomerMasters(Workbook wb) {
        Sheet sheet = wb.getSheet("CustomerMaster");
        if (sheet == null) return new ArrayList<>();
        List<CustomerMaster> list = new ArrayList<>();
        Iterator<Row> rows = sheet.rowIterator();
        if (rows.hasNext()) rows.next();
        while (rows.hasNext()) {
            Row row = rows.next();
            CustomerMaster cm = new CustomerMaster();
            cm.setCustomerCode(getCellString(row, 0));
            cm.setName(getCellString(row, 1));
            cm.setCountry(getCellString(row, 2));
            cm.setPaymentTermsDays((int) getCellNumeric(row, 3));
            cm.setPricingGroup(getCellString(row, 4));
            list.add(cm);
        }
        return list;
    }

    private LocalDateTime parseLocalDateTime(String s) {
        if (s == null || s.isBlank()) return LocalDateTime.now();
        try {
            return LocalDateTime.parse(s, DATE_TIME_FMT);
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    private String getCellString(Row row, int col) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toString();
                } else {
                    double val = cell.getNumericCellValue();
                    yield (val == Math.floor(val)) ? String.valueOf((long) val) : String.valueOf(val);
                }
            }
            default -> "";
        };
    }

    private double getCellNumeric(Row row, int col) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return 0;
        return switch (cell.getCellType()) {
            case NUMERIC -> cell.getNumericCellValue();
            case STRING -> {
                try {
                    yield Double.parseDouble(cell.getStringCellValue().trim());
                } catch (NumberFormatException e) {
                    yield 0;
                }
            }
            default -> 0;
        };
    }
}