import cliente from './cliente'
import type { Meta } from '../types'

type PayloadMeta = {
  nome: string
  valorAlvo: number
  prazo?: string
  cor: string
  icone: string
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
