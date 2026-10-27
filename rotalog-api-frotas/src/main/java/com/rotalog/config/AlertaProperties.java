package com.rotalog.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * Configuração do alerta de manutenção preventiva (prefixo rotalog.alertas).
 * O e-mail do gestor vem da variável de ambiente ROTALOG_EMAIL_GESTOR.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rotalog.alertas")
public class AlertaProperties {

    /** Elegível quando a km desde a última preventiva é maior que este valor. */
    private Long limiteKm = 50000L;

    /** Elegível quando a última preventiva foi há mais de N meses. */
    private Integer intervaloMeses = 6;

    private String emailGestor;

    @PostConstruct
    void validar() {
        if (emailGestor == null || emailGestor.trim().isEmpty()) {
            throw new IllegalStateException(
                "A variável de ambiente ROTALOG_EMAIL_GESTOR não está definida. "
                + "Defina o e-mail do gestor que receberá os alertas de manutenção preventiva.");
        }
    }
}
