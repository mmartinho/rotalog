namespace api_notificacoes.DTOs;

/// <summary>
/// DTO de resposta do alerta de manutenção preventiva
/// Status do contrato: ENVIADA ou FALHA
/// </summary>
public class AlertaManutencaoResponse
{
    public long NotificacaoId { get; set; }
    public string Status { get; set; } = string.Empty;
    public string? MensagemErro { get; set; }
}
