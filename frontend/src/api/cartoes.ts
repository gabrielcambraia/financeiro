import cliente from './cliente'
import type { Cartao } from '../types'

type PayloadCartao = {
  nome: string
  limite: number
  diaFechamento: number
  diaVencimento: number
  contaPagamentoId: number
  cor: string
  icone: string
  bancoId?: number
  entidadeId?: number | null
}

export const buscarCartoes = () => cliente.get<Cartao[]>('/cartoes').then(r => r.data)

export const criarCartao = (data: PayloadCartao) =>
  cliente.post<Cartao>('/cartoes', data).then(r => r.data)

export const atualizarCartao = (id: number, data: PayloadCartao) =>
  cliente.put<Cartao>(`/cartoes/${id}`, data).then(r => r.data)

export const excluirCartao = (id: number) =>
  cliente.delete(`/cartoes/${id}`)
