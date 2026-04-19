export interface ProductionOrderOperation {
  operationId: number;
  workCenter: string;
  description: string;
  setupHours: number;
  processingHours: number;
  totalHours: number;
  plannedStart: string;
  plannedFinish: string;
  isDelayed: boolean;
  delayReason: string | null;
}

export interface ProductionOrderComponent {
  componentCode: string;
  componentDescription: string;
  requiredQuantity: number;
  reservedQuantity: number;
  unit: string;
  batch: string | null;
  withdrawn: boolean;
}

export interface ProductionOrder {
  productionOrderId: string;
  salesOrderId: string;
  material: string;
  plannedQuantity: number;
  basicStartDate: string;
  basicFinishDate: string;
  actualStartDate: string;
  actualFinishDate: string;
  status: string; // CREATED, RELEASED, DELAYED
  operations: ProductionOrderOperation[];
  components: ProductionOrderComponent[];
  inspections?: Inspection[];
}

export interface DashboardData {
  ordersByStatus: { [key: string]: number };
  totalOrders: number;
  totalPanels: number;
  recentOrders: ProductionOrder[];
}

export interface Inspection {
  inspectionId: string;
  operationId: number;
  type: string;
  description: string;
  required: boolean;
  specification: any;           // flexible object
  result: any;
  status: string;               // PENDING, IN_PROGRESS, PASSED, FAILED, SKIPPED
  scheduledTime: string;
  completedTime: string | null;
  measuredValue: any;
  judgment: string;             // PASS, FAIL, REWORK
  remarks: string | null;
}

export interface InspectionUpdateRequest {
  measuredValue: any;
  judgment: string;
  status: string;
  remarks: string | null;
}