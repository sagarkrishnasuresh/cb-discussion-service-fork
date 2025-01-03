package com.igot.cb.metrics.controller;

import com.igot.cb.metrics.service.ApiMetricsTracker;
import com.igot.cb.pores.util.ApiResponse;
import com.igot.cb.pores.util.ProjectUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiMetricsController {

    @GetMapping("/api/metrics")
    public ApiMetricsTracker.ApiMetricsResponse getApiMetrics(@RequestParam String apiEndpoint) {
        return ApiMetricsTracker.getApiMetrics(apiEndpoint);
    }

    @GetMapping("/api/metrics/enableTracking")
    public ApiResponse enableTracking() {
        ApiResponse response = ProjectUtil.createDefaultResponse("/api/metrics/enableTracking");
        ApiMetricsTracker.enableTracking();
        return response;
    }

    @GetMapping("/api/metrics/disableTracking")
    public ApiResponse disableTracking() {
        ApiResponse response = ProjectUtil.createDefaultResponse("/api/metrics/disableTracking");
        ApiMetricsTracker.disableTracking();
        return response;
    }

}
