import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormGroup, FormControl, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { Employee, LeaveRequest } from '../models/leave-request.model';
import { LeaveRequestsService } from './leave-requests.service';

function dateRangeValidator(control: AbstractControl): ValidationErrors | null {
  const start = control.get('startDate')?.value;
  const end = control.get('endDate')?.value;
  if (start && end && start > end) {
    return { dateRange: true };
  }
  return null;
}

@Component({
  selector: 'app-leave-requests',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './leave-requests.component.html',
  styleUrls: ['./leave-requests.component.css']
})
export class LeaveRequestsComponent implements OnInit, OnDestroy {
  requests: LeaveRequest[] = [];
  employees: Employee[] = [];
  loading = false;
  submitError = '';
  submitSuccess = false;

  approveLoading = new Set<number>();
  approveError = new Map<number, string>();
  approveSuccess = new Set<number>();

  private destroy$ = new Subject<void>();

  form = new FormGroup({
    employeeId: new FormControl<number | null>(null, Validators.required),
    type: new FormControl<number | null>(null, Validators.required),
    startDate: new FormControl('', Validators.required),
    endDate: new FormControl('', Validators.required),
  }, { validators: dateRangeValidator });

  constructor(private service: LeaveRequestsService) {}

  ngOnInit(): void {
    this.loadRequests();
    this.service.getEmployees()
      .pipe(takeUntil(this.destroy$))
      .subscribe(data => this.employees = data);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadRequests(): void {
    this.loading = true;
    this.service.getRequests()
      .pipe(takeUntil(this.destroy$))
      .subscribe(data => {
        this.requests = data;
        this.loading = false;
      });
  }

  submit(): void {
    if (this.form.invalid) return;
    this.submitError = '';
    this.submitSuccess = false;
    const { employeeId, type, startDate, endDate } = this.form.value;
    this.service.createRequest({ employeeId: employeeId!, type: type!, startDate: startDate!, endDate: endDate! })
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.submitSuccess = true;
          this.form.reset();
          this.loadRequests();
        },
        error: err => {
          this.submitError = err.error ?? 'Failed to submit request';
        }
      });
  }

  approve(id: number): void {
    this.approveLoading.add(id);
    this.approveError.delete(id);
    this.approveSuccess.delete(id);
    this.service.approveRequest(id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.approveLoading.delete(id);
          this.approveSuccess.add(id);
          this.loadRequests();
        },
        error: err => {
          this.approveLoading.delete(id);
          this.approveError.set(id, err.error ?? 'Failed to approve');
        }
      });
  }

  typeLabel(type: number): string {
    if (type === 0) return 'Vacation';
    if (type === 1) return 'Sick';
    return 'Unpaid';
  }

  statusLabel(status: number): string {
    if (status === 0) return 'Pending';
    if (status === 1) return 'Approved';
    return 'Rejected';
  }
}
