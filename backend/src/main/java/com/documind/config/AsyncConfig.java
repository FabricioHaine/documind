package com.documind.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Executor dedicado ao processamento de documentos.
 *
 * Limitacao documentada (MVP): o executor e em memoria — se a aplicacao for
 * reiniciada no meio de um processamento, o DocumentProcessingScheduler
 * reenfileira tarefas pendentes ao iniciar (recovery basico).
 */
@Configuration
public class AsyncConfig {

    public static final String DOCUMENT_PROCESSING_EXECUTOR = "documentProcessingExecutor";

    @Bean(name = DOCUMENT_PROCESSING_EXECUTOR)
    public Executor documentProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("doc-process-");
        executor.initialize();
        return executor;
    }
}
