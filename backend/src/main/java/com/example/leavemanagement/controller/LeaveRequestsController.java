package com.example.leavemanagement.controller;

import com.example.leavemanagement.dto.CreateLeaveRequestDto;
import com.example.leavemanagement.model.LeaveRequest;
import com.example.leavemanagement.repository.LeaveRequestRepository;
import com.example.leavemanagement.service.LeaveRequestService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leave-requests")
public class LeaveRequestsController {

    private final LeaveRequestService leaveRequestService;
    private final LeaveRequestRepository leaveRequestRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public LeaveRequestsController(LeaveRequestService leaveRequestService,
                                   LeaveRequestRepository leaveRequestRepository) {
        this.leaveRequestService = leaveRequestService;
        this.leaveRequestRepository = leaveRequestRepository;
    }

    @GetMapping
    public List<LeaveRequest> getAll() {
        return leaveRequestService.getAll();
    }

    @GetMapping("/search")
    public List<LeaveRequest> search(@RequestParam String name) {
        String sql = "SELECT * FROM leave_requests WHERE employee_id IN " +
                "(SELECT id FROM employees WHERE name LIKE :pattern)";

        @SuppressWarnings("unchecked")
        List<LeaveRequest> results = entityManager
                .createNativeQuery(sql, LeaveRequest.class)
                .setParameter("pattern", "%" + name + "%")
                .getResultList();

        return results;
    }

    @PostMapping
    public ResponseEntity<LeaveRequest> create(@RequestBody CreateLeaveRequestDto dto) {
        return ResponseEntity.ok(leaveRequestService.create(dto));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<LeaveRequest> approve(@PathVariable Long id) {
        return ResponseEntity.ok(leaveRequestService.approve(id));
    }
}
