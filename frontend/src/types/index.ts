export type TipoConta = 'CORRENTE' | 'POUPANCA' | 'CARTEIRA' | 'INVESTIMENTO'
export type TipoTransacao = 'RECEITA' | 'DESPESA' | 'TRANSFERENCIA'
export type TipoPagamento = 'DEBITO' | 'CREDITO'
export type StatusTransacao = 'PENDENTE' | 'PAGA' | 'ATRASADA' | 'CANCELADA'
export type DirecaoTransferencia = 'SAIDA' | 'ENTRADA'
export type NivelAcesso = 'USUARIO' | 'ADMIN'
export type PapelUsuario = 'DONO' | 'MEMBRO'
export type TipoEspaco = 'PESSOAL' | 'FAMILIA' | 'EMPRESA'
export type PlanoEspaco = 'GRATUITO' | 'PAGO'
// Catálogo de módulos opcionais por espaço — hoje sem nenhuma validação de
// acesso, só a base para a tela de admin marcar quais espaços têm cada um.
export type ModuloEspaco = 'WHATSAPP_IA'

// Planos e entidades
export type CodigoPlano = 'INDIVIDUAL' | 'FAMILIA' | 'EMPRESA'
export type StatusAssinatura = 'ATIVA' | 'SUSPENSA' | 'CANCELADA'
export type TipoPessoa = 'FISICA' | 'JURIDICA'

export interface Entidade {
  id: number
  tipoPessoa: TipoPessoa
  nome: string
  nomeFantasia?: string
  documento: string
  inscricaoEstadual?: string
  dataNascimento?: string
  email?: string
  telefone?: string
  cep?: string
  logradouro?: string
  numero?: string
  complemento?: string
  bairro?: string
  cidade?: string
  uf?: string
  criadoEm: string
  atualizadoEm: string
}

export interface Assinatura {
  id: number
  codigoPlano: CodigoPlano
  nomePlano: string
  limiteEntidades: number
  entidadesUsadas: number
  status: StatusAssinatura
  vigenciaInicio: string
  vigenciaFim?: string
}

export interface PlanoAdmin {
  id: number
  codigo: CodigoPlano
  nome: string
  limiteEntidades: number
  ativo: boolean
  criadoEm: string
}

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
export type TipoRemuneracao = 'NENHUMA' | 'PRE_FIXADA' | 'POS_CDI' | 'POS_SELIC' | 'IPCA_MAIS'

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
  /** Somente leitura: derivado de saldoInicial + movimentações, nunca enviado em criação/edição. */
  saldo: number
  saldoInicial: number
  cor: string
  icone: string
  bancoId?: number
  banco?: Banco
  entidadeId?: number | null
}

export interface Categoria {
  id: number
  nome: string
  tipo: TipoTransacao
  cor: string
  icone: string
  entidadeId?: number | null
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
  multa?: number
  entidadeId?: number | null
  origemDerivada?: 'META' | 'ATIVO' | 'DIVIDA'
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
  vencimentos: Vencimentos
}

export interface ItemVencimento {
  id: number
  descricao: string
  valor: number
  dataVencimento: string
  diasEmRelacaoAHoje: number
  contaNome: string
}

export interface GrupoVencimento {
  totalVencido: number
  quantidadeVencida: number
  vencidas: ItemVencimento[]
  totalAVencer: number
  quantidadeAVencer: number
  aVencer: ItemVencimento[]
}

export interface Vencimentos {
  aPagar: GrupoVencimento
  aReceber: GrupoVencimento
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
  entidadeId?: number | null
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
  cartaoNome: string
  cartaoCor: string
  contaPagamentoId: number
  contaPagamentoNome: string
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
  entidadeId?: number | null
}

export interface Orcamento {
  id: number
  categoriaId: number
  categoria: Categoria
  mes: string
  limite: number
  gasto: number
  percentualUsado: number
  estourado: boolean
  entidadeId?: number | null
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
  // rendimento automático
  remuneracaoTipo?: TipoRemuneracao
  taxa?: number
  inicioRendimento?: string
  isentoIr?: boolean
  rendidoAte?: string
  entidadeId?: number | null
  // campos derivados (rentabilidade / IR estimado) — só em GET /ativos
  totalAportado?: number
  totalResgatado?: number
  totalRendimento?: number
  rentabilidadePercentual?: number
  irEstimado?: number
  valorLiquidoEstimado?: number
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
  entidadeId?: number | null
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
  entidadeId?: number | null
}

export interface EntidadeResumo {
  id: number
  nome: string
  tipoPessoa: TipoPessoa
}

export interface ItemImpacto {
  tipo: string
  descricao: string
  valor: number
}

export interface OrigemVinculada {
  tipo: 'ATIVO' | 'META' | 'DIVIDA' | 'FATURA'
  id: number
  nome: string
  efeito: string
}

export interface RespostaImpacto {
  bloqueado: boolean
  motivoBloqueio?: string
  itensAfetados: ItemImpacto[]
  origem?: OrigemVinculada
}
