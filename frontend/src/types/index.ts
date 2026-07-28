export type TipoConta = 'CORRENTE' | 'POUPANCA' | 'CARTEIRA' | 'INVESTIMENTO'
export type TipoTransacao = 'RECEITA' | 'DESPESA' | 'TRANSFERENCIA'
export type TipoPagamento = 'DEBITO' | 'CREDITO'
export type StatusTransacao = 'PENDENTE' | 'PAGA' | 'ATRASADA' | 'CANCELADA'
export type DirecaoTransferencia = 'SAIDA' | 'ENTRADA'
export type NivelAcesso = 'USUARIO' | 'ADMIN'
export type TipoEspaco = 'PESSOAL' | 'FAMILIA' | 'EMPRESA'
export type PlanoEspaco = 'GRATUITO' | 'PAGO'
// Catálogo de módulos opcionais por espaço — hoje sem nenhuma validação de
// acesso, só a base para a tela de admin marcar quais espaços têm cada um.
export type ModuloEspaco = 'WHATSAPP_IA'

export interface RespostaPaginada<T> {
  itens: T[]
  pagina: number
  tamanho: number
  totalItens: number
  totalPaginas: number
}
export type StatusDivida = 'EM_DIA' | 'ATRASADA' | 'QUITADA' | 'CANCELADA'
export type TipoAtivo = 'RESERVA' | 'RENDA_FIXA' | 'RENDA_VARIAVEL'
export type TipoMovimentacaoAtivo = 'APORTE' | 'RESGATE' | 'RENDIMENTO'

export interface Banco {
  id: number
  nome: string
  corPrimaria: string
  sigla: string
  temLogo: boolean
}

export interface Conta {
  id: number
  nome: string
  tipo: TipoConta
  saldo: number
  cor: string
  icone: string
  bancoId?: number
  banco?: Banco
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
  dataVencimento?: string
  dataPagamento?: string
  dataCancelamento?: string
  status: StatusTransacao
  fixa: boolean
  totalParcelas?: number
  numeroParcela?: number
  grupoParcelaId?: string
  contaVinculada?: Conta
  transferenciaId?: string
  direcaoTransferencia?: DirecaoTransferencia
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

export interface Cartao {
  id: number
  nome: string
  limite: number
  diaFechamento: number
  diaVencimento: number
  contaPagamentoId: number
  contaPagamento: Conta
  cor: string
  icone: string
  bancoId?: number
  banco?: Banco
  faturaAtualTotal: number
  limiteDisponivel: number
}

export interface ItemFatura {
  id: number
  cartaoId: number
  categoriaId?: number
  categoria?: Categoria
  valor: number
  descricao?: string
  data: string
  totalParcelas?: number
  numeroParcela?: number
  grupoParcelaId?: string
  faturaId?: number
  dataCancelamento?: string
  cancelado: boolean
  faturado: boolean
}

export interface Fatura {
  id: number
  cartaoId: number
  cartao: Cartao
  dataFechamento: string
  dataVencimento: string
  transacaoDespesaId: number
  valor: number
  status: StatusTransacao
  itens?: ItemFatura[]
}

export interface Orcamento {
  id: number
  categoriaId: number
  categoria: Categoria
  mes: string
  limite: number
  gasto: number
  percentualUsado: number
}

export interface Ativo {
  id: number
  nome: string
  tipo: TipoAtivo
  contaId: number
  conta: Conta
  cor: string
  icone: string
  valorAtual: number
  percentualCarteira: number
  dataCancelamento?: string
}

export interface MovimentacaoAtivo {
  id: number
  tipo: TipoMovimentacaoAtivo
  valor: number
  data: string
  contaId?: number
}

export interface Patrimonio {
  totalPatrimonio: number
  porTipo: { tipo: TipoAtivo; valor: number }[]
  evolucaoMensal: { mes: string; valor: number }[]
}

export interface Divida {
  id: number
  descricao: string
  credor?: string
  valorTotal: number
  totalParcelas: number
  contaId: number
  conta: Conta
  categoriaId?: number
  categoria?: Categoria
  dataInicio: string
  dataCancelamento?: string
  valorPago: number
  valorRestante: number
  parcelasPagas: number
  status: StatusDivida
  proximaParcelaData?: string
  proximaParcelaValor?: number
}

export interface Meta {
  id: number
  nome: string
  valorAlvo: number
  valorAtual: number
  prazo?: string
  cor: string
  icone: string
  dataCancelamento?: string
  percentualConcluido: number
  mesesEstimados?: number
  concluida: boolean
}
