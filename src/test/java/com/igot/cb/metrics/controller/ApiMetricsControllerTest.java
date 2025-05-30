package com.igot.cb.metrics.controller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;

import com.igot.cb.metrics.service.ApiMetricsTracker;
import com.igot.cb.pores.util.ApiResponse;
import com.igot.cb.pores.util.ProjectUtil;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;


@ExtendWith(MockitoExtension.class)
class ApiMetricsControllerTest {

    @InjectMocks
    private ApiMetricsController controller;

    @BeforeEach
    void setup() {
        // No-op for now
    }

    @Test
    void testGetApiMetrics() {
        String apiEndpoint = "/test";
        ApiMetricsTracker.ApiMetricsResponse mockResponse = new ApiMetricsTracker.ApiMetricsResponse();

        try (MockedStatic<ApiMetricsTracker> mockedTracker = Mockito.mockStatic(ApiMetricsTracker.class)) {
            mockedTracker.when(() -> ApiMetricsTracker.getApiMetrics(apiEndpoint)).thenReturn(mockResponse);

            ApiMetricsTracker.ApiMetricsResponse response = controller.getApiMetrics(apiEndpoint);

            assertNotNull(response);
        }
    }

    @Test
    void testEnableTracking() {
        try (
                MockedStatic<ApiMetricsTracker> mockedTracker = Mockito.mockStatic(ApiMetricsTracker.class);
                MockedStatic<ProjectUtil> mockedUtil = Mockito.mockStatic(ProjectUtil.class)
        ) {
            ApiResponse mockResponse = new ApiResponse();
            mockedUtil.when(() -> ProjectUtil.createDefaultResponse("/api/metrics/enableTracking")).thenReturn(mockResponse);

            ApiResponse response = controller.enableTracking();

            assertNotNull(response);
            mockedTracker.verify(() -> ApiMetricsTracker.enableTracking());
        }
    }

    @Test
    void testDisableTracking() {
        try (
                MockedStatic<ApiMetricsTracker> mockedTracker = Mockito.mockStatic(ApiMetricsTracker.class);
                MockedStatic<ProjectUtil> mockedUtil = Mockito.mockStatic(ProjectUtil.class)
        ) {
            ApiResponse mockResponse = new ApiResponse();
            mockedUtil.when(() -> ProjectUtil.createDefaultResponse("/api/metrics/disableTracking")).thenReturn(mockResponse);

            ApiResponse response = controller.disableTracking();

            assertNotNull(response);
            mockedTracker.verify(() -> ApiMetricsTracker.disableTracking());
        }
    }
}
