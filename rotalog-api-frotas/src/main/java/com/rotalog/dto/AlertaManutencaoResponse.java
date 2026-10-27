package com.rotalog.dto;

import com.rotalog.domain.AlertaManutencao;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Alerta de manutenção com a placa do veículo, para listagem
 */
@Getter
@Setter
@NoArgsConstructor
public class AlertaManutencaoResponse {
    private Long id;
    private Long veiculoId;
    private String placa;
    private String motivo;
    private Long kmDesdeUltimaPreventiva;
    private LocalDateTime dataReferencia;
    private String status;
    private Long notificacaoId;
    private String mensagemErro;
    private LocalDateTime dataCriacao;

    public static AlertaManutencaoResponse de(AlertaManutencao alerta, String placa) {
        AlertaManutencaoResponse response = new AlertaManutencaoResponse();
        response.setId(alerta.getId());
        response.setVeiculoId(alerta.getVeiculoId());
        response.setPlaca(placa);
        response.setMotivo(alerta.getMotivo().name());
        response.setKmDesdeUltimaPreventiva(alerta.getKmDesdeUltimaPreventiva());
        response.setDataReferencia(alerta.getDataReferencia());
        response.setStatus(alerta.getStatus().name());
        response.setNotificacaoId(alerta.getNotificacaoId());
        response.setMensagemErro(alerta.getMensagemErro());
        response.setDataCriacao(alerta.getDataCriacao());
        return response;
    }
}
