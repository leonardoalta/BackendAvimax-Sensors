package com.avimax.backend.controller;

import com.avimax.backend.dto.DashboardPrincipalResponse;
import com.avimax.backend.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/principal")
    public DashboardPrincipalResponse principal() {
        return dashboardService.getPrincipalDashboard();
    }
}