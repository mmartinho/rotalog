package com.rotalog.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * AlertaManutencao entity - alerta de manutenção preventiva enviado ao gestor
 *
 * FIXME: Sem FK constraint no banco (veiculo_id é só BIGINT)
 */
@Entity
@Table(name = "alertas_manutencao")
@Getter
@Setter
@NoArgsConstructor
public class AlertaManutencao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "veiculo_id", nullable = false)
    private Long veiculoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo", nullable = false)
    private MotivoAlerta motivo;

    @Column(name = "km_desde_ultima_preventiva")
    private Long kmDesdeUltimaPreventiva;

    @Column(name = "data_referencia")
    private LocalDateTime dataReferencia; // última preventiva ou data de cadastro do veículo

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StatusAlerta status;

    @Column(name = "notificacao_id")
    private Long notificacaoId;

    @Column(name = "mensagem_erro")
    private String mensagemErro;

    @Column(name = "data_criacao")
    private LocalDateTime dataCriacao;

    @Column(name = "data_atualizacao")
    private LocalDateTime dataAtualizacao;
}
