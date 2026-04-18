import { Routes } from '@angular/router';
import { UploadComponent } from './pages/upload/upload.component';
import { DashboardComponent } from './pages/dashboard/dashboard.component';
import { GanttComponent } from './pages/gantt/gantt.component';
import { ProductionOrdersTableComponent } from './pages/production-orders/production-orders.component';

export const routes: Routes = [
  { path: '', redirectTo: '/upload', pathMatch: 'full' },
  { path: 'upload', component: UploadComponent },
  { path: 'dashboard', component: DashboardComponent },
  { path: 'gantt', component: GanttComponent },
  { path: 'production-orders', component: ProductionOrdersTableComponent }
];