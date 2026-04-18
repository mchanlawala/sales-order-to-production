import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ProductionOrder, DashboardData } from '../models/production-order.model';

@Injectable({ providedIn: 'root' })
export class ProductionService {
  private api = '/api/production';

  constructor(private http: HttpClient) {}

  uploadExcel(file: File): Observable<any> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post(`${this.api}/upload`, form).pipe(catchError(this.handleError));
  }

  getProductionOrders(): Observable<ProductionOrder[]> {
    return this.http.get<ProductionOrder[]>(`${this.api}/orders`).pipe(catchError(this.handleError));
  }

  getDashboard(): Observable<DashboardData> {
    return this.http.get<DashboardData>(`${this.api}/dashboard`).pipe(catchError(this.handleError));
  }

  getInsights(): Observable<{ insights: string }> {
    return this.http.get<{ insights: string }>(`${this.api}/insights`).pipe(catchError(this.handleError));
  }

  private handleError(err: HttpErrorResponse) {
    const msg = err.error?.error || err.message || 'Unknown error';
    return throwError(() => new Error(msg));
  }
}