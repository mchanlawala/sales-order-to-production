import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { BaseChartDirective } from 'ng2-charts';
import { ProductionService } from '../../services/production.service';
import { DashboardData, ProductionOrder } from '../../models/production-order.model';
import { ChartData, ChartOptions } from 'chart.js';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatIconModule, MatButtonModule, MatProgressSpinnerModule, MatTableModule, BaseChartDirective],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent implements OnInit {
  isLoading = true;
  data: DashboardData | null = null;
  insights = '';
  insightsLoading = false;
  errorMsg = '';

  statusChartData: ChartData<'bar'> = { labels: [], datasets: [] };
  statusChartOptions: ChartOptions<'bar'> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { display: false } },
    scales: { y: { beginAtZero: true, ticks: { stepSize: 1 } } }
  };

  displayedColumns = ['orderId', 'material', 'quantity', 'status', 'startDate', 'finishDate'];

  constructor(private prodService: ProductionService) {}

  ngOnInit() {
    this.loadDashboard();
  }

  loadDashboard() {
    this.isLoading = true;
    this.prodService.getDashboard().subscribe({
      next: (d) => {
        this.data = d;
        this.buildCharts(d);
        this.isLoading = false;
      },
      error: (err) => {
        this.errorMsg = err.message;
        this.isLoading = false;
      }
    });
  }

  loadInsights() {
    this.insightsLoading = true;
    this.prodService.getInsights().subscribe({
      next: (res) => { this.insights = res.insights; this.insightsLoading = false; },
      error: () => { this.insights = 'Failed to load insights.'; this.insightsLoading = false; }
    });
  }

  buildCharts(data: DashboardData) {
    const labels = Object.keys(data.ordersByStatus);
    const values = Object.values(data.ordersByStatus);
    this.statusChartData = {
      labels: labels,
      datasets: [{
        data: values,
        backgroundColor: labels.map(l => l === 'DELAYED' ? '#EF5350' : l === 'RELEASED' ? '#66BB6A' : '#FFA726'),
        borderRadius: 4
      }]
    };
  }

  statusClass(status: string): string {
    const map: Record<string, string> = {
      RELEASED: 'chip-green', DELAYED: 'chip-red', CREATED: 'chip-blue'
    };
    return map[status] || 'chip-grey';
  }

  formatDate(dateStr: string): string {
    if (!dateStr) return '—';
    return new Date(dateStr).toLocaleString();
  }
}