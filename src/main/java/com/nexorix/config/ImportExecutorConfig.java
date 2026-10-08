package com.nexorix.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Trabajadores que leen los extractos en segundo plano.
 *
 * Numero FIJO de hilos + cola con limite: si llegan miles de archivos,
 * esperan su turno; si la cola se llena, se rechazan con un mensaje claro
 * en vez de tumbar el servidor.
 */
@Configuration
@EnableScheduling
public class ImportExecutorConfig {

    @Bean(name = "importExecutor")
    public ThreadPoolTaskExecutor importExecutor(
            @Value("${nexorix.import.workers:4}") int workers,
            @Value("${nexorix.import.max-workers:8}") int maxWorkers,
            @Value("${nexorix.import.queue-capacity:300}") int queueCapacity
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(workers);
        executor.setMaxPoolSize(maxWorkers);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("importar-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
