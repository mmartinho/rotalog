import { Route } from '@angular/router';
import { DashboardComponent } from './components/dashboard/dashboard.component';
import { VeiculosComponent } from './components/veiculos/veiculos.component';
import { MotoristasComponent } from './components/motoristas/motoristas.component';
import { ManutencoesComponent } from './components/manutencoes/manutencoes.component';
import { EntregasComponent } from './components/entregas/entregas.component';
import { AlertasManutencaoComponent } from './components/alertas-manutencao/alertas-manutencao.component';

// TODO: Sem lazy loading - todos os módulos carregados no bundle principal
// TODO: Sem guards de autenticação
// TODO: Sem resolvers para pré-carregar dados
export const appRoutes: Route[] = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  { path: 'dashboard', component: DashboardComponent },
  { path: 'veiculos', component: VeiculosComponent },
  { path: 'motoristas', component: MotoristasComponent },
  { path: 'manutencoes', component: ManutencoesComponent },
  { path: 'alertas-manutencao', component: AlertasManutencaoComponent },
  { path: 'entregas', component: EntregasComponent },
  // TODO: Rota wildcard sem página 404 customizada
  { path: '**', redirectTo: 'dashboard' }
];
