import cliente from './cliente'

export interface PontoProjecao {
  data: string
  saldo: number
  saldoSimulado?: number
}

export interface Projecao {
  saldoAtual: number
  pontos: PontoProjecao[]
}

interface FiltrosProjecao {
  dias?: number
  contaId?: number
  simulacaoValor?: number
  simulacaoData?: string
}

export const buscarProjecao = (filtros: FiltrosProjecao) =>
  cliente.get<Projecao>('/projecao', { params: filtros }).then(r => r.data)
