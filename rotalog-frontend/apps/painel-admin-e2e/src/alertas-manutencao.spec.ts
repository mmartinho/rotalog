import { test, expect, Page } from '@playwright/test';
import { execSync } from 'child_process';

/**
 * E2E do alerta de manutenção preventiva: painel-admin -> api-frotas -> api-notificacoes -> banco.
 *
 * Pré-requisitos e passo a passo: docs/e2e-alertas-manutencao.md
 *
 * - O banco precisa estar com o seed (scripts 09 e 10 do rotalog-workspace).
 * - O estado do banco é compartilhado: os testes limpam a tabela frotas.alertas_manutencao
 *   e rodam em série, apenas no chromium.
 * - O cenário "api-notificacoes fora do ar" só roda com E2E_NOTIFICACOES_DOWN=true
 *   (com a api-notificacoes desligada); o fluxo feliz roda sem essa variável.
 */

const FROTAS_API = process.env['FROTAS_API_URL'] ?? 'http://localhost:8080/api';
const NOTIFICACOES_API = process.env['NOTIFICACOES_API_URL'] ?? 'http://localhost:5000/api';
const EMAIL_GESTOR = process.env['ROTALOG_EMAIL_GESTOR'] ?? 'gestor@rotalog.com';
const DB_CONTAINER = process.env['E2E_DB_CONTAINER'] ?? 'rotalog-db';
const NOTIFICACOES_FORA_DO_AR = process.env['E2E_NOTIFICACOES_DOWN'] === 'true';

// Placas do seed 10-seed-alertas-manutencao.sql
const PLACAS_ELEGIVEIS = ['ALK1A11', 'ALT2B22', 'ALX3C33', 'ALS5E55'];
const PLACAS_IGNORADAS = ['ALN4D44', 'ALI6F66']; // não elegível e inativo

test.describe.configure({ mode: 'serial' });

test.beforeEach(({ browserName }) => {
  test.skip(browserName !== 'chromium', 'Estado do banco compartilhado: roda só no chromium');
});

function limparAlertas(): void {
  execSync(
    `docker exec ${DB_CONTAINER} psql -U rotalog_admin -d rotalog -c "TRUNCATE frotas.alertas_manutencao RESTART IDENTITY"`,
    { stdio: 'pipe' }
  );
}

async function abrirTelaDeAlertas(page: Page): Promise<void> {
  await page.goto('/alertas-manutencao');
  await expect(page.getByRole('heading', { name: 'Alertas de Manutenção Preventiva' })).toBeVisible();
}

async function verificarAgora(page: Page): Promise<void> {
  await page.getByTestId('btn-verificar').click();
  await expect(page.getByTestId('resumo')).toBeVisible();
}

function linhaDaPlaca(page: Page, placa: string) {
  return page.locator('tbody tr', { hasText: placa });
}

test.describe('fluxo completo com a api-notificacoes no ar', () => {
  test.skip(NOTIFICACOES_FORA_DO_AR, 'Cenário para a api-notificacoes no ar');

  test.beforeAll(() => limparAlertas());

  test('verificação gera alertas ENVIADA e a notificação existe na api-notificacoes', async ({ page, request }) => {
    await abrirTelaDeAlertas(page);
    await expect(page.getByTestId('vazio')).toBeVisible();

    await verificarAgora(page);

    for (const placa of PLACAS_ELEGIVEIS) {
      await expect(linhaDaPlaca(page, placa).locator('.status-badge')).toHaveText('ENVIADA');
    }
    for (const placa of PLACAS_IGNORADAS) {
      await expect(linhaDaPlaca(page, placa)).toHaveCount(0);
    }
    await expect(linhaDaPlaca(page, 'ALK1A11')).toContainText('Quilometragem');
    await expect(linhaDaPlaca(page, 'ALT2B22')).toContainText('Tempo');
    await expect(linhaDaPlaca(page, 'ALX3C33')).toContainText('Quilometragem e tempo');

    // a notificação do alerta existe na api-notificacoes, com o e-mail do gestor
    const alertas = await (await request.get(`${FROTAS_API}/manutencoes/alertas?status=ENVIADA`)).json();
    const alerta = alertas.find((a: { placa: string }) => a.placa === 'ALK1A11');
    expect(alerta.notificacaoId).toBeTruthy();

    const resposta = await request.get(`${NOTIFICACOES_API}/notificacoes?tipo=ALERTA_MANUTENCAO&servicoOrigem=api-frotas`);
    expect(resposta.ok()).toBeTruthy();
    const notificacoes = await resposta.json();
    const notificacao = notificacoes.find((n: { id: number }) => n.id === alerta.notificacaoId);
    expect(notificacao).toBeDefined();
    expect(notificacao.destinatario).toBe(EMAIL_GESTOR);
    expect(notificacao.canal).toBe('email');
    expect(notificacao.status).toBe('ENVIADO');
    expect(notificacao.mensagem).toContain('ALK1A11');
  });

  test('verificar de novo não duplica os alertas (idempotência)', async ({ page }) => {
    await abrirTelaDeAlertas(page);
    const antes = await page.locator('tbody tr').count();

    await verificarAgora(page);

    await expect(page.getByTestId('resumo')).toContainText('Novos alertas: 0');
    await expect(page.locator('tbody tr')).toHaveCount(antes);
  });

  test('filtro por status na tela', async ({ page }) => {
    await abrirTelaDeAlertas(page);
    const filtro = page.getByTestId('filtro-status');

    await filtro.selectOption('PENDENTE');
    await expect(page.getByTestId('vazio')).toBeVisible();

    await filtro.selectOption('ENVIADA');
    await expect(linhaDaPlaca(page, 'ALK1A11')).toBeVisible();
    const badges = page.locator('tbody .status-badge');
    for (const texto of await badges.allTextContents()) {
      expect(texto.trim()).toBe('ENVIADA');
    }

    await filtro.selectOption('');
    await expect(linhaDaPlaca(page, 'ALK1A11')).toBeVisible();
  });
});

test.describe('api-notificacoes fora do ar', () => {
  test.skip(!NOTIFICACOES_FORA_DO_AR, 'Defina E2E_NOTIFICACOES_DOWN=true com a api-notificacoes desligada');

  test.beforeAll(() => limparAlertas());

  test('alertas são registrados como PENDENTE', async ({ page }) => {
    await abrirTelaDeAlertas(page);

    await verificarAgora(page);

    for (const placa of PLACAS_ELEGIVEIS) {
      await expect(linhaDaPlaca(page, placa).locator('.status-badge')).toHaveText('PENDENTE');
    }
    await expect(page.getByTestId('resumo')).toContainText('enviados: 0');

    await page.getByTestId('filtro-status').selectOption('ENVIADA');
    await expect(page.getByTestId('vazio')).toBeVisible();

    await page.getByTestId('filtro-status').selectOption('PENDENTE');
    await expect(linhaDaPlaca(page, 'ALK1A11').locator('.status-badge')).toHaveText('PENDENTE');
  });
});
