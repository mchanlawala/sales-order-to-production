import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { FormsModule } from '@angular/forms';
import { Inspection, InspectionUpdateRequest } from '../../models/production-order.model';

@Component({
  selector: 'app-inspection-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule
  ],
  template: `
    <h2 mat-dialog-title>Inspection Result: {{ data.inspection.description }}</h2>
    <mat-dialog-content>
      <mat-form-field appearance="outline" style="width:100%">
        <mat-label>Measured Value</mat-label>
        <input matInput [(ngModel)]="update.measuredValue" placeholder="e.g., 546.2 or 'No defects'">
      </mat-form-field>
      <mat-form-field appearance="outline" style="width:100%">
        <mat-label>Judgment</mat-label>
        <mat-select [(ngModel)]="update.judgment">
          <mat-option value="PASS">PASS</mat-option>
          <mat-option value="FAIL">FAIL</mat-option>
          <mat-option value="REWORK">REWORK</mat-option>
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline" style="width:100%">
        <mat-label>Status</mat-label>
        <mat-select [(ngModel)]="update.status">
          <mat-option value="PASSED">PASSED</mat-option>
          <mat-option value="FAILED">FAILED</mat-option>
          <mat-option value="IN_PROGRESS">IN_PROGRESS</mat-option>
          <mat-option value="SKIPPED">SKIPPED</mat-option>
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline" style="width:100%">
        <mat-label>Remarks (optional)</mat-label>
        <textarea matInput rows="3" [(ngModel)]="update.remarks"></textarea>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="cancel()">Cancel</button>
      <button mat-raised-button color="primary" (click)="save()" [disabled]="!update.judgment || !update.status">Save</button>
    </mat-dialog-actions>
  `
})
export class InspectionDialogComponent {
  update: InspectionUpdateRequest = {
    measuredValue: null,
    judgment: '',
    status: '',
    remarks: null
  };

  constructor(
    public dialogRef: MatDialogRef<InspectionDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { inspection: Inspection; productionOrderId: string }
  ) {
    // Pre-fill existing values if any
    if (data.inspection.measuredValue) this.update.measuredValue = data.inspection.measuredValue;
    if (data.inspection.judgment) this.update.judgment = data.inspection.judgment;
    if (data.inspection.status && data.inspection.status !== 'PENDING') this.update.status = data.inspection.status;
    if (data.inspection.remarks) this.update.remarks = data.inspection.remarks;
  }

  cancel(): void {
    this.dialogRef.close();
  }

  save(): void {
    this.dialogRef.close(this.update);
  }
}