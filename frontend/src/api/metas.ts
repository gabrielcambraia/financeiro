import cliente from './cliente'
import type { Meta, RespostaImpacto } from '../types'

type PayloadMeta = {
  nome: string
  valorAlvo: number
  prazo?: string
  cor: string
  icone: string
  filialId?: number | null
}

type PayloadMovimento = {
  valor: number
  contaId: number
  data?: string
}

export const buscarMetas = () => cliente.get<Meta[]>('/metas').then(r => r.data)

export const criarMeta = (data: PayloadMeta) =>
  cliente.post<Meta>('/metas', data).then(r => r.data)

export const atualizarMeta = (id: number, data: PayloadMeta) =>
  cliente.put<Meta>(`/metas/${id}`, data).then(r => r.data)

export const excluirMeta = (id: number) =>
  cliente.delete(`/metas/${id}`)

export const cancelarMeta = (id: number) =>
  cliente.patch<Meta>(`/metas/${id}/cancelar`).then(r => r.data)

export const aportarMeta = (id: number, data: PayloadMovimento) =>
  cliente.patch<Meta>(`/metas/${id}/aportar`, data).then(r => r.data)

export const resgatarMeta = (id: number, data: PayloadMovimento) =>
  cliente.patch<Meta>(`/metas/${id}/resgatar`, data).then(r => r.data)

export const impactoCancelamentoMeta = (id: number) =>
  cliente.get<RespostaImpacto>(`/metas/${id}/impacto-cancelamento`).then(r => r.data)

export const impactoExclusaoMeta = (id: number) =>
  cliente.get<RespostaImpacto>(`/metas/${id}/impacto-exclusao`).then(r => r.data)
