package com.igot.cb.pores.config;

import com.igot.cb.pores.util.CbServerProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {
    @Autowired
    private CbServerProperties cbServerProperties;

    @Bean

    public ThreadPoolTaskExecutor taskExecutor() {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(cbServerProperties.getAsyncThreadPoolSize());
        executor.setMaxPoolSize(cbServerProperties.getAsyncThreadMaxPoolSize());
        executor.setThreadNamePrefix("async-");
        executor.initialize();
        return executor;
    }
}