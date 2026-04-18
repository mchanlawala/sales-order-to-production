import { Component } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatToolbarModule, MatIconModule, MatButtonModule],
  template: `
    <mat-toolbar color="primary" class="app-toolbar">
      <mat-icon>factory</mat-icon>
      <span style="margin-left: 8px;">Production Order Scheduler</span>
      <span class="spacer"></span>
      <button mat-button routerLink="/upload" routerLinkActive="active-link">
        <mat-icon>upload_file</mat-icon> Upload
      </button>
      <button mat-button routerLink="/dashboard" routerLinkActive="active-link">
        <mat-icon>dashboard</mat-icon> Dashboard
      </button>
      <button mat-button routerLink="/gantt" routerLinkActive="active-link">
        <mat-icon>view_timeline</mat-icon> Gantt Chart
      </button>
      <button mat-button routerLink="/production-orders" routerLinkActive="active-link">
        <mat-icon>table_chart</mat-icon> Production Orders
      </button>
    </mat-toolbar>
    <router-outlet></router-outlet>
  `,
  styles: [`
    .active-link { background-color: rgba(255,255,255,0.2); border-radius: 4px; }
  `]
})
export class AppComponent {}