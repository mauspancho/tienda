package com.tienda.pos.whatsapp;

import com.tienda.pos.common.NormalMode;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.core.task.TaskExecutor;

@Configuration
@NormalMode
@EnableConfigurationProperties(WhatsappProperties.class)
public class WhatsappConfiguration {
    @Bean(name = "whatsappGroupSyncExecutor")
    public TaskExecutor whatsappGroupSyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("whatsapp-groups-");
        executor.initialize();
        return executor;
    }
}
