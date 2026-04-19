import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { FormsModule } from '@angular/forms';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';              // NEW
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';      // NEW
import { ProductionService } from '../../services/production.service';
import { ProductionOrder, Inspection, InspectionUpdateRequest } from '../../models/production-order.model';  // NEW (add Inspection types)
import { InspectionDialogComponent } from './inspection-dialog.component';          // NEW

interface SalesOrderConfirmation {
  salesOrderId: string;
  requestedDelivery: string;
  confirmedDelivery: string;
  reasonForChange: string;
}

@Component({
  selector: 'app-production-orders',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatTableModule,
    MatTabsModule,
    MatSelectModule,
    MatFormFieldModule,
    MatDialogModule,          // NEW
    MatSnackBarModule         // NEW
  ],
  templateUrl: './production-orders.component.html',
  styleUrls: ['./production-orders.component.css']
})
export class ProductionOrdersTableComponent implements OnInit {
  isLoading = true;
  errorMsg = '';
  productionOrders: ProductionOrder[] = [];

  // Selected production order for operations & components views
  selectedProductionOrderId = '';
  selectedProductionOrder: ProductionOrder | null = null;

  // Table columns
  prodOrdersColumns = ['prodOrderId', 'salesOrderId', 'material', 'plannedQty', 'basicStart', 'basicFinish', 'actualStart', 'actualFinish', 'status'];
  operationsColumns = ['opId', 'workCenter', 'description', 'setupHours', 'processingHours', 'totalHours', 'start', 'finish', 'delayed'];
  componentsColumns = ['component', 'description', 'requiredQty', 'reservedQty', 'unit', 'batch', 'withdrawn'];
  confirmationsColumns = ['salesOrderId', 'requestedDelivery', 'confirmedDelivery', 'reason'];
  inspectionsColumns = ['inspectionId', 'type', 'description', 'scheduledTime', 'status', 'judgment'];  // NEW

  // Sales order confirmations (computed from production orders)
  salesOrderConfirmations: SalesOrderConfirmation[] = [];

  constructor(
    private prodService: ProductionService,
    private dialog: MatDialog,          // NEW
    private snackBar: MatSnackBar       // NEW
  ) {}

  ngOnInit() {
    this.loadData();
  }

  loadData() {
    this.isLoading = true;
    this.prodService.getProductionOrders().subscribe({
      next: (orders) => {
        this.productionOrders = orders;
        if (orders.length) {
          this.selectedProductionOrderId = orders[0].productionOrderId;
          this.selectedProductionOrder = orders[0];
          this.computeSalesOrderConfirmations();
        }
        this.isLoading = false;
      },
      error: (err) => {
        this.errorMsg = err.message;
        this.isLoading = false;
      }
    });
  }

  computeSalesOrderConfirmations() {
    // Group by salesOrderId
    const salesOrderMap = new Map<string, {
      requestedDelivery: string;
      confirmedDelivery: string;
      reasons: string[]
    }>();

    for (const po of this.productionOrders) {
      const soId = po.salesOrderId;
      // Determine confirmed delivery date (max of production order finish dates for that sales order)
      const confirmedDate = po.actualFinishDate || po.basicFinishDate;

      if (!salesOrderMap.has(soId)) {
        // We don't have requested delivery date from production order; need to fetch from original sales order.
        // Since backend doesn't return requested delivery, we'll set a placeholder or we could add a field.
        // For demo, we'll generate a reasonable reason.
        salesOrderMap.set(soId, {
          requestedDelivery: 'N/A', // would come from sales order data
          confirmedDelivery: confirmedDate,
          reasons: []
        });
      } else {
        const existing = salesOrderMap.get(soId)!;
        if (confirmedDate > existing.confirmedDelivery) {
          existing.confirmedDelivery = confirmedDate;
        }
      }
      // Collect reasons for delay (from delayed steps)
      if (po.status === 'DELAYED') {
        salesOrderMap.get(soId)!.reasons.push(`Production order ${po.productionOrderId} delayed`);
      }
    }

    this.salesOrderConfirmations = Array.from(salesOrderMap.entries()).map(([soId, data]) => ({
      salesOrderId: soId,
      requestedDelivery: data.requestedDelivery,
      confirmedDelivery: this.formatDate(data.confirmedDelivery),
      reasonForChange: data.reasons.length ? data.reasons.join('; ') : (data.confirmedDelivery > data.requestedDelivery ? 'Due date breach' : 'No change')
    }));
  }

  onProductionOrderSelect(orderId: string) {
    this.selectedProductionOrderId = orderId;
    this.selectedProductionOrder = this.productionOrders.find(o => o.productionOrderId === orderId) || null;
  }

  // NEW: Open dialog to update inspection result
  updateInspection(inspection: Inspection) {
    const dialogRef = this.dialog.open(InspectionDialogComponent, {
      width: '500px',
      data: { inspection, productionOrderId: this.selectedProductionOrderId }
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.prodService.updateInspection(this.selectedProductionOrderId, inspection.inspectionId, result)
          .subscribe({
            next: (updated) => {
              // Update local data
              const idx = this.selectedProductionOrder!.inspections!.findIndex(i => i.inspectionId === updated.inspectionId);
              if (idx !== -1) this.selectedProductionOrder!.inspections![idx] = updated;
              this.snackBar.open(`Inspection ${updated.inspectionId} updated`, 'Close', { duration: 3000 });
              this.loadData(); // or just refresh the view
            },
            error: (err) => this.snackBar.open(`Error: ${err.message}`, 'Close', { duration: 4000 })
          });
      }
    });
  }

  // NEW: Helper for inspection status styling
  inspectionStatusClass(status: string): string {
    const map: Record<string, string> = {
      PASSED: 'chip-green',
      FAILED: 'chip-red',
      IN_PROGRESS: 'chip-blue',
      PENDING: 'chip-orange',
      SKIPPED: 'chip-grey'
    };
    return map[status] || 'chip-grey';
  }

  formatDate(dateStr: string): string {
    if (!dateStr) return '—';
    return new Date(dateStr).toLocaleString();
  }

  formatDuration(min: number): string {
    if (!min) return '—';
    const h = Math.floor(min / 60);
    const m = min % 60;
    return h > 0 ? `${h}h ${m > 0 ? m + 'm' : ''}` : `${m}m`;
  }

  statusClass(status: string): string {
    const map: Record<string, string> = {
      RELEASED: 'chip-green',
      DELAYED: 'chip-red',
      CREATED: 'chip-blue'
    };
    return map[status] || 'chip-grey';
  }
}