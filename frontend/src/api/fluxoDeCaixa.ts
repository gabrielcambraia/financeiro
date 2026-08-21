import cliente from './cliente'

export interface PontoFluxo {
  data: string
  saldo: number
  saldoSimulado?: number
}

export interface FluxoDeCaixa {
  saldoAtual: number
  pontos: PontoFluxo[]
}

interface FiltrosFluxo {
  dias?: number
  contaId?: number
  simulacaoValor?: number
  simulacaoData?: string
}

export const buscarFluxoDeCaixa = (filtros: FiltrosFluxo) =>
  cliente.get<FluxoDeCaixa>('/fluxo-de-caixa', { params: filtros }).then(r => r.data)
