import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Employee, LeaveRequest } from '../models/leave-request.model';

@Injectable({ providedIn: 'root' })
export class LeaveRequestsService {
  private base = 'http://localhost:5080/api';

  constructor(private http: HttpClient) {}

  getRequests(): Observable<LeaveRequest[]> {
    return this.http.get<LeaveRequest[]>(`${this.base}/leave-requests`);
  }

  getEmployees(): Observable<Employee[]> {
    return this.http.get<Employee[]>(`${this.base}/employees`);
  }

  createRequest(payload: { employeeId: number; type: number; startDate: string; endDate: string }): Observable<LeaveRequest> {
    return this.http.post<LeaveRequest>(`${this.base}/leave-requests`, payload);
  }

  approveRequest(id: number): Observable<LeaveRequest> {
    return this.http.post<LeaveRequest>(`${this.base}/leave-requests/${id}/approve`, {});
  }
}
