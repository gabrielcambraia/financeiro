import cliente from './cliente'
import type { Divida, RespostaImpacto, Transacao } from '../types'

type PayloadDivida = {
  descricao: string
  credor?: string
  valorTotal: number
  totalParcelas: number
  contaId: number
  categoriaId?: number
  dataInicio: string
}

export const buscarDividas = () => cliente.get<Divida[]>('/dividas').then(r => r.data)

export const buscarParcelasDivida = (id: number) =>
  cliente.get<Transacao[]>(`/dividas/${id}/parcelas`).then(r => r.data)

export const criarDivida = (data: PayloadDivida) =>
  cliente.post<Divida>('/dividas', data).then(r => r.data)

export const cancelarDivida = (id: number) =>
  cliente.patch<Divida>(`/dividas/${id}/cancelar`).then(r => r.data)

export const excluirDivida = (id: number) =>
  cliente.delete(`/dividas/${id}`)

export const impactoCancelamentoDivida = (id: number) =>
  cliente.get<RespostaImpacto>(`/dividas/${id}/impacto-cancelamento`).then(r => r.data)

export const impactoExclusaoDivida = (id: number) =>
  cliente.get<RespostaImpacto>(`/dividas/${id}/impacto-exclusao`).then(r => r.data)
