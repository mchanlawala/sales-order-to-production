package com.production.domain;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class ParsedExcelData {
    private List<MaterialMaster> materialMasters;
    private List<BillOfMaterial> billsOfMaterial;
    private List<RoutingOperation> routingOperations;
    private Map<String, WorkCenter> workCenters;
    private List<SalesOrder> salesOrders;
    private List<CustomerMaster> customerMasters;
}