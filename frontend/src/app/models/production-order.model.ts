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
}

export interface DashboardData {
  ordersByStatus: { [key: string]: number };
  totalOrders: number;
  totalPanels: number;
  recentOrders: ProductionOrder[];
}