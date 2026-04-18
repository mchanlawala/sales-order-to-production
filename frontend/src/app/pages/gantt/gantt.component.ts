import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { ProductionService } from '../../services/production.service';
import { ProductionOrder, ProductionOrderOperation } from '../../models/production-order.model';

interface GanttBar {
  operationId: number;
  description: string;
  workCenter: string;
  start: Date;
  end: Date;
  durationMin: number;
  isDelayed: boolean;
  delayReason: string | null;
  leftPct: number;
  widthPct: number;
  color: string;
}

@Component({
  selector: 'app-gantt',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatFormFieldModule,
    MatSelectModule
  ],
  templateUrl: './gantt.component.html',
  styleUrls: ['./gantt.component.css']
})
export class GanttComponent implements OnInit {
  isLoading = true;
  errorMsg = '';
  productionOrders: ProductionOrder[] = [];
  selectedOrderId = '';
  viewMode: 'all' | 'single' = 'all';
  filterStatus = 'ALL';

  allGantts: { order: ProductionOrder; bars: GanttBar[]; totalHours: number }[] = [];
  allMinDate: Date = new Date();
  allMaxDate: Date = new Date();
  allTimeMarkers: { label: string; pct: number }[] = [];

  singleGantt: GanttBar[] = [];
  singleMinDate: Date = new Date();
  singleMaxDate: Date = new Date();
  singleTimeMarkers: { label: string; pct: number }[] = [];

  tooltip = { visible: false, x: 0, y: 0, name: '', line: '', start: '', end: '', duration: '', delayed: false, delayReason: '' };

  private colors = ['#378ADD', '#1D9E75', '#D85A30', '#9C27B0', '#D4537E', '#BA7517', '#185FA5', '#3B6D11'];

  constructor(private prodService: ProductionService) {}

  ngOnInit() {
    this.loadOrders();
  }

  loadOrders() {
    this.isLoading = true;
    this.prodService.getProductionOrders().subscribe({
      next: (orders) => {
        this.productionOrders = orders;
        if (orders.length) {
          this.selectedOrderId = orders[0].productionOrderId;
          this.buildAllGantts();
          this.buildSingleGantt();
        }
        this.isLoading = false;
      },
      error: (err) => {
        this.errorMsg = err.message;
        this.isLoading = false;
      }
    });
  }

  buildAllGantts() {
    if (!this.productionOrders.length) return;
    const allStarts: number[] = [];
    const allEnds: number[] = [];
    for (const order of this.productionOrders) {
      if (order.operations?.length) {
        allStarts.push(new Date(order.operations[0].plannedStart).getTime());
        allEnds.push(new Date(order.operations[order.operations.length-1].plannedFinish).getTime());
      }
    }
    const minT = Math.min(...allStarts);
    const maxT = Math.max(...allEnds);
    const span = maxT - minT || 1;
    this.allMinDate = new Date(minT);
    this.allMaxDate = new Date(maxT);
    this.allTimeMarkers = this.buildMarkers(minT, maxT, 6);

    this.allGantts = this.productionOrders.map((order, idx) => {
      const bars: GanttBar[] = (order.operations || []).map((op, opIdx) => {
        const start = new Date(op.plannedStart).getTime();
        const end = new Date(op.plannedFinish).getTime();
        return {
          operationId: op.operationId,
          description: op.description,
          workCenter: op.workCenter,
          start: new Date(op.plannedStart),
          end: new Date(op.plannedFinish),
          durationMin: Math.round(op.totalHours * 60),
          isDelayed: op.isDelayed,
          delayReason: op.delayReason,
          leftPct: ((start - minT) / span) * 100,
          widthPct: Math.max(0.5, ((end - start) / span) * 100),
          color: this.colors[opIdx % this.colors.length]
        };
      });
      const totalHours = order.operations?.reduce((sum, op) => sum + op.totalHours, 0) || 0;
      return { order, bars, totalHours: Math.round(totalHours) };
    });
  }

  buildSingleGantt() {
    const order = this.productionOrders.find(o => o.productionOrderId === this.selectedOrderId);
    if (!order?.operations?.length) { this.singleGantt = []; return; }
    const ops = order.operations;
    const minT = new Date(ops[0].plannedStart).getTime();
    const maxT = new Date(ops[ops.length-1].plannedFinish).getTime();
    const span = maxT - minT || 1;
    this.singleMinDate = new Date(minT);
    this.singleMaxDate = new Date(maxT);
    this.singleTimeMarkers = this.buildMarkers(minT, maxT, 5);
    this.singleGantt = ops.map((op, idx) => {
      const start = new Date(op.plannedStart).getTime();
      const end = new Date(op.plannedFinish).getTime();
      return {
        operationId: op.operationId,
        description: op.description,
        workCenter: op.workCenter,
        start: new Date(op.plannedStart),
        end: new Date(op.plannedFinish),
        durationMin: Math.round(op.totalHours * 60),
        isDelayed: op.isDelayed,
        delayReason: op.delayReason,
        leftPct: ((start - minT) / span) * 100,
        widthPct: Math.max(0.5, ((end - start) / span) * 100),
        color: this.colors[idx % this.colors.length]
      };
    });
  }

  buildMarkers(minT: number, maxT: number, count: number): { label: string; pct: number }[] {
    const markers = [];
    for (let i = 0; i <= count; i++) {
      const t = new Date(minT + (maxT - minT) * i / count);
      markers.push({ label: this.formatTime(t), pct: (i / count) * 100 });
    }
    return markers;
  }

  onOrderSelect(orderId: string) {
    this.selectedOrderId = orderId;
    this.buildSingleGantt();
    this.viewMode = 'single';
  }

  showTooltip(event: MouseEvent, bar: GanttBar) {
    this.tooltip = {
      visible: true,
      x: event.clientX + 14,
      y: event.clientY - 10,
      name: bar.description,
      line: bar.workCenter,
      start: this.formatDateTime(bar.start),
      end: this.formatDateTime(bar.end),
      duration: this.formatDuration(bar.durationMin),
      delayed: bar.isDelayed,
      delayReason: bar.delayReason || ''
    };
  }

  hideTooltip() { this.tooltip.visible = false; }

  formatDateTime(d: Date): string { return d.toLocaleString(); }
  formatTime(d: Date): string { return d.toLocaleTimeString([], { hour: '2-digit', minute:'2-digit' }); }
  formatDuration(min: number): string {
    const h = Math.floor(min / 60);
    const m = min % 60;
    return h > 0 ? `${h}h ${m > 0 ? m + 'm' : ''}` : `${m}m`;
  }

  statusClass(status: string): string {
    const map: Record<string, string> = { RELEASED: 'chip-green', DELAYED: 'chip-red', CREATED: 'chip-blue' };
    return map[status] || 'chip-grey';
  }

  get uniqueStatuses(): string[] {
    const set = new Set(this.productionOrders.map(o => o.status));
    return ['ALL', ...Array.from(set)];
  }

  get filteredOrders() {
    if (this.filterStatus === 'ALL') return this.productionOrders;
    return this.productionOrders.filter(o => o.status === this.filterStatus);
  }

  // Helper to get selected order status for display
  getSelectedOrderStatus(): string {
    const order = this.productionOrders.find(o => o.productionOrderId === this.selectedOrderId);
    return order?.status || '';
  }
}