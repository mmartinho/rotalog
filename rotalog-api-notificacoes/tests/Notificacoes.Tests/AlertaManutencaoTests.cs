using api_notificacoes.Controllers;
using api_notificacoes.Data;
using api_notificacoes.DTOs;
using api_notificacoes.Models;
using api_notificacoes.Services;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging.Abstractions;
using Moq;
using System.Text.Json;
using Xunit;

namespace Notificacoes.Tests;

public class AlertaManutencaoTests
{
    private static NotificacoesDbContext CriarContexto()
    {
        var options = new DbContextOptionsBuilder<NotificacoesDbContext>()
            .UseInMemoryDatabase(Guid.NewGuid().ToString())
            .Options;
        return new NotificacoesDbContext(options);
    }

    private static NotificacaoService CriarService(NotificacoesDbContext context, IEnvioEmail envioEmail)
    {
        return new NotificacaoService(
            context,
            NullLogger<NotificacaoService>.Instance,
            new ConfigurationBuilder().Build(),
            envioEmail);
    }

    private static NotificacoesController CriarController(NotificacaoService service)
    {
        return new NotificacoesController(service, NullLogger<NotificacoesController>.Instance);
    }

    private static AlertaManutencaoRequest RequestValido() => new()
    {
        VeiculoId = 7,
        Placa = "ABC1D23",
        Motivo = "KM_E_TEMPO",
        KmDesdeUltimaPreventiva = 60000,
        DataReferencia = new DateTime(2024, 1, 15),
        EmailGestor = "gestor@rotalog.com"
    };

    // ---------- Validação do request (controller) ----------

    [Fact]
    public async Task Controller_RequestValido_Retorna200()
    {
        var envio = new Mock<IEnvioEmail>();
        using var context = CriarContexto();
        var controller = CriarController(CriarService(context, envio.Object));

        var resultado = await controller.CriarAlertaManutencao(RequestValido());

        var ok = Assert.IsType<OkObjectResult>(resultado);
        Assert.IsType<AlertaManutencaoResponse>(ok.Value);
    }

    [Theory]
    [InlineData("veiculoId")]
    [InlineData("placa")]
    [InlineData("motivo")]
    [InlineData("emailGestor")]
    public async Task Controller_CampoObrigatorioAusente_Retorna400(string campo)
    {
        var envio = new Mock<IEnvioEmail>();
        using var context = CriarContexto();
        var controller = CriarController(CriarService(context, envio.Object));
        var request = RequestValido();
        switch (campo)
        {
            case "veiculoId": request.VeiculoId = 0; break;
            case "placa": request.Placa = ""; break;
            case "motivo": request.Motivo = " "; break;
            case "emailGestor": request.EmailGestor = ""; break;
        }

        var resultado = await controller.CriarAlertaManutencao(request);

        Assert.IsType<BadRequestObjectResult>(resultado);
        envio.Verify(e => e.EnviarAsync(It.IsAny<string>(), It.IsAny<string?>(), It.IsAny<string>()), Times.Never);
        Assert.Empty(context.Notificacoes);
    }

    // ---------- Envio ----------

    [Fact]
    public async Task Service_EnvioComSucesso_RetornaEnviada()
    {
        var envio = new Mock<IEnvioEmail>();
        using var context = CriarContexto();
        var service = CriarService(context, envio.Object);

        var resposta = await service.CriarAlertaManutencao(RequestValido());

        Assert.Equal("ENVIADA", resposta.Status);
        Assert.Null(resposta.MensagemErro);
        envio.Verify(e => e.EnviarAsync("gestor@rotalog.com", It.IsAny<string?>(), It.IsAny<string>()), Times.Once);
    }

    [Fact]
    public async Task Service_EnvioComFalha_RetornaFalhaSemLancarExcecao()
    {
        var envio = new Mock<IEnvioEmail>();
        envio.Setup(e => e.EnviarAsync(It.IsAny<string>(), It.IsAny<string?>(), It.IsAny<string>()))
            .ThrowsAsync(new Exception("SMTP connection timeout"));
        using var context = CriarContexto();
        var service = CriarService(context, envio.Object);

        var resposta = await service.CriarAlertaManutencao(RequestValido());

        Assert.Equal("FALHA", resposta.Status);
        Assert.Equal("SMTP connection timeout", resposta.MensagemErro);
    }

    [Fact]
    public async Task Controller_EnvioComFalha_Retorna200ComStatusFalha()
    {
        var envio = new Mock<IEnvioEmail>();
        envio.Setup(e => e.EnviarAsync(It.IsAny<string>(), It.IsAny<string?>(), It.IsAny<string>()))
            .ThrowsAsync(new Exception("falha"));
        using var context = CriarContexto();
        var controller = CriarController(CriarService(context, envio.Object));

        var resultado = await controller.CriarAlertaManutencao(RequestValido());

        var ok = Assert.IsType<OkObjectResult>(resultado);
        Assert.Equal("FALHA", ((AlertaManutencaoResponse)ok.Value!).Status);
    }

    // ---------- Histórico ----------

    [Fact]
    public async Task Service_GravaHistoricoComTipoEOrigemCorretos()
    {
        var envio = new Mock<IEnvioEmail>();
        using var context = CriarContexto();
        var service = CriarService(context, envio.Object);

        var resposta = await service.CriarAlertaManutencao(RequestValido());

        var notificacao = await context.Notificacoes.SingleAsync();
        Assert.Equal(resposta.NotificacaoId, notificacao.Id);
        Assert.Equal("ALERTA_MANUTENCAO", notificacao.Tipo);
        Assert.Equal("api-frotas", notificacao.ServicoOrigem);
        Assert.Equal("email", notificacao.Canal);
        Assert.Equal("gestor@rotalog.com", notificacao.Destinatario);
        Assert.Equal("veiculo-7", notificacao.ReferenciaId);
        Assert.Equal("ENVIADO", notificacao.Status);
        Assert.NotNull(notificacao.DataEnvio);
    }

    [Fact]
    public async Task Service_FalhaNoEnvio_GravaNotificacaoComoFalha()
    {
        var envio = new Mock<IEnvioEmail>();
        envio.Setup(e => e.EnviarAsync(It.IsAny<string>(), It.IsAny<string?>(), It.IsAny<string>()))
            .ThrowsAsync(new Exception("falha"));
        using var context = CriarContexto();
        var service = CriarService(context, envio.Object);

        await service.CriarAlertaManutencao(RequestValido());

        var notificacao = await context.Notificacoes.SingleAsync();
        Assert.Equal("FALHA", notificacao.Status);
        Assert.Equal("falha", notificacao.ErroMensagem);
        Assert.Equal(1, notificacao.Tentativas);
        Assert.Null(notificacao.DataEnvio);
    }

    // ---------- Mensagem ----------

    [Fact]
    public async Task Service_SemTemplate_UsaTextoPadrao()
    {
        string? assuntoEnviado = null;
        string? mensagemEnviada = null;
        var envio = new Mock<IEnvioEmail>();
        envio.Setup(e => e.EnviarAsync(It.IsAny<string>(), It.IsAny<string?>(), It.IsAny<string>()))
            .Callback<string, string?, string>((_, assunto, mensagem) =>
            {
                assuntoEnviado = assunto;
                mensagemEnviada = mensagem;
            })
            .Returns(Task.CompletedTask);
        using var context = CriarContexto();
        var service = CriarService(context, envio.Object);

        await service.CriarAlertaManutencao(RequestValido());

        Assert.Contains("ABC1D23", assuntoEnviado);
        Assert.Contains("KM_E_TEMPO", mensagemEnviada);
        Assert.Contains("60000", mensagemEnviada);
    }

    [Fact]
    public async Task Service_ComTemplate_AplicaVariaveis()
    {
        string? assuntoEnviado = null;
        string? mensagemEnviada = null;
        var envio = new Mock<IEnvioEmail>();
        envio.Setup(e => e.EnviarAsync(It.IsAny<string>(), It.IsAny<string?>(), It.IsAny<string>()))
            .Callback<string, string?, string>((_, assunto, mensagem) =>
            {
                assuntoEnviado = assunto;
                mensagemEnviada = mensagem;
            })
            .Returns(Task.CompletedTask);
        using var context = CriarContexto();
        context.Templates.Add(new TemplateNotificacao
        {
            Tipo = "ALERTA_MANUTENCAO_PREVENTIVA",
            Canal = "email",
            AssuntoTemplate = "Preventiva {{placa}}",
            CorpoTemplate = "{{placa}} / {{motivo}} / {{km_desde_ultima_preventiva}} / {{data_referencia}}",
            Ativo = true
        });
        await context.SaveChangesAsync();
        var service = CriarService(context, envio.Object);

        await service.CriarAlertaManutencao(RequestValido());

        Assert.Equal("Preventiva ABC1D23", assuntoEnviado);
        Assert.Equal("ABC1D23 / KM_E_TEMPO / 60000 / 15/01/2024", mensagemEnviada);
    }

    // ---------- Validação do motivo ----------

    [Theory]
    [InlineData("KM")]
    [InlineData("TEMPO")]
    [InlineData("KM_E_TEMPO")]
    public async Task Controller_MotivoDoContrato_Retorna200(string motivo)
    {
        var envio = new Mock<IEnvioEmail>();
        using var context = CriarContexto();
        var controller = CriarController(CriarService(context, envio.Object));
        var request = RequestValido();
        request.Motivo = motivo;

        var resultado = await controller.CriarAlertaManutencao(request);

        Assert.IsType<OkObjectResult>(resultado);
    }

    [Theory]
    [InlineData("km")]
    [InlineData("PREVENTIVA")]
    [InlineData("KM E TEMPO")]
    public async Task Controller_MotivoForaDoContrato_Retorna400SemEnviar(string motivo)
    {
        var envio = new Mock<IEnvioEmail>();
        using var context = CriarContexto();
        var controller = CriarController(CriarService(context, envio.Object));
        var request = RequestValido();
        request.Motivo = motivo;

        var resultado = await controller.CriarAlertaManutencao(request);

        Assert.IsType<BadRequestObjectResult>(resultado);
        envio.Verify(e => e.EnviarAsync(It.IsAny<string>(), It.IsAny<string?>(), It.IsAny<string>()), Times.Never);
        Assert.Empty(context.Notificacoes);
    }

    // ---------- Contrato JSON com a api-frotas (NotificacaoClient / AlertaManutencaoPayload) ----------

    // Mesmas opções de JSON que o ASP.NET Core usa nos controllers (camelCase, case-insensitive)
    private static readonly JsonSerializerOptions OpcoesJson = new(JsonSerializerDefaults.Web);

    // Corpo gerado pelo Jackson (api-frotas) para AlertaManutencaoPayload, com write-dates-as-timestamps=false
    private const string PayloadJava =
        "{\"veiculoId\":7,\"placa\":\"ABC1D23\",\"motivo\":\"KM\",\"kmDesdeUltimaPreventiva\":60000," +
        "\"dataReferencia\":\"2026-08-16T10:00:00\",\"emailGestor\":\"gestor@rotalog.com\"}";

    [Fact]
    public void Json_PayloadDaApiFrotas_DesserializaNoRequest()
    {
        var request = JsonSerializer.Deserialize<AlertaManutencaoRequest>(PayloadJava, OpcoesJson);

        Assert.NotNull(request);
        Assert.Equal(7L, request!.VeiculoId);
        Assert.Equal("ABC1D23", request.Placa);
        Assert.Equal("KM", request.Motivo);
        Assert.Equal(60000L, request.KmDesdeUltimaPreventiva);
        Assert.Equal(new DateTime(2026, 8, 16, 10, 0, 0), request.DataReferencia);
        Assert.Equal("gestor@rotalog.com", request.EmailGestor);
    }

    [Fact]
    public async Task Json_PayloadDaApiFrotas_RetornaRespostaComCamposDoContrato()
    {
        var envio = new Mock<IEnvioEmail>();
        using var context = CriarContexto();
        var controller = CriarController(CriarService(context, envio.Object));
        var request = JsonSerializer.Deserialize<AlertaManutencaoRequest>(PayloadJava, OpcoesJson)!;

        var resultado = await controller.CriarAlertaManutencao(request);

        var ok = Assert.IsType<OkObjectResult>(resultado);
        var json = JsonSerializer.Serialize(ok.Value, OpcoesJson);
        using var doc = JsonDocument.Parse(json);
        var raiz = doc.RootElement;
        Assert.Equal("ENVIADA", raiz.GetProperty("status").GetString());
        Assert.True(raiz.GetProperty("notificacaoId").GetInt64() > 0);
        Assert.Equal(JsonValueKind.Null, raiz.GetProperty("mensagemErro").ValueKind);
    }

    [Fact]
    public async Task Json_RespostaDeFalha_TemStatusFalhaEMensagemErro()
    {
        var envio = new Mock<IEnvioEmail>();
        envio.Setup(e => e.EnviarAsync(It.IsAny<string>(), It.IsAny<string?>(), It.IsAny<string>()))
            .ThrowsAsync(new Exception("SMTP connection timeout"));
        using var context = CriarContexto();
        var controller = CriarController(CriarService(context, envio.Object));
        var request = JsonSerializer.Deserialize<AlertaManutencaoRequest>(PayloadJava, OpcoesJson)!;

        var resultado = await controller.CriarAlertaManutencao(request);

        var ok = Assert.IsType<OkObjectResult>(resultado);
        var json = JsonSerializer.Serialize(ok.Value, OpcoesJson);
        using var doc = JsonDocument.Parse(json);
        Assert.Equal("FALHA", doc.RootElement.GetProperty("status").GetString());
        Assert.Equal("SMTP connection timeout", doc.RootElement.GetProperty("mensagemErro").GetString());
    }
}
