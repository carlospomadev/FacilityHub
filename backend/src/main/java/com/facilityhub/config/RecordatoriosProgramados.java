package com.facilityhub.config;

import com.facilityhub.api.dto.alerta.ResultadoRecordatoriosDto;
import com.facilityhub.service.ConflictoException;
import com.facilityhub.service.RecordatorioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

// FH-006 / P6-D2: tarea diaria de recordatorios. Solo existe si facilityhub.recordatorios.enabled=true (por defecto
// false): sin la propiedad no se activa la planificacion ni se escribe nada. Cron en Europe/Madrid; nunca al arrancar.
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "facilityhub.recordatorios", name = "enabled", havingValue = "true")
public class RecordatoriosProgramados {
    private static final Logger log = LoggerFactory.getLogger(RecordatoriosProgramados.class);
    private final RecordatorioService recordatorioService;

    public RecordatoriosProgramados(RecordatorioService recordatorioService) {
        this.recordatorioService = recordatorioService;
    }

    @Scheduled(cron = "${facilityhub.recordatorios.cron:0 0 7 * * *}", zone = "Europe/Madrid")
    public void ejecutarDiario() {
        try {
            ResultadoRecordatoriosDto r = recordatorioService.ejecutar(false);
            log.info("Recordatorios {}: candidatos={} proximos={} vencidos={} validacionPendiente={} avisosCreados={}",
                r.fecha(), r.candidatos(), r.proximos(), r.vencidos(), r.validacionPendiente(), r.avisos());
        } catch (ConflictoException e) {
            log.info("Recordatorios omitidos: ejecucion en curso");
        }
    }
}
