package com.igot.cb.metrics.controller;

import com.igot.cb.metrics.service.ApiMetricsTracker;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiMetricsController {

    @GetMapping("/api/metrics")
    public ApiMetricsTracker.ApiMetricsResponse getApiMetrics(@RequestParam String apiEndpoint) {
        return ApiMetricsTracker.getApiMetrics(apiEndpoint);
    }
}
