import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FrotasService } from '../../services/frotas.service';
import { AlertaManutencao, ResumoVerificacaoAlertas, StatusAlerta } from '../../models';

// TODO: DataTable e StatusBadge de libs/ui-components são componentes React e não servem
// para o Angular - tabela e badge feitos como nas demais telas do painel
@Component({
  selector: 'app-alertas-manutencao',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="alertas-page">
      <div class="page-header">
        <h1>Alertas de Manutenção Preventiva</h1>
        <button class="btn-primary" data-testid="btn-verificar" [disabled]="verificando" (click)="verificarAgora()">
          {{ verificando ? 'Verificando...' : 'Verificar agora' }}
        </button>
      </div>

      <div *ngIf="resumo" class="resumo" data-testid="resumo">
        Verificados: {{ resumo.verificados }} · Elegíveis: {{ resumo.elegiveis }} ·
        Novos alertas: {{ resumo.novosAlertas }} (enviados: {{ resumo.enviados }},
        falhas: {{ resumo.falhas }}, pendentes: {{ resumo.pendentes }})
      </div>

      <div class="filtros">
        <label for="filtro-status">Status</label>
        <select id="filtro-status" data-testid="filtro-status" [value]="filtroStatus" (change)="filtrar($any($event.target).value)">
          <option *ngFor="let opcao of opcoesFiltro" [value]="opcao.valor">{{ opcao.rotulo }}</option>
        </select>
      </div>

      <div *ngIf="loading" class="loading" data-testid="loading">Carregando alertas...</div>

      <div *ngIf="!loading && erro" class="error-state" data-testid="erro">{{ erro }}</div>

      <table class="data-table" data-testid="tabela-alertas" *ngIf="!loading && !erro && alertas.length > 0">
        <thead>
          <tr>
            <th>Placa</th>
            <th>Motivo</th>
            <th>Km desde a preventiva</th>
            <th>Data de referência</th>
            <th>Status</th>
            <th>Data do alerta</th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let alerta of alertas">
            <td>{{ alerta.placa || '-' }}</td>
            <td>{{ rotuloMotivo(alerta.motivo) }}</td>
            <td>{{ alerta.kmDesdeUltimaPreventiva | number:'1.0-0' }}</td>
            <td>{{ alerta.dataReferencia | date:'dd/MM/yyyy' }}</td>
            <td>
              <span class="status-badge" [ngClass]="'status-' + alerta.status" [attr.title]="alerta.mensagemErro">
                {{ alerta.status }}
              </span>
            </td>
            <td>{{ alerta.dataCriacao | date:'dd/MM/yyyy HH:mm' }}</td>
          </tr>
        </tbody>
      </table>

      <div *ngIf="!loading && !erro && alertas.length === 0" class="empty-state" data-testid="vazio">
        Nenhum alerta encontrado.
      </div>
    </div>
  `,
  styles: [`
    .alertas-page { padding: 20px; }
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
    .page-header h1 { margin: 0; color: #333; font-size: 24px; }
    .btn-primary { background: #1565c0; color: white; border: none; padding: 10px 20px; border-radius: 6px; cursor: pointer; }
    .btn-primary:disabled { opacity: 0.6; cursor: default; }
    .resumo { margin-bottom: 16px; padding: 12px 16px; background: #e3f2fd; color: #1565c0; border-radius: 8px; font-size: 14px; }
    .filtros { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; color: #666; }
    .filtros select { padding: 8px 12px; border: 1px solid #ddd; border-radius: 6px; background: white; }
    .loading { text-align: center; padding: 40px; color: #666; }
    .error-state { text-align: center; padding: 40px; color: #c62828; background: #ffebee; border-radius: 12px; }
    .data-table { width: 100%; border-collapse: collapse; background: white; border-radius: 12px; overflow: hidden; box-shadow: 0 2px 8px rgba(0,0,0,0.1); }
    .data-table th { text-align: left; padding: 14px; background: #f5f5f5; color: #666; font-size: 13px; text-transform: uppercase; }
    .data-table td { padding: 14px; border-bottom: 1px solid #f5f5f5; font-size: 14px; }
    .data-table tr:hover { background: #f9f9f9; }
    .status-badge { padding: 4px 10px; border-radius: 12px; font-size: 12px; font-weight: 500; }
    .status-ENVIADA { background: #e8f5e9; color: #2e7d32; }
    .status-FALHA { background: #ffebee; color: #c62828; }
    .status-PENDENTE { background: #fff3e0; color: #e65100; }
    .empty-state { text-align: center; padding: 40px; color: #999; background: white; border-radius: 12px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); }
  `]
})
export class AlertasManutencaoComponent implements OnInit {
  readonly opcoesFiltro: { valor: '' | StatusAlerta; rotulo: string }[] = [
    { valor: '', rotulo: 'Todos' },
    { valor: 'ENVIADA', rotulo: 'Enviada' },
    { valor: 'FALHA', rotulo: 'Falha' },
    { valor: 'PENDENTE', rotulo: 'Pendente' }
  ];

  alertas: AlertaManutencao[] = [];
  filtroStatus: '' | StatusAlerta = '';
  loading = true;
  verificando = false;
  erro: string | null = null;
  resumo: ResumoVerificacaoAlertas | null = null;

  constructor(private frotasService: FrotasService) {}

  ngOnInit(): void {
    this.carregarAlertas();
  }

  async carregarAlertas(): Promise<void> {
    this.loading = true;
    this.erro = null;
    try {
      this.alertas = await this.frotasService.listarAlertas(this.filtroStatus || undefined);
    } catch (error) {
      console.error('Erro:', error);
      this.alertas = [];
      this.erro = 'Não foi possível carregar os alertas. Tente novamente.';
    }
    this.loading = false;
  }

  filtrar(status: '' | StatusAlerta): void {
    this.filtroStatus = status;
    this.carregarAlertas();
  }

  // Ponto único de disparo da verificação. O destino final ainda não foi decidido
  // (ao abrir uma tela ou ao logar): para mover, basta chamar frotasService.verificarAlertas()
  // do ngOnInit da tela escolhida ou do fluxo de login.
  async verificarAgora(): Promise<void> {
    this.verificando = true;
    this.erro = null;
    try {
      this.resumo = await this.frotasService.verificarAlertas();
    } catch (error) {
      console.error('Erro:', error);
      this.verificando = false;
      this.erro = 'Não foi possível verificar os alertas. Tente novamente.';
      return;
    }
    this.verificando = false;
    await this.carregarAlertas();
  }

  rotuloMotivo(motivo: string): string {
    const rotulos: Record<string, string> = {
      KM: 'Quilometragem',
      TEMPO: 'Tempo',
      KM_E_TEMPO: 'Quilometragem e tempo'
    };
    return rotulos[motivo] ?? motivo;
  }
}
