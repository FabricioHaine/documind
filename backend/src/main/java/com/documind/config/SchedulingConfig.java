package com.documind.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita o scheduler que reenfileira tarefas de processamento pendentes
 * apos reinicializacoes (recovery do executor em memoria).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
