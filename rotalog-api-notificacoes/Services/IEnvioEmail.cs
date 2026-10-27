namespace api_notificacoes.Services;

/// <summary>
/// Abstração do envio de e-mail, para isolar o envio simulado e permitir
/// resultados previsíveis nos testes. Lança exceção quando o envio falha.
/// </summary>
public interface IEnvioEmail
{
    Task EnviarAsync(string destinatario, string? assunto, string mensagem);
}
