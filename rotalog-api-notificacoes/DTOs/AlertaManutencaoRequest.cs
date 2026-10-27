namespace api_notificacoes.DTOs;

/// <summary>
/// DTO de pedido de alerta de manutenção preventiva (enviado pela api-frotas)
/// </summary>
public class AlertaManutencaoRequest
{
    public long VeiculoId { get; set; }
    public string Placa { get; set; } = string.Empty;

    // KM, TEMPO ou KM_E_TEMPO
    public string Motivo { get; set; } = string.Empty;
    public long KmDesdeUltimaPreventiva { get; set; }
    public DateTime? DataReferencia { get; set; }
    public string EmailGestor { get; set; } = string.Empty;
}
