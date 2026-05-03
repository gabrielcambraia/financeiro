package com.financeiro.controller;

import com.financeiro.dto.DashboardDTO;
import com.financeiro.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService service;

    @GetMapping
    public DashboardDTO getDashboard(
            @RequestParam String month,
            @RequestParam(required = false) Long accountId) {
        return service.getDashboard(month, accountId);
    }
}
