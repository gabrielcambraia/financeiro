import cliente from './cliente'
import type { ConversaoParaCartaoPayload, ItemFatura, RespostaImpacto, Transacao, TipoTransacao } from '../types'

interface FiltrosTransacao {
  month: string
  contaId?: number
  tipo?: TipoTransacao
  categoriaId?: number
  dataVencimentoInicio?: string
  dataVencimentoFim?: string
}

interface PayloadCriarTransacao {
  contaId: number
  contaDestinoId?: number
  categoriaId?: number
  tipo: TipoTransacao
  tipoPagamento: 'DEBITO' | 'CREDITO'
  valor: number
  descricao?: string
  data: string
  dataVencimento?: string
  dataPagamento?: string
  quitarNaCriacao?: boolean
  fixa: boolean
  debitoAutomatico?: boolean
  totalParcelas?: number
  entidadeId?: number | null
}

export const buscarTransacoes = (filtros: FiltrosTransacao) =>
  cliente.get<Transacao[]>('/transacoes', { params: filtros }).then(r => r.data)

export const criarTransacao = (data: PayloadCriarTransacao) =>
  cliente.post<Transacao[]>('/transacoes', data).then(r => r.data)

export type EscopoAtualizacao = 'UNICA' | 'FUTURAS'
export type EscopoExclusao = 'UNICA' | 'GRUPO' | 'FUTURAS'

export const atualizarTransacao = (id: number, data: PayloadCriarTransacao, scope: EscopoAtualizacao = 'UNICA') =>
  cliente.put<Transacao>(`/transacoes/${id}`, data, { params: { scope } }).then(r => r.data)

export const excluirTransacao = (id: number, scope: EscopoExclusao = 'UNICA') =>
  cliente.delete(`/transacoes/${id}`, { params: { scope } })

interface OpcoesPagar {
  dataPagamento?: string
  multa?: number
}

export const pagarTransacao = (id: number, opcoes: OpcoesPagar = {}) =>
  cliente.patch<Transacao>(`/transacoes/${id}/pagar`, opcoes).then(r => r.data)

export const estornarTransacao = (id: number) =>
  cliente.patch<Transacao>(`/transacoes/${id}/estornar`).then(r => r.data)

export const cancelarTransacao = (id: number, scope: EscopoExclusao = 'UNICA') =>
  cliente.patch<Transacao>(`/transacoes/${id}/cancelar`, undefined, { params: { scope } }).then(r => r.data)

export const impactoCancelamentoTransacao = (id: number) =>
  cliente.get<RespostaImpacto>(`/transacoes/${id}/impacto-cancelamento`).then(r => r.data)

export const impactoExclusaoTransacao = (id: number) =>
  cliente.get<RespostaImpacto>(`/transacoes/${id}/impacto-exclusao`).then(r => r.data)

export const converterTransacaoParaCartao = (id: number, payload: ConversaoParaCartaoPayload) =>
  cliente.post<ItemFatura[]>(`/transacoes/${id}/converter-para-cartao`, payload).then(r => r.data)
