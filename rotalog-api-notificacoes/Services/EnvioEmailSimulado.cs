namespace api_notificacoes.Services;

/// <summary>
/// Envio de e-mail simulado (não envia e-mail de verdade)
///
/// FIXME: Não envia email de verdade
/// FIXME: Credenciais SMTP no appsettings.json
/// </summary>
public class EnvioEmailSimulado : IEnvioEmail
{
    private readonly IConfiguration _configuration;
    private readonly ILogger<EnvioEmailSimulado> _logger;

    public EnvioEmailSimulado(IConfiguration configuration, ILogger<EnvioEmailSimulado> logger)
    {
        _configuration = configuration;
        _logger = logger;
    }

    public async Task EnviarAsync(string destinatario, string? assunto, string mensagem)
    {
        var smtpServer = _configuration["EmailSettings:SmtpServer"];
        var smtpPort = _configuration["EmailSettings:SmtpPort"];
        var senderEmail = _configuration["EmailSettings:SenderEmail"];

        _logger.LogInformation("[EMAIL FAKE] Enviando para {Dest} via {Smtp}:{Port}",
            destinatario, smtpServer, smtpPort);
        _logger.LogInformation("[EMAIL FAKE] De: {From} | Assunto: {Subject}",
            senderEmail, assunto);
        _logger.LogInformation("[EMAIL FAKE] Corpo: {Body}",
            mensagem.Length > 100 ? mensagem.Substring(0, 100) + "..." : mensagem);

        // FIXME: Simula delay de envio
        await Task.Delay(100);

        // FIXME: Simula falha aleatória (10% por padrão; EmailSettings:SimulatedFailureRate=0 desliga, útil no E2E)
        var taxaFalha = _configuration.GetValue<double>("EmailSettings:SimulatedFailureRate", 0.1);
        if (Random.Shared.NextDouble() < taxaFalha)
        {
            throw new Exception("SMTP connection timeout (simulated)");
        }
    }
}
