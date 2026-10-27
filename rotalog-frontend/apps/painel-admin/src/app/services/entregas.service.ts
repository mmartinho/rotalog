import { Injectable } from '@angular/core';
import { Entrega } from '../models';

// TODO: URL hardcoded - deveria usar environment
// TODO: Porta diferente do api-frotas - inconsistência
const API_URL = 'http://localhost:3000';

@Injectable({
  providedIn: 'root'
})
export class EntregasService {

  // TODO: Sem cache como no FrotasService - inconsistência entre services
  constructor() {}

  async getEntregas(): Promise<Entrega[]> {
    try {
      const response = await fetch(`${API_URL}/api/entregas`);
      if (!response.ok) throw new Error('Erro ao buscar entregas');
      return await response.json();
    } catch (error) {
      console.error('Erro:', error);
      return [];
    }
  }

  async getEntrega(id: number): Promise<Entrega | null> {
    try {
      const response = await fetch(`${API_URL}/api/entregas/${id}`);
      if (!response.ok) return null;
      return await response.json();
    } catch (error) {
      console.error('Erro:', error);
      return null;
    }
  }

  // TODO: Método nunca usado - dead code
  async getEntregasPorMotorista(motoristaId: number): Promise<Entrega[]> {
    try {
      const response = await fetch(`${API_URL}/api/entregas?motorista=${motoristaId}`);
      if (!response.ok) throw new Error('Erro');
      return await response.json();
    } catch (error) {
      console.error('Erro:', error);
      return [];
    }
  }
}
