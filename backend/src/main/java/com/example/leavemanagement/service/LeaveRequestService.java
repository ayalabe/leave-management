package com.example.leavemanagement.service;

import com.example.leavemanagement.dto.CreateLeaveRequestDto;
import com.example.leavemanagement.model.Employee;
import com.example.leavemanagement.model.LeaveRequest;
import com.example.leavemanagement.model.LeaveStatus;
import com.example.leavemanagement.model.LeaveType;
import com.example.leavemanagement.repository.EmployeeRepository;
import com.example.leavemanagement.repository.LeaveRequestRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class LeaveRequestService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;

    public LeaveRequestService(LeaveRequestRepository leaveRequestRepository,
                               EmployeeRepository employeeRepository) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.employeeRepository = employeeRepository;
    }

    public List<LeaveRequest> getAll() {
        return leaveRequestRepository.findAll().stream()
                .sorted((a, b) -> b.getStartDate().compareTo(a.getStartDate()))
                .toList();
    }

    public ResponseEntity<?> create(CreateLeaveRequestDto dto) {
        Employee employee = employeeRepository.findById(dto.getEmployeeId()).orElse(null);
        if (employee == null) {
            return ResponseEntity.status(404).body("Employee not found");
        }

        int days = (int) ChronoUnit.DAYS.between(dto.getStartDate(), dto.getEndDate()) + 1;

        int used = leaveRequestRepository
                .findByEmployeeIdAndTypeAndStatus(dto.getEmployeeId(), LeaveType.VACATION, LeaveStatus.APPROVED)
                .stream()
                .mapToInt(LeaveRequest::getDays)
                .sum();

        if (dto.getType() == LeaveType.VACATION && (used + days) > employee.getAnnualQuota()) {
            int remaining = employee.getAnnualQuota() - used;
            return ResponseEntity.badRequest().body(
                "Not enough vacation balance: requested " + days + " days but only " + remaining + " remaining"
            );
        }

        LeaveRequest request = new LeaveRequest();
        request.setEmployeeId(dto.getEmployeeId());
        request.setType(dto.getType());
        request.setStartDate(dto.getStartDate());
        request.setEndDate(dto.getEndDate());
        request.setDays(days);
        request.setStatus(LeaveStatus.PENDING);

        leaveRequestRepository.save(request);
        return ResponseEntity.ok(request);
    }

    @Transactional
    public ResponseEntity<?> approve(Long id) {
        LeaveRequest request = leaveRequestRepository.findByIdWithLock(id).orElse(null);
        if (request == null) {
            return ResponseEntity.status(404).body("Leave request not found");
        }
        if (request.getStatus() != LeaveStatus.PENDING) {
            return ResponseEntity.status(409).body("Request is already " + request.getStatus());
        }

        if (request.getType() == LeaveType.VACATION) {
            Employee employee = employeeRepository.findById(request.getEmployeeId()).orElseThrow();
            int used = leaveRequestRepository
                    .findByEmployeeIdAndTypeAndStatus(request.getEmployeeId(), LeaveType.VACATION, LeaveStatus.APPROVED)
                    .stream()
                    .mapToInt(LeaveRequest::getDays)
                    .sum();
            if (used + request.getDays() > employee.getAnnualQuota()) {
                return ResponseEntity.badRequest().body("Approving this request would exceed the employee's annual quota");
            }
        }

        request.setStatus(LeaveStatus.APPROVED);
        leaveRequestRepository.save(request);
        return ResponseEntity.ok(request);
    }
}
