import cliente from './cliente'
import type { Orcamento } from '../types'

type PayloadOrcamento = {
  categoriaId: number
  mes: string
  limite: number
  entidadeId?: number | null
}

export const buscarOrcamentos = (mes: string) =>
  cliente.get<Orcamento[]>('/orcamentos', { params: { mes } }).then(r => r.data)

export const criarOrcamento = (data: PayloadOrcamento) =>
  cliente.post<Orcamento>('/orcamentos', data).then(r => r.data)

export const atualizarOrcamento = (id: number, data: PayloadOrcamento) =>
  cliente.put<Orcamento>(`/orcamentos/${id}`, data).then(r => r.data)

export const excluirOrcamento = (id: number) =>
  cliente.delete(`/orcamentos/${id}`)
