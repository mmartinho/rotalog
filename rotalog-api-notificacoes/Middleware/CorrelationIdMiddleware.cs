using System.Text.RegularExpressions;

namespace api_notificacoes.Middleware;

/// <summary>
/// Lê o X-Correlation-ID enviado pela api-frotas (ou gera um, se ausente/inválido), devolve no response
/// e abre um escopo de log com "CorrelationId" para que todos os logs da requisição o incluam.
/// </summary>
public class CorrelationIdMiddleware
{
    public const string HeaderName = "X-Correlation-ID";

    private static readonly Regex FormatoValido = new("^[A-Za-z0-9\\-_.]{1,64}$", RegexOptions.Compiled);

    private readonly RequestDelegate _next;
    private readonly ILogger<CorrelationIdMiddleware> _logger;

    public CorrelationIdMiddleware(RequestDelegate next, ILogger<CorrelationIdMiddleware> logger)
    {
        _next = next;
        _logger = logger;
    }

    public async Task InvokeAsync(HttpContext context)
    {
        var recebido = context.Request.Headers[HeaderName].FirstOrDefault();
        var correlationId = recebido != null && FormatoValido.IsMatch(recebido)
            ? recebido
            : Guid.NewGuid().ToString();

        context.Response.Headers[HeaderName] = correlationId;

        using (_logger.BeginScope(new Dictionary<string, object> { ["CorrelationId"] = correlationId }))
        {
            await _next(context);
        }
    }
}
