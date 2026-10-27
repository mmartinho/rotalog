import { TestBed } from '@angular/core/testing';
import { FrotasService } from './frotas.service';

describe('FrotasService - alertas de manutenção', () => {
  let service: FrotasService;
  let fetchMock: jest.Mock;

  const respostaOk = (corpo: unknown) =>
    Promise.resolve({ ok: true, json: () => Promise.resolve(corpo) } as Response);

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(FrotasService);
    fetchMock = jest.fn();
    (globalThis as any).fetch = fetchMock;
  });

  describe('listarAlertas', () => {
    it('sem filtro, busca todos os alertas', async () => {
      const alertas = [{ id: 1, placa: 'ABC1D23', status: 'ENVIADA' }];
      fetchMock.mockReturnValue(respostaOk(alertas));

      const resultado = await service.listarAlertas();

      expect(fetchMock).toHaveBeenCalledWith('http://localhost:8080/api/manutencoes/alertas');
      expect(resultado).toEqual(alertas);
    });

    it('com filtro, envia o status na query', async () => {
      fetchMock.mockReturnValue(respostaOk([]));

      await service.listarAlertas('PENDENTE');

      expect(fetchMock).toHaveBeenCalledWith('http://localhost:8080/api/manutencoes/alertas?status=PENDENTE');
    });

    it('propaga o erro quando a API responde com falha', async () => {
      fetchMock.mockReturnValue(Promise.resolve({ ok: false } as Response));

      await expect(service.listarAlertas()).rejects.toThrow('Erro ao buscar alertas de manutenção');
    });

    it('propaga o erro quando a API está fora do ar', async () => {
      fetchMock.mockRejectedValue(new TypeError('Failed to fetch'));

      await expect(service.listarAlertas()).rejects.toThrow('Failed to fetch');
    });
  });

  describe('verificarAlertas', () => {
    it('dispara a verificação por POST e devolve o resumo', async () => {
      const resumo = { verificados: 8, elegiveis: 5, novosAlertas: 4, enviados: 3, falhas: 0, pendentes: 1 };
      fetchMock.mockReturnValue(respostaOk(resumo));

      const resultado = await service.verificarAlertas();

      expect(fetchMock).toHaveBeenCalledWith('http://localhost:8080/api/manutencoes/alertas/verificar', {
        method: 'POST'
      });
      expect(resultado).toEqual(resumo);
    });

    it('propaga o erro quando a API responde com falha', async () => {
      fetchMock.mockReturnValue(Promise.resolve({ ok: false } as Response));

      await expect(service.verificarAlertas()).rejects.toThrow('Erro ao verificar alertas de manutenção');
    });
  });
});
