package com.igot.cb.metrics.service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ApiMetricsTracker {

    // Centralized map to store metrics for each API endpoint
    private static final Map<String, ApiMetrics> apiMetricsMap = new ConcurrentHashMap<>();
    private static final ThreadLocal<Boolean> trackingEnabled = ThreadLocal.withInitial(() -> false);

    public static void enableTracking() {
        trackingEnabled.set(true);
    }

    public static boolean isTrackingEnabled() {
        return trackingEnabled.get();
    }

    /**
     * Records an API call.
     *
     * @param apiEndpoint The API endpoint being tracked.
     */
    public static void recordApiCall(String apiEndpoint) {
        ApiMetrics apiMetrics = apiMetricsMap.computeIfAbsent(apiEndpoint, k -> new ApiMetrics());
        apiMetrics.incrementApiCallCount();
    }

    /**
     * Records a database operation's metrics.
     *
     * @param apiEndpoint The API endpoint being tracked.
     * @param dbName      The name of the database (e.g., "cassandra", "redis").
     * @param operation   The operation performed (e.g., "insert", "read").
     * @param timeTaken   The time taken for the operation in milliseconds.
     */
    public static void recordDbOperation(String apiEndpoint, String dbName, String operation, long timeTaken) {
        ApiMetrics apiMetrics = apiMetricsMap.computeIfAbsent(apiEndpoint, k -> new ApiMetrics());
        DbMetrics dbMetrics = apiMetrics.getDbMetricsMap().computeIfAbsent(dbName, k -> new DbMetrics());

        // Update counts and times for the operation
        dbMetrics.getOperationCounts().merge(operation, 1, Integer::sum);
        dbMetrics.getOperationTimes().merge(operation, timeTaken, Long::sum);
    }

    /**
     * Retrieves the metrics for a specific API.
     *
     * @param apiEndpoint The API endpoint to retrieve metrics for.
     * @return A formatted response object containing metrics.
     */
    public static ApiMetricsResponse getApiMetrics(String apiEndpoint) {
        ApiMetrics apiMetrics = apiMetricsMap.getOrDefault(apiEndpoint, new ApiMetrics());
        return apiMetrics.toResponse();
    }

    /**
     * Clears all stored metrics (optional, for resetting metrics).
     */
    public static void resetMetrics() {
        apiMetricsMap.clear();
    }

    // Inner class representing metrics for a specific API
    private static class ApiMetrics {
        private int apiCallCount = 0;
        private final Map<String, DbMetrics> dbMetricsMap = new ConcurrentHashMap<>();

        public void incrementApiCallCount() {
            apiCallCount++;
        }

        public Map<String, DbMetrics> getDbMetricsMap() {
            return dbMetricsMap;
        }

        public ApiMetricsResponse toResponse() {
            ApiMetricsResponse response = new ApiMetricsResponse();
            response.setApiCallCount(apiCallCount);
            response.setCassandraMetrics(dbMetricsMap.getOrDefault("cassandra", new DbMetrics()).toResponse());
            response.setRedisMetrics(dbMetricsMap.getOrDefault("redis", new DbMetrics()).toResponse());
            response.setElasticsearchMetrics(dbMetricsMap.getOrDefault("elasticsearch", new DbMetrics()).toResponse());
            response.setPostgresMetrics(dbMetricsMap.getOrDefault("postgres", new DbMetrics()).toResponse());
            return response;
        }
    }

    // Inner class representing database-specific metrics
    private static class DbMetrics {
        private final Map<String, Integer> operationCounts = new HashMap<>();
        private final Map<String, Long> operationTimes = new HashMap<>();

        public Map<String, Integer> getOperationCounts() {
            return operationCounts;
        }

        public Map<String, Long> getOperationTimes() {
            return operationTimes;
        }

        public DbMetricsResponse toResponse() {
            DbMetricsResponse response = new DbMetricsResponse();
            response.setInsertCount(operationCounts.getOrDefault("insert", 0));
            response.setUpdateCount(operationCounts.getOrDefault("update", 0));
            response.setReadCount(operationCounts.getOrDefault("read", 0));
            response.setDeleteCount(operationCounts.getOrDefault("delete", 0));

            response.setTotalInsertTime(operationTimes.getOrDefault("insert", 0L));
            response.setTotalUpdateTime(operationTimes.getOrDefault("update", 0L));
            response.setTotalReadTime(operationTimes.getOrDefault("read", 0L));
            response.setTotalDeleteTime(operationTimes.getOrDefault("delete", 0L));
            return response;
        }
    }

    // Response class representing metrics in the desired format
    public static class ApiMetricsResponse {
        private int apiCallCount;
        private DbMetricsResponse cassandraMetrics;
        private DbMetricsResponse redisMetrics;
        private DbMetricsResponse elasticsearchMetrics;
        private DbMetricsResponse postgresMetrics;

        // Getters and setters
        public int getApiCallCount() {
            return apiCallCount;
        }

        public void setApiCallCount(int apiCallCount) {
            this.apiCallCount = apiCallCount;
        }

        public DbMetricsResponse getCassandraMetrics() {
            return cassandraMetrics;
        }

        public void setCassandraMetrics(DbMetricsResponse cassandraMetrics) {
            this.cassandraMetrics = cassandraMetrics;
        }

        public DbMetricsResponse getRedisMetrics() {
            return redisMetrics;
        }

        public void setRedisMetrics(DbMetricsResponse redisMetrics) {
            this.redisMetrics = redisMetrics;
        }

        public DbMetricsResponse getElasticsearchMetrics() {
            return elasticsearchMetrics;
        }

        public void setElasticsearchMetrics(DbMetricsResponse elasticsearchMetrics) {
            this.elasticsearchMetrics = elasticsearchMetrics;
        }

        public DbMetricsResponse getPostgresMetrics() {
            return postgresMetrics;
        }

        public void setPostgresMetrics(DbMetricsResponse postgresMetrics) {
            this.postgresMetrics = postgresMetrics;
        }
    }

    // Response class for database-specific metrics
    public static class DbMetricsResponse {
        private int insertCount;
        private int updateCount;
        private int readCount;
        private int deleteCount;
        private long totalInsertTime;
        private long totalUpdateTime;
        private long totalReadTime;
        private long totalDeleteTime;

        // Getters and setters
        public int getInsertCount() {
            return insertCount;
        }

        public void setInsertCount(int insertCount) {
            this.insertCount = insertCount;
        }

        public int getUpdateCount() {
            return updateCount;
        }

        public void setUpdateCount(int updateCount) {
            this.updateCount = updateCount;
        }

        public int getReadCount() {
            return readCount;
        }

        public void setReadCount(int readCount) {
            this.readCount = readCount;
        }

        public int getDeleteCount() {
            return deleteCount;
        }

        public void setDeleteCount(int deleteCount) {
            this.deleteCount = deleteCount;
        }

        public long getTotalInsertTime() {
            return totalInsertTime;
        }

        public void setTotalInsertTime(long totalInsertTime) {
            this.totalInsertTime = totalInsertTime;
        }

        public long getTotalUpdateTime() {
            return totalUpdateTime;
        }

        public void setTotalUpdateTime(long totalUpdateTime) {
            this.totalUpdateTime = totalUpdateTime;
        }

        public long getTotalReadTime() {
            return totalReadTime;
        }

        public void setTotalReadTime(long totalReadTime) {
            this.totalReadTime = totalReadTime;
        }

        public long getTotalDeleteTime() {
            return totalDeleteTime;
        }

        public void setTotalDeleteTime(long totalDeleteTime) {
            this.totalDeleteTime = totalDeleteTime;
        }
    }

}
