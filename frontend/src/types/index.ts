export type TipoConta = 'CORRENTE' | 'POUPANCA' | 'CARTEIRA' | 'INVESTIMENTO'
export type TipoTransacao = 'RECEITA' | 'DESPESA'
export type TipoPagamento = 'DEBITO' | 'CREDITO'

export interface Conta {
  id: number
  nome: string
  tipo: TipoConta
  saldo: number
  cor: string
  icone: string
}

export interface Categoria {
  id: number
  nome: string
  tipo: TipoTransacao
  cor: string
  icone: string
}

export interface Transacao {
  id: number
  conta: Conta
  categoria?: Categoria
  contaId: number
  categoriaId?: number
  tipo: TipoTransacao
  tipoPagamento: TipoPagamento
  valor: number
  descricao?: string
  data: string
  fixa: boolean
  totalParcelas?: number
  numeroParcela?: number
  grupoParcelaId?: string
}

export interface ResumoFluxo {
  receita: number
  despesa: number
  saldo: number
}

export interface DadosPainel {
  totalReceitas: number
  totalDespesas: number
  saldoLiquido: number
  realizado: ResumoFluxo
  pendente: ResumoFluxo
  despesasPorCategoria: ResumoCategoria[]
  receitasPorCategoria: ResumoCategoria[]
  tendenciaMensal: TendenciaMensal[]
  saldosContas: SaldoConta[]
  saldoDiario: SaldoDiario[]
}

export interface ResumoCategoria {
  categoria: Categoria
  total: number
  percentual: number
}

export interface TendenciaMensal {
  mes: string
  receita: number
  despesa: number
}

export interface SaldoConta {
  conta: Conta
  saldo: number
}

export interface SaldoDiario {
  data: string
  saldo: number
}
