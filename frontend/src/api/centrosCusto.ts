import cliente from './cliente'
import type { CentroCusto } from '../types'

export const buscarCentrosCusto = () =>
  cliente.get<CentroCusto[]>('/centros-custo').then(r => r.data)

export const criarCentroCusto = (data: Omit<CentroCusto, 'id'>) =>
  cliente.post<CentroCusto>('/centros-custo', data).then(r => r.data)

export const atualizarCentroCusto = (id: number, data: Omit<CentroCusto, 'id'>) =>
  cliente.put<CentroCusto>(`/centros-custo/${id}`, data).then(r => r.data)

export const excluirCentroCusto = (id: number) =>
  cliente.delete(`/centros-custo/${id}`)
