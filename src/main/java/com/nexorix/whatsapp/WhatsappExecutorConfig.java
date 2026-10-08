package com.nexorix.whatsapp;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Hilos para WhatsApp: mandar las preguntas y procesar las notas de voz
 * sin hacer esperar a la persona ni a Meta (que exige responder el webhook rapido).
 */
@Configuration
public class WhatsappExecutorConfig {

    @Bean(name = "whatsappExecutor")
    public ThreadPoolTaskExecutor whatsappExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("whatsapp-");
        // Si la cola se llena, lo hace el hilo que llego: mas lento, pero no se pierde nada.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
