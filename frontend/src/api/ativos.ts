import cliente from './cliente'
import type { Ativo, MovimentacaoAtivo, Patrimonio, TipoAtivo, TipoRemuneracao } from '../types'

type PayloadAtivo = {
  nome: string
  tipo: TipoAtivo
  contaId: number
  cor: string
  icone: string
  remuneracaoTipo?: TipoRemuneracao
  taxa?: number | null
  inicioRendimento?: string | null
  isentoIr?: boolean
  valorInicial?: number | null
  dataInicial?: string | null
}

type PayloadMovimento = {
  valor: number
  contaId?: number
  data?: string
}

export const buscarAtivos = () => cliente.get<Ativo[]>('/ativos').then(r => r.data)

export const buscarPatrimonio = (meses = 6) =>
  cliente.get<Patrimonio>('/ativos/patrimonio', { params: { meses } }).then(r => r.data)

export const buscarMovimentacoesAtivo = (id: number) =>
  cliente.get<MovimentacaoAtivo[]>(`/ativos/${id}/movimentacoes`).then(r => r.data)

export const criarAtivo = (data: PayloadAtivo) =>
  cliente.post<Ativo>('/ativos', data).then(r => r.data)

export const atualizarAtivo = (id: number, data: PayloadAtivo) =>
  cliente.put<Ativo>(`/ativos/${id}`, data).then(r => r.data)

export const excluirAtivo = (id: number) =>
  cliente.delete(`/ativos/${id}`)

export const cancelarAtivo = (id: number) =>
  cliente.patch<Ativo>(`/ativos/${id}/cancelar`).then(r => r.data)

export const aportarAtivo = (id: number, data: PayloadMovimento) =>
  cliente.patch<Ativo>(`/ativos/${id}/aportar`, data).then(r => r.data)

export const resgatarAtivo = (id: number, data: PayloadMovimento) =>
  cliente.patch<Ativo>(`/ativos/${id}/resgatar`, data).then(r => r.data)

export const registrarRendimentoAtivo = (id: number, data: PayloadMovimento) =>
  cliente.patch<Ativo>(`/ativos/${id}/rendimento`, data).then(r => r.data)
