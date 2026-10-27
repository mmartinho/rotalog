import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AlertasManutencaoComponent } from './alertas-manutencao.component';
import { FrotasService } from '../../services/frotas.service';
import { AlertaManutencao } from '../../models';

describe('AlertasManutencaoComponent', () => {
  let fixture: ComponentFixture<AlertasManutencaoComponent>;
  let frotasService: { listarAlertas: jest.Mock; verificarAlertas: jest.Mock };

  const alerta = (parcial: Partial<AlertaManutencao>): AlertaManutencao => ({
    id: 1,
    veiculoId: 1,
    placa: 'ABC1D23',
    motivo: 'KM',
    kmDesdeUltimaPreventiva: 60000,
    dataReferencia: '2026-08-16T10:00:00',
    status: 'ENVIADA',
    notificacaoId: 10,
    mensagemErro: null,
    dataCriacao: '2026-10-16T12:00:00',
    ...parcial
  });

  const texto = (seletor: string): string =>
    (fixture.nativeElement.querySelector(seletor)?.textContent ?? '').trim();

  const renderizar = async () => {
    fixture = TestBed.createComponent(AlertasManutencaoComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  };

  beforeEach(() => {
    frotasService = {
      listarAlertas: jest.fn().mockResolvedValue([]),
      verificarAlertas: jest.fn().mockResolvedValue({
        verificados: 8, elegiveis: 5, novosAlertas: 4, enviados: 3, falhas: 0, pendentes: 1
      })
    };
    TestBed.configureTestingModule({
      imports: [AlertasManutencaoComponent],
      providers: [{ provide: FrotasService, useValue: frotasService }]
    });
  });

  it('mostra o estado de carregando antes da resposta', () => {
    frotasService.listarAlertas.mockReturnValue(new Promise(() => undefined));

    fixture = TestBed.createComponent(AlertasManutencaoComponent);
    fixture.detectChanges();

    expect(texto('[data-testid="loading"]')).toContain('Carregando');
  });

  it('renderiza a lista de alertas', async () => {
    frotasService.listarAlertas.mockResolvedValue([
      alerta({ id: 1, placa: 'ABC1D23', motivo: 'KM' }),
      alerta({ id: 2, placa: 'DEF4G56', motivo: 'TEMPO', status: 'PENDENTE' })
    ]);

    await renderizar();

    const linhas = fixture.nativeElement.querySelectorAll('tbody tr');
    expect(linhas.length).toBe(2);
    expect(linhas[0].textContent).toContain('ABC1D23');
    expect(linhas[0].textContent).toContain('Quilometragem');
    expect(linhas[0].textContent).toContain('60,000');
    expect(linhas[1].textContent).toContain('DEF4G56');
    expect(linhas[1].textContent).toContain('Tempo');
  });

  it('mostra o status em um badge com a classe da cor', async () => {
    frotasService.listarAlertas.mockResolvedValue([
      alerta({ id: 1, status: 'ENVIADA' }),
      alerta({ id: 2, status: 'FALHA' }),
      alerta({ id: 3, status: 'PENDENTE' })
    ]);

    await renderizar();

    const badges = fixture.nativeElement.querySelectorAll('.status-badge');
    expect(badges.length).toBe(3);
    expect(badges[0].classList).toContain('status-ENVIADA');
    expect(badges[1].classList).toContain('status-FALHA');
    expect(badges[2].classList).toContain('status-PENDENTE');
    expect(badges[2].textContent.trim()).toBe('PENDENTE');
  });

  it('mostra o estado de lista vazia', async () => {
    await renderizar();

    expect(texto('[data-testid="vazio"]')).toContain('Nenhum alerta encontrado');
    expect(fixture.nativeElement.querySelector('table')).toBeNull();
  });

  it('mostra o estado de erro quando a API falha', async () => {
    jest.spyOn(console, 'error').mockImplementation(() => undefined);
    frotasService.listarAlertas.mockRejectedValue(new Error('falhou'));

    await renderizar();

    expect(texto('[data-testid="erro"]')).toContain('Não foi possível carregar os alertas');
    expect(fixture.nativeElement.querySelector('[data-testid="vazio"]')).toBeNull();
  });

  it('carrega todos os alertas, sem filtro, ao abrir', async () => {
    await renderizar();

    expect(frotasService.listarAlertas).toHaveBeenCalledWith(undefined);
  });

  it('filtra por status ao trocar o filtro', async () => {
    await renderizar();
    const select: HTMLSelectElement = fixture.nativeElement.querySelector('[data-testid="filtro-status"]');

    select.value = 'PENDENTE';
    select.dispatchEvent(new Event('change'));
    await fixture.whenStable();

    expect(frotasService.listarAlertas).toHaveBeenLastCalledWith('PENDENTE');
  });

  it('volta a listar todos ao escolher "Todos"', async () => {
    await renderizar();
    const select: HTMLSelectElement = fixture.nativeElement.querySelector('[data-testid="filtro-status"]');
    select.value = 'FALHA';
    select.dispatchEvent(new Event('change'));
    await fixture.whenStable();

    select.value = '';
    select.dispatchEvent(new Event('change'));
    await fixture.whenStable();

    expect(frotasService.listarAlertas).toHaveBeenLastCalledWith(undefined);
  });

  it('"Verificar agora" dispara a verificação, mostra o resumo e recarrega a lista', async () => {
    await renderizar();
    frotasService.listarAlertas.mockClear();

    fixture.nativeElement.querySelector('[data-testid="btn-verificar"]').click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(frotasService.verificarAlertas).toHaveBeenCalledTimes(1);
    expect(frotasService.listarAlertas).toHaveBeenCalledTimes(1);
    expect(texto('[data-testid="resumo"]')).toContain('Novos alertas: 4');
  });

  it('mostra o erro quando a verificação falha', async () => {
    jest.spyOn(console, 'error').mockImplementation(() => undefined);
    await renderizar();
    frotasService.verificarAlertas.mockRejectedValue(new Error('falhou'));

    fixture.nativeElement.querySelector('[data-testid="btn-verificar"]').click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(texto('[data-testid="erro"]')).toContain('Não foi possível verificar os alertas');
  });
});
