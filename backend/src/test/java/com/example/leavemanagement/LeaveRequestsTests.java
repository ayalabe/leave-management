package com.example.leavemanagement;

import com.example.leavemanagement.controller.LeaveRequestsController;
import com.example.leavemanagement.dto.CreateLeaveRequestDto;
import com.example.leavemanagement.model.Employee;
import com.example.leavemanagement.model.LeaveType;
import com.example.leavemanagement.repository.EmployeeRepository;
import com.example.leavemanagement.repository.LeaveRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class LeaveRequestsTests {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private LeaveRequestsController controller;

    @Autowired
    private EmployeeRepository employees;

    @Autowired
    private LeaveRequestRepository leaveRequests;

    @Test
    void create_WithinQuota_Succeeds() {
        Employee emp = new Employee();
        emp.setName("Test Emp");
        emp.setAnnualQuota(20);
        employees.save(emp);

        long before = leaveRequests.count();

        CreateLeaveRequestDto dto = new CreateLeaveRequestDto();
        dto.setEmployeeId(emp.getId());
        dto.setType(LeaveType.VACATION);
        dto.setStartDate(LocalDate.of(2026, 3, 1));
        dto.setEndDate(LocalDate.of(2026, 3, 3));

        ResponseEntity<?> result = controller.create(dto);

        assertTrue(result.getStatusCode().is2xxSuccessful());
        assertEquals(before + 1, leaveRequests.count());
    }

    @Test
    void create_ExceedsQuotaWhenCombinedWithApproved_Returns400() {
        Employee emp = new Employee();
        emp.setName("Quota Test Emp");
        emp.setAnnualQuota(20);
        employees.save(emp);

        com.example.leavemanagement.model.LeaveRequest existing = new com.example.leavemanagement.model.LeaveRequest();
        existing.setEmployeeId(emp.getId());
        existing.setType(LeaveType.VACATION);
        existing.setStartDate(LocalDate.of(2026, 1, 1));
        existing.setEndDate(LocalDate.of(2026, 1, 18));
        existing.setDays(18);
        existing.setStatus(com.example.leavemanagement.model.LeaveStatus.APPROVED);
        leaveRequests.save(existing);

        CreateLeaveRequestDto dto = new CreateLeaveRequestDto();
        dto.setEmployeeId(emp.getId());
        dto.setType(LeaveType.VACATION);
        dto.setStartDate(LocalDate.of(2026, 3, 1));
        dto.setEndDate(LocalDate.of(2026, 3, 5));

        ResponseEntity<?> result = controller.create(dto);

        assertEquals(400, result.getStatusCode().value());
    }
}
