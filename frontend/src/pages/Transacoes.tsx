import { useState, useEffect } from 'react'
import { useLocation } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Trash2, Pencil, CreditCard, Repeat, Layers, CheckCircle2, Ban, Undo2, ArrowRightLeft, TrendingUp, TrendingDown, CalendarDays, Upload, Download, Plus, ChevronLeft, ChevronRight } from 'lucide-react'
import { format, parseISO } from 'date-fns'
import {
  buscarTransacoes, excluirTransacao, pagarTransacao, estornarTransacao, cancelarTransacao,
  impactoCancelamentoTransacao, impactoExclusaoTransacao,
  type EscopoExclusao,
} from '../api/transacoes'
import { buscarItensFatura, excluirItemFatura, cancelarItemFatura } from '../api/itensFatura'
import { buscarFaturas } from '../api/faturas'
import { buscarCategorias } from '../api/categorias'
import { buscarCentrosCusto } from '../api/centrosCusto'
import { buscarContas } from '../api/contas'
import { buscarCartoes } from '../api/cartoes'
import { useLojaFiltro } from '../store/lojaFiltro'
import SeletorMes from '../components/SeletorMes'
import FormularioTransacao, { type EdicaoLancamento } from '../components/forms/FormularioTransacao'
import SobreposicaoModal from '../components/SobreposicaoModal'
import Spinner from '../components/Spinner'
import type { Transacao, ItemFatura, Fatura, TipoTransacao, StatusTransacao, RespostaImpacto } from '../types'

const rotuloStatus: Record<StatusTransacao, string> = {
  PAGA: 'Paga',
  PENDENTE: 'Pendente',
  ATRASADA: 'Atrasada',
  CANCELADA: 'Cancelada',
}

const corStatus: Record<StatusTransacao, string> = {
  PAGA: 'bg-emerald-500/15 text-emerald-500',
  PENDENTE: 'bg-amber-500/15 text-amber-500',
  ATRASADA: 'bg-red-500/15 text-red-500',
  CANCELADA: 'bg-gray-500/15 text-gray-400 line-through',
}

function BadgeStatus({ status }: { status: StatusTransacao }) {
  return (
    <span className={`text-xs px-1.5 py-0.5 rounded-full font-medium ${corStatus[status]}`}>
      {rotuloStatus[status]}
    </span>
  )
}

const fmt = (v: number) =>
  new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(v)

// Lançamentos mescla três modelos: Transacao (débito, amarrada a uma conta,
// com vencimento/pagamento), ItemFatura (compra em aberto no cartão — ainda
// não faturada) e Fatura (fatura fechada de um cartão específico, com sua
// própria despesa consolidada). Ver FormularioTransacao.tsx para o mesmo
// union do lado do formulário (que só lida com TRANSACAO/ITEM_FATURA).
type Lancamento =
  | { origem: 'TRANSACAO'; id: string; data: string; tx: Transacao }
  | { origem: 'ITEM_FATURA'; id: string; data: string; item: ItemFatura }
  | { origem: 'FATURA'; id: string; data: string; fatura: Fatura }


interface EstadoNavegacaoTransacoes {
  tipo?: TipoTransacao
  dataVencimentoInicio?: string
  dataVencimentoFim?: string
}

const ITENS_POR_PAGINA = 15

export default function Transacoes() {
  const qc = useQueryClient()
  const { mes, contaId, definirContaId } = useLojaFiltro()
  // Vindo do bloco de vencimentos do painel via location.state (não query
  // string): filtra por período de vencimento em vez de mês de competência
  // e já exclui canceladas/pagas no backend. Usar state em vez de URL mantém
  // o link fora da barra de endereço, então o usuário não edita à mão.
  const location = useLocation()
  const estadoNavegacao = (location.state ?? {}) as EstadoNavegacaoTransacoes
  const [filtroTipo, setFiltroTipo] = useState<TipoTransacao | ''>(estadoNavegacao.tipo ?? '')
  const dataVencimentoInicio = estadoNavegacao.dataVencimentoInicio
  const dataVencimentoFim = estadoNavegacao.dataVencimentoFim

  // Filtros de data: estado local para edição, aplicados ao pressionar "Aplicar"
  const [localDataInicio, setLocalDataInicio] = useState(dataVencimentoInicio ?? '')
  const [localDataFim, setLocalDataFim] = useState(dataVencimentoFim ?? '')
  const [filtroDataInicio, setFiltroDataInicio] = useState(dataVencimentoInicio)
  const [filtroDataFim, setFiltroDataFim] = useState(dataVencimentoFim)
  const [vencimentoAberto, setVencimentoAberto] = useState(!!dataVencimentoInicio)

  const [filtroCategoria, setFiltroCategoria] = useState<number | ''>('')
  const [filtroCentroCusto, setFiltroCentroCusto] = useState<number | ''>('')
  const [filtroStatus, setFiltroStatus] = useState<StatusTransacao | ''>('')
  const [filtroCartao, setFiltroCartao] = useState<number | ''>('')
  const [modoCartao, setModoCartao] = useState(false)
  const [exibirCartao, setExibirCartao] = useState(false)
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<EdicaoLancamento | undefined>()
  const [deleteModal, setDeleteModal] = useState<{ tx: Transacao; impacto: RespostaImpacto | null; carregando: boolean; erro?: string } | null>(null)
  const [cancelModal, setCancelModal] = useState<{ tx: Transacao; impacto: RespostaImpacto | null; carregando: boolean; erro?: string } | null>(null)
  const [pagarModal, setPagarModal] = useState<{ id: number; tipo: TipoTransacao; status: StatusTransacao } | null>(null)
  const [confirmarExcluirItem, setConfirmarExcluirItem] = useState<number | null>(null)

  // Paginação
  const [pagina, setPagina] = useState(1)
  useEffect(() => { setPagina(1) }, [mes, contaId, filtroTipo, filtroCategoria, filtroCentroCusto, filtroCartao, modoCartao, exibirCartao, filtroDataInicio, filtroDataFim, filtroStatus])

  const abrirDeleteModal = async (tx: Transacao) => {
    setDeleteModal({ tx, impacto: null, carregando: true })
    try {
      const impacto = await impactoExclusaoTransacao(tx.id)
      setDeleteModal({ tx, impacto, carregando: false })
    } catch {
      setDeleteModal({ tx, impacto: null, carregando: false })
    }
  }

  const abrirCancelModal = async (tx: Transacao) => {
    setCancelModal({ tx, impacto: null, carregando: true })
    try {
      const impacto = await impactoCancelamentoTransacao(tx.id)
      setCancelModal({ tx, impacto, carregando: false })
    } catch {
      setCancelModal({ tx, impacto: null, carregando: false })
    }
  }

  // Com um cartão selecionado no filtro, a tela mostra só o que é daquele
  // cartão (compras em aberto + faturas fechadas) — nada de lançamentos de
  // débito da conta, mesmo que a conta de pagamento do cartão seja a mesma.
  const { data: transacoes = [], isLoading } = useQuery({
    queryKey: ['transacoes', mes, contaId, filtroTipo, filtroCategoria, filtroCentroCusto, filtroDataInicio, filtroDataFim],
    queryFn: () => buscarTransacoes({
      month: mes,
      contaId,
      tipo: filtroTipo || undefined,
      categoriaId: filtroCategoria || undefined,
      centroCustoId: filtroCentroCusto || undefined,
      dataVencimentoInicio: filtroDataInicio,
      dataVencimentoFim: filtroDataFim,
    }),
    enabled: !filtroCartao && !modoCartao,
  })

  // Itens de fatura em aberto não têm vencimento nem "tipo" formal — sempre
  // são despesa. Não busca quando o filtro de tipo exclui despesas, nem
  // quando a navegação veio filtrada por vencimento (não se aplica a eles).
  const buscaItensHabilitada = (modoCartao || !!filtroCartao || filtroTipo === '') && (exibirCartao || modoCartao || !!filtroCartao)
  const { data: itensFaturaRaw = [] } = useQuery({
    queryKey: ['itensFatura', 'lancamentos', mes, contaId, filtroCartao, filtroCentroCusto],
    queryFn: () => buscarItensFatura({ month: mes, contaId, cartaoId: filtroCartao || undefined, centroCustoId: filtroCentroCusto || undefined }),
    enabled: buscaItensHabilitada,
  })
  // Sem suporte a filtro de categoria no endpoint de itens em aberto — filtra no cliente.
  const itensFatura = itensFaturaRaw
    .filter(i => !filtroCategoria || i.categoriaId === filtroCategoria)

  // Faturas fechadas do cartão selecionado, no mesmo mês de competência dos
  // demais lançamentos da tela — sem isso, apareceriam faturas de qualquer
  // mês junto com compras em aberto do mês atual.
  const { data: faturasCartaoRaw = [] } = useQuery({
    queryKey: ['faturas', 'lancamentos', filtroCartao, mes],
    queryFn: () => buscarFaturas(filtroCartao as number, mes),
    enabled: !!filtroCartao && buscaItensHabilitada,
  })
  // Faturas não têm categoria própria (é dos itens que a compõem) — sem
  // categoria selecionada, mostra todas; com categoria, some da lista (não
  // dá pra saber se ela "tem" aquela categoria sem abrir o detalhe).
  const faturasCartao = filtroCategoria ? [] : faturasCartaoRaw

  const { data: categorias = [] } = useQuery({ queryKey: ['categorias'], queryFn: () => buscarCategorias() })
  const { data: centrosCusto = [] } = useQuery({ queryKey: ['centros-custo'], queryFn: buscarCentrosCusto })
  const { data: contas = [] } = useQuery({ queryKey: ['contas'], queryFn: buscarContas })
  const { data: cartoes = [] } = useQuery({ queryKey: ['cartoes'], queryFn: buscarCartoes })

  const invalidarTudo = async () => {
    await Promise.all([
      qc.invalidateQueries({ queryKey: ['transacoes'] }),
      qc.invalidateQueries({ queryKey: ['itensFatura'] }),
      qc.invalidateQueries({ queryKey: ['faturas'] }),
      qc.invalidateQueries({ queryKey: ['painel'] }),
      qc.invalidateQueries({ queryKey: ['contas'] }),
      qc.invalidateQueries({ queryKey: ['cartoes'] }),
    ])
  }

  const deleteMutation = useMutation({
    mutationFn: ({ id, scope }: { id: number; scope: EscopoExclusao }) => excluirTransacao(id, scope),
    onSuccess: async () => { await invalidarTudo(); setDeleteModal(null); toast.success('Transação excluída') },
    onError: (e: unknown) => {
      const msg = (e as { response?: { data?: { mensagem?: string } } })?.response?.data?.mensagem
      setDeleteModal(prev => prev ? { ...prev, erro: msg ?? 'Erro ao excluir.' } : prev)
    },
  })

  const pagarMutation = useMutation({
    mutationFn: ({ id, dataPagamento, multa }: { id: number; dataPagamento?: string; multa?: number }) =>
      pagarTransacao(id, { dataPagamento, multa }),
    onSuccess: async () => { await invalidarTudo(); setPagarModal(null); toast.success('Pagamento registrado') },
  })

  const estornarMutation = useMutation({
    mutationFn: (id: number) => estornarTransacao(id),
    onSuccess: async () => { await invalidarTudo(); toast.success('Pagamento estornado') },
  })

  const cancelarMutation = useMutation({
    mutationFn: ({ id, scope }: { id: number; scope: EscopoExclusao }) => cancelarTransacao(id, scope),
    onSuccess: async () => { await invalidarTudo(); setCancelModal(null); toast.success('Transação cancelada') },
    onError: (e: unknown) => {
      const msg = (e as { response?: { data?: { mensagem?: string } } })?.response?.data?.mensagem
      setCancelModal(prev => prev ? { ...prev, erro: msg ?? 'Erro ao cancelar.' } : prev)
    },
  })

  const excluirItemMutation = useMutation({
    mutationFn: (id: number) => excluirItemFatura(id),
    onSuccess: async () => { await invalidarTudo(); setConfirmarExcluirItem(null); toast.success('Compra excluída') },
  })

  const cancelarItemMutation = useMutation({
    mutationFn: (id: number) => cancelarItemFatura(id),
    onSuccess: async () => { await invalidarTudo(); toast.success('Compra cancelada') },
  })

  // Com cartão selecionado: só compras em aberto + faturas fechadas daquele
  // cartão. Sem cartão selecionado: lançamentos de débito da conta + compras
  // em aberto de todos os cartões (comportamento padrão da tela).
  const lancamentos: Lancamento[] = (modoCartao || filtroCartao)
    ? [
        ...itensFatura.map(item => ({ origem: 'ITEM_FATURA' as const, id: `item-${item.id}`, data: item.data, item })),
        ...faturasCartao.map(fatura => ({ origem: 'FATURA' as const, id: `fatura-${fatura.id}`, data: fatura.dataFechamento, fatura })),
      ]
    : [
        ...transacoes.map(tx => ({ origem: 'TRANSACAO' as const, id: `tx-${tx.id}`, data: tx.data, tx })),
        ...(filtroTipo === '' && exibirCartao ? itensFatura.map(item => ({ origem: 'ITEM_FATURA' as const, id: `item-${item.id}`, data: item.data, item })) : []),
      ]

  // Itens de fatura não têm status formal — somem quando um status específico é filtrado.
  const visiveis = filtroStatus
    ? lancamentos.filter(l =>
        (l.origem === 'TRANSACAO' && l.tx.status === filtroStatus) ||
        (l.origem === 'FATURA' && l.fatura.status === filtroStatus))
    : lancamentos

  // Paginação sobre a lista filtrada (ordenada por data desc)
  const visiveisOrdenados = [...visiveis].sort((a, b) => b.data.localeCompare(a.data))
  const totalPaginas = Math.max(1, Math.ceil(visiveisOrdenados.length / ITENS_POR_PAGINA))
  const visivelsPaginados = visiveisOrdenados.slice((pagina - 1) * ITENS_POR_PAGINA, pagina * ITENS_POR_PAGINA)

  // Totais derivados de `visiveis` (todos os filtros já aplicados) —
  // cancelados nunca entram, pois a movimentação nunca aconteceu de fato.
  const txVisiveis = visiveis
    .filter(l => l.origem === 'TRANSACAO')
    .map(l => l.tx)
    .filter(t => t.status !== 'CANCELADA')
  const itensVisiveis = visiveis
    .filter(l => l.origem === 'ITEM_FATURA')
    .map(l => l.item)
    .filter(i => !i.cancelado)
  const faturasVisiveis = visiveis
    .filter(l => l.origem === 'FATURA')
    .map(l => l.fatura)

  const totalReceitas = txVisiveis.filter(t => t.tipo === 'RECEITA').reduce((s, t) => s + t.valor, 0)
  const totalDespesas = txVisiveis.filter(t => t.tipo === 'DESPESA').reduce((s, t) => s + t.valor, 0)
    + itensVisiveis.reduce((s, i) => s + i.valor, 0)
    + faturasVisiveis.reduce((s, f) => s + f.valor, 0)
  const resultado = totalReceitas - totalDespesas
  const countReceitas = txVisiveis.filter(t => t.tipo === 'RECEITA').length
  const countDespesas = txVisiveis.filter(t => t.tipo === 'DESPESA').length + itensVisiveis.length

  // Chip de tipo: classe base e classe ativa
  const chipBase = 'text-xs px-3 py-1.5 rounded-full font-medium transition-colors flex items-center gap-1'
  const chipAtivo = 'bg-acento text-white border border-acento'
  const chipInativo = 'border border-borda text-conteudo-suave hover:border-acento hover:text-acento bg-superficie'

  return (
    <div className="p-6 space-y-5">
      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <h1 className="text-2xl font-bold text-conteudo">Lançamentos</h1>
        <div className="flex items-center gap-3 md:w-auto">
          <SeletorMes />
        </div>
      </div>

      {/* Cards de resumo — mobile: 3 linhas horizontais; desktop: 3 colunas */}
      <div className="card p-0 overflow-hidden md:hidden">
        {([
          { label: 'Receitas', valor: `+${fmt(totalReceitas)}`, count: `${countReceitas} lançamentos`, cor: 'text-sucesso' },
          { label: 'Despesas', valor: `-${fmt(totalDespesas)}`, count: `${countDespesas} lançamentos`, cor: 'text-perigo' },
          { label: 'Resultado', valor: fmt(resultado), count: `${countReceitas + countDespesas} transações`, cor: resultado >= 0 ? 'text-sucesso' : 'text-perigo' },
        ] as const).map(({ label, valor, count, cor }, i) => (
          <div key={label} className={`flex items-center gap-3 px-4 py-3 ${i < 2 ? 'border-b border-borda' : ''}`}>
            <div className="flex-1 min-w-0">
              <p className="text-xs font-semibold text-conteudo-suave">{label}</p>
              <p className="text-xs text-conteudo-suave">{count}</p>
            </div>
            <p className={`text-base font-bold whitespace-nowrap ${cor}`}>{valor}</p>
          </div>
        ))}
      </div>
      <div className="hidden md:grid grid-cols-3 gap-3">
        <div className="card p-4">
          <p className="text-xs text-conteudo-suave mb-1">Receitas</p>
          <p className="text-lg font-bold text-sucesso">+{fmt(totalReceitas)}</p>
          <p className="text-xs text-conteudo-suave mt-1">{countReceitas} lançamentos</p>
        </div>
        <div className="card p-4">
          <p className="text-xs text-conteudo-suave mb-1">Despesas</p>
          <p className="text-lg font-bold text-perigo">-{fmt(totalDespesas)}</p>
          <p className="text-xs text-conteudo-suave mt-1">{countDespesas} lançamentos</p>
        </div>
        <div className="card p-4">
          <p className="text-xs text-conteudo-suave mb-1">Resultado</p>
          <p className={`text-lg font-bold ${resultado >= 0 ? 'text-sucesso' : 'text-perigo'}`}>{fmt(resultado)}</p>
          <p className="text-xs text-conteudo-suave mt-1">{countReceitas + countDespesas} transações total</p>
        </div>
      </div>

      {/* Barra de filtros em chips */}
      <div className="card p-3">
        {/* Linha 1: chips de filtro + ações */}
        <div className="flex flex-wrap gap-2 items-center">
          {/* Chips de tipo */}
          <button
            className={`${chipBase} ${!modoCartao && filtroTipo === '' ? chipAtivo : chipInativo}`}
            onClick={() => { setFiltroTipo(''); setFiltroCategoria(''); setModoCartao(false) }}>
            Todos
          </button>
          <button
            className={`${chipBase} ${!modoCartao && filtroTipo === 'RECEITA' ? chipAtivo : chipInativo}`}
            onClick={() => { setFiltroTipo('RECEITA'); setFiltroCategoria(''); setModoCartao(false), setFiltroCartao('') }}>
            <TrendingUp size={11} />
            Receita
          </button>
          <button
            className={`${chipBase} ${!modoCartao && filtroTipo === 'DESPESA' ? chipAtivo : chipInativo}`}
            onClick={() => { setFiltroTipo('DESPESA'); setFiltroCategoria(''); setModoCartao(false), setFiltroCartao('') }}>
            <TrendingDown size={11} />
            Despesa
          </button>
          <button
            className={`${chipBase} ${!modoCartao && filtroTipo === 'TRANSFERENCIA' ? chipAtivo : chipInativo}`}
            onClick={() => { setFiltroTipo('TRANSFERENCIA'); setFiltroCategoria(''); setModoCartao(false); setFiltroCartao('') }}>
            <ArrowRightLeft size={11} />
            Transferência
          </button>
          <button
            className={`${chipBase} ${modoCartao ? chipAtivo : chipInativo}`}
            onClick={() => { setModoCartao(true); setFiltroTipo(''); setFiltroCartao('') }}>
            <CreditCard size={11} />
            Cartão
          </button>

          {/* Divisor */}
          <div className="w-px h-5 bg-borda shrink-0" />

          {/* Select de conta estilizado como chip */}
          <select
            className="text-xs px-3 py-1.5 rounded-full font-medium border border-borda bg-superficie text-conteudo-suave focus:border-acento focus:outline-none cursor-pointer"
            value={contaId ?? ''}
            onChange={e => definirContaId(e.target.value ? Number(e.target.value) : undefined)}>
            <option value="">Conta</option>
            {[...contas].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')).map(c => (
              <option key={c.id} value={c.id}>{c.nome}</option>
            ))}
          </select>

          {/* Select de categoria estilizado como chip */}
          <select
            className="text-xs px-3 py-1.5 rounded-full font-medium border border-borda bg-superficie text-conteudo-suave focus:border-acento focus:outline-none cursor-pointer"
            value={filtroCategoria}
            onChange={e => setFiltroCategoria(e.target.value ? Number(e.target.value) : '')}>
            <option value="">Categoria</option>
            {categorias
              .filter(c => !filtroTipo || filtroTipo === 'TRANSFERENCIA' || c.tipo === filtroTipo)
              .sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR'))
              .map(c => <option key={c.id} value={c.id}>{c.nome}</option>)}
          </select>

          {/* Select de centro de custo estilizado como chip */}
          {centrosCusto.length > 0 && (
            <select
              className="text-xs px-3 py-1.5 rounded-full font-medium border border-borda bg-superficie text-conteudo-suave focus:border-acento focus:outline-none cursor-pointer"
              value={filtroCentroCusto}
              onChange={e => setFiltroCentroCusto(e.target.value ? Number(e.target.value) : '')}>
              <option value="">Centro de Custo</option>
              {[...centrosCusto].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')).map(cc => (
                <option key={cc.id} value={cc.id}>{cc.nome}</option>
              ))}
            </select>
          )}

          {/* Select de cartão estilizado como chip */}
          <select
            className="text-xs px-3 py-1.5 rounded-full font-medium border border-borda bg-superficie text-conteudo-suave focus:border-acento focus:outline-none cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
            value={filtroCartao}
            disabled={!modoCartao && (filtroTipo === 'RECEITA' || filtroTipo === 'TRANSFERENCIA')}
            onChange={e => setFiltroCartao(e.target.value ? Number(e.target.value) : '')}>
            <option value="">Cartão</option>
            {[...cartoes].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')).map(c => (
              <option key={c.id} value={c.id}>{c.nome}</option>
            ))}
          </select>

          {/* Select de status estilizado como chip */}
          <select
            className="text-xs px-3 py-1.5 rounded-full font-medium border border-borda bg-superficie text-conteudo-suave focus:border-acento focus:outline-none cursor-pointer"
            value={filtroStatus}
            onChange={e => setFiltroStatus((e.target.value || '') as StatusTransacao | '')}>
            <option value="">Status</option>
            {Object.entries(rotuloStatus).map(([status, rotulo]) => (
              <option key={status} value={status}>{rotulo}</option>
            ))}
          </select>

          {/* Select cartão sim/não — controla se itens de fatura aparecem na lista mista */}
          {!modoCartao && !filtroCartao && filtroTipo !== 'RECEITA' && filtroTipo !== 'TRANSFERENCIA' && (
            <select
              className={`text-xs px-3 py-1.5 rounded-full font-medium border focus:outline-none cursor-pointer ${exibirCartao ? 'border-borda bg-superficie text-conteudo-suave focus:border-acento' : 'border-acento bg-acento/10 text-acento'}`}
              value={exibirCartao ? 'sim' : 'nao'}
              onChange={e => setExibirCartao(e.target.value === 'sim')}>
              <option value="sim">Cartão: Sim</option>
              <option value="nao">Cartão: Não</option>
            </select>
          )}

          {/* Chip de vencimento — não se aplica a itens de cartão */}
          {!modoCartao && (
            <button
              className={`${chipBase} ${(filtroDataInicio || filtroDataFim) ? chipAtivo : chipInativo}`}
              onClick={() => setVencimentoAberto(!vencimentoAberto)}>
              <CalendarDays size={12} />
              Vencimento
            </button>
          )}

          {/* Ações à direita */}
          <div className="ml-auto flex gap-2">
            <button className="btn-ghost text-xs py-1.5 px-3 flex items-center gap-1">
              <Upload size={13} />
              Importar
            </button>
            <button className="btn-ghost text-xs py-1.5 px-3 flex items-center gap-1">
              <Download size={13} />
              Exportar
            </button>
            <button
              className="btn-primary text-xs py-1.5 px-3 flex items-center gap-1"
              onClick={() => { setEditing(undefined); setShowForm(true) }}>
              <Plus size={14} />
              Novo lançamento
            </button>
          </div>
        </div>

        {/* Linha 2: painel de período de vencimento (condicional) */}
        {vencimentoAberto && (
          <div className="flex flex-wrap gap-3 items-center pt-3 mt-2 border-t border-borda">
            <span className="text-xs text-conteudo-suave font-medium">Período de vencimento:</span>
            <input
              type="date"
              className="input text-sm py-1.5"
              style={{ width: 'auto' }}
              value={localDataInicio}
              onChange={e => setLocalDataInicio(e.target.value)}
            />
            <span className="text-xs text-conteudo-suave">até</span>
            <input
              type="date"
              className="input text-sm py-1.5"
              style={{ width: 'auto' }}
              value={localDataFim}
              onChange={e => setLocalDataFim(e.target.value)}
            />
            <button
              className="btn-primary text-xs px-3 py-1.5"
              onClick={() => { setFiltroDataInicio(localDataInicio || undefined); setFiltroDataFim(localDataFim || undefined) }}>
              Aplicar
            </button>
            <button
              className="btn-ghost text-xs px-3 py-1.5"
              onClick={() => {
                setLocalDataInicio('')
                setLocalDataFim('')
                setFiltroDataInicio(undefined)
                setFiltroDataFim(undefined)
                setVencimentoAberto(false)
              }}>
              Limpar
            </button>
          </div>
        )}
      </div>

      {/* Tabela de lançamentos */}
      {isLoading ? (
        <div className="flex justify-center py-12">
          <Spinner />
        </div>
      ) : visiveis.length === 0 ? (
        <div className="card text-center py-12 text-conteudo-suave">
          <p>Nenhum lançamento encontrado.</p>
          <button onClick={() => setShowForm(true)} className="mt-3 text-acento hover:opacity-80 text-sm">
            + Adicionar o primeiro
          </button>
        </div>
      ) : (
        <>
          {/* Desktop: tabela */}
          <div className="hidden md:block card p-0 overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-sm border-collapse">
                <thead>
                  <tr className="border-b border-borda">
                    <th className="px-4 py-3 text-left text-xs font-semibold text-conteudo-suave uppercase tracking-wide w-[110px]">Data</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-conteudo-suave uppercase tracking-wide">Descrição</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-conteudo-suave uppercase tracking-wide">Categoria</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-conteudo-suave uppercase tracking-wide">Centro de Custo</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-conteudo-suave uppercase tracking-wide">Conta</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-conteudo-suave uppercase tracking-wide">Status</th>
                    <th className="px-4 py-3 text-right text-xs font-semibold text-conteudo-suave uppercase tracking-wide">Valor</th>
                    <th className="px-4 py-3 w-[48px]" />
                  </tr>
                </thead>
                <tbody>
                  {visivelsPaginados.map(lanc => lanc.origem === 'FATURA' ? (
                    <tr key={lanc.id} className="group border-b border-borda last:border-b-0 hover:bg-superficie-2 transition-colors">
                      <td className="px-4 py-3 text-xs text-conteudo-suave whitespace-nowrap">
                        {format(parseISO(lanc.fatura.dataFechamento), 'dd/MM/yyyy')}
                      </td>
                      <td className="px-4 py-3">
                        <div className="font-medium text-conteudo">Fatura {lanc.fatura.cartao.nome}</div>
                        <div className="text-xs text-conteudo-suave mt-0.5">
                          {lanc.fatura.cartao.contaPagamento.nome} · vence {format(parseISO(lanc.fatura.dataVencimento), 'dd/MM/yyyy')}
                        </div>
                      </td>
                      <td className="px-4 py-3 text-xs text-conteudo-suave">—</td>
                      <td className="px-4 py-3 text-xs text-conteudo-suave">—</td>
                      <td className="px-4 py-3 text-sm text-conteudo-suave">{lanc.fatura.cartao.contaPagamento.nome}</td>
                      <td className="px-4 py-3"><BadgeStatus status={lanc.fatura.status} /></td>
                      <td className="px-4 py-3 text-right">
                        <span className="font-bold text-red-500 whitespace-nowrap">-{fmt(lanc.fatura.valor)}</span>
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex gap-0.5 justify-end opacity-0 group-hover:opacity-100 transition-opacity">
                          {(lanc.fatura.status === 'PENDENTE' || lanc.fatura.status === 'ATRASADA') && (
                            <button onClick={() => setPagarModal({ id: lanc.fatura.transacaoDespesaId, tipo: 'DESPESA', status: lanc.fatura.status })}
                              title="Marcar fatura como paga"
                              className="p-1.5 rounded-lg hover:bg-emerald-900/40 text-conteudo-suave hover:text-emerald-500 transition-colors">
                              <CheckCircle2 size={14} />
                            </button>
                          )}
                          {lanc.fatura.status === 'PAGA' && (
                            <button onClick={() => estornarMutation.mutate(lanc.fatura.transacaoDespesaId)}
                              title="Estornar (voltar para pendente)"
                              className="p-1.5 rounded-lg hover:bg-amber-900/40 text-conteudo-suave hover:text-amber-500 transition-colors">
                              <Undo2 size={14} />
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ) : lanc.origem === 'TRANSACAO' ? (
                    <tr key={lanc.id} className={`group border-b border-borda last:border-b-0 hover:bg-superficie-2 transition-colors ${lanc.tx.status === 'CANCELADA' ? 'opacity-60' : ''}`}>
                      <td className="px-4 py-3 text-xs text-conteudo-suave whitespace-nowrap">
                        {format(parseISO(lanc.tx.data), 'dd/MM/yyyy')}
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-1.5 font-medium text-conteudo">
                          {lanc.tx.tipo === 'TRANSFERENCIA'
                            ? (lanc.tx.descricao || 'Transferência')
                            : (lanc.tx.descricao || lanc.tx.categoria?.nome || '—')}
                          {lanc.tx.fixa && <Repeat size={11} className="text-acento shrink-0" aria-label="Fixa" />}
                          {lanc.tx.totalParcelas && (
                            <span className="text-xs text-conteudo-suave flex items-center gap-0.5">
                              <Layers size={10} />{lanc.tx.numeroParcela}/{lanc.tx.totalParcelas}
                            </span>
                          )}
                        </div>
                        <div className="text-xs text-conteudo-suave mt-0.5">
                          {lanc.tx.tipo === 'TRANSFERENCIA' ? (
                            <span className="flex items-center gap-1">
                              <ArrowRightLeft size={10} />
                              {lanc.tx.direcaoTransferencia === 'SAIDA'
                                ? `${lanc.tx.conta.nome} → ${lanc.tx.contaVinculada?.nome ?? '—'}`
                                : `${lanc.tx.contaVinculada?.nome ?? '—'} → ${lanc.tx.conta.nome}`}
                            </span>
                          ) : (
                            `${lanc.tx.conta.nome}${lanc.tx.tipoPagamento === 'CREDITO' ? ' · Crédito' : ''}`
                          )}
                        </div>
                      </td>
                      <td className="px-4 py-3">
                        {lanc.tx.categoria ? (
                          <div className="flex items-center gap-1.5">
                            <div className="w-2 h-2 rounded-full shrink-0" style={{ background: lanc.tx.categoria.cor }} />
                            <span className="text-xs text-conteudo whitespace-nowrap">{lanc.tx.categoria.nome}</span>
                          </div>
                        ) : <span className="text-xs text-conteudo-suave">—</span>}
                      </td>
                      <td className="px-4 py-3">
                        {lanc.tx.centroCusto ? (
                          <div className="flex items-center gap-1.5">
                            <div className="w-2 h-2 rounded-full shrink-0" style={{ background: lanc.tx.centroCusto.cor }} />
                            <span className="text-xs text-conteudo whitespace-nowrap">{lanc.tx.centroCusto.nome}</span>
                          </div>
                        ) : <span className="text-xs text-conteudo-suave">—</span>}
                      </td>
                      <td className="px-4 py-3 text-sm text-conteudo-suave whitespace-nowrap">{lanc.tx.conta.nome}</td>
                      <td className="px-4 py-3"><BadgeStatus status={lanc.tx.status} /></td>
                      <td className="px-4 py-3 text-right">
                        <div className="whitespace-nowrap">
                          <span className={`font-bold ${lanc.tx.tipo === 'TRANSFERENCIA' ? 'text-blue-500' : lanc.tx.tipo === 'RECEITA' ? 'text-emerald-500' : 'text-red-500'}`}>
                            {lanc.tx.tipo === 'TRANSFERENCIA'
                              ? (lanc.tx.direcaoTransferencia === 'ENTRADA' ? '+' : '-')
                              : (lanc.tx.tipo === 'RECEITA' ? '+' : '-')}
                            {fmt(lanc.tx.valor + (lanc.tx.multa ?? 0))}
                          </span>
                          {lanc.tx.multa != null && (
                            <div className="text-xs text-orange-500">+ multa {fmt(lanc.tx.multa)}</div>
                          )}
                        </div>
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex gap-0.5 justify-end opacity-0 group-hover:opacity-100 transition-opacity">
                          {(lanc.tx.status === 'PENDENTE' || lanc.tx.status === 'ATRASADA') && (
                            <button onClick={() => setPagarModal({ id: lanc.tx.id, tipo: lanc.tx.tipo, status: lanc.tx.status })}
                              title={lanc.tx.tipo === 'RECEITA' ? 'Marcar como recebida' : lanc.tx.tipo === 'TRANSFERENCIA' ? 'Marcar como transferida' : 'Marcar como paga'}
                              className="p-1.5 rounded-lg hover:bg-emerald-900/40 text-conteudo-suave hover:text-emerald-500 transition-colors">
                              <CheckCircle2 size={14} />
                            </button>
                          )}
                          {lanc.tx.status === 'PAGA' && (
                            <button onClick={() => estornarMutation.mutate(lanc.tx.id)}
                              title="Estornar (voltar para pendente)"
                              className="p-1.5 rounded-lg hover:bg-amber-900/40 text-conteudo-suave hover:text-amber-500 transition-colors">
                              <Undo2 size={14} />
                            </button>
                          )}
                          {lanc.tx.status !== 'CANCELADA' && (
                            <button onClick={() => abrirCancelModal(lanc.tx)} title="Cancelar"
                              className="p-1.5 rounded-lg hover:bg-orange-900/40 text-conteudo-suave hover:text-orange-500 transition-colors">
                              <Ban size={14} />
                            </button>
                          )}
                          <button
                            onClick={() => { if (!lanc.tx.origemDerivada) { setEditing({ origem: 'TRANSACAO', tx: lanc.tx }); setShowForm(true) } }}
                            disabled={!!lanc.tx.origemDerivada}
                            title={lanc.tx.origemDerivada ? 'Transação derivada — exclua e crie uma nova para corrigir' : undefined}
                            className="p-1.5 rounded-lg hover:bg-superficie-2 text-conteudo-suave hover:text-conteudo transition-colors disabled:opacity-30 disabled:cursor-not-allowed">
                            <Pencil size={14} />
                          </button>
                          {lanc.tx.status === 'CANCELADA' && (
                            <button onClick={() => abrirDeleteModal(lanc.tx)} title="Excluir"
                              className="p-1.5 rounded-lg hover:bg-red-900/40 text-conteudo-suave hover:text-red-500 transition-colors">
                              <Trash2 size={14} />
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ) : (
                    <tr key={lanc.id} className="group border-b border-borda last:border-b-0 hover:bg-superficie-2 transition-colors">
                      <td className="px-4 py-3 text-xs text-conteudo-suave whitespace-nowrap">
                        {format(parseISO(lanc.item.data), 'dd/MM/yyyy')}
                      </td>
                      <td className="px-4 py-3">
                        <div className={`flex items-center gap-1.5 font-medium text-conteudo ${lanc.item.cancelado ? 'line-through text-conteudo-suave' : ''}`}>
                          {lanc.item.descricao || lanc.item.categoria?.nome || '—'}
                          {lanc.item.fixa && <Repeat size={11} className="text-acento shrink-0" aria-label="Fixa" />}
                          {lanc.item.totalParcelas && (
                            <span className="text-xs text-conteudo-suave flex items-center gap-0.5">
                              <Layers size={10} />{lanc.item.numeroParcela}/{lanc.item.totalParcelas}
                            </span>
                          )}
                        </div>
                        <div className="text-xs text-conteudo-suave mt-0.5">{lanc.item.cartaoNome} · Cartão</div>
                      </td>
                      <td className="px-4 py-3">
                        {lanc.item.categoria ? (
                          <div className="flex items-center gap-1.5">
                            <div className="w-2 h-2 rounded-full shrink-0" style={{ background: lanc.item.categoria.cor }} />
                            <span className="text-xs text-conteudo whitespace-nowrap">{lanc.item.categoria.nome}</span>
                          </div>
                        ) : <span className="text-xs text-conteudo-suave">—</span>}
                      </td>
                      <td className="px-4 py-3">
                        {lanc.item.centroCusto ? (
                          <div className="flex items-center gap-1.5">
                            <div className="w-2 h-2 rounded-full shrink-0" style={{ background: lanc.item.centroCusto.cor }} />
                            <span className="text-xs text-conteudo whitespace-nowrap">{lanc.item.centroCusto.nome}</span>
                          </div>
                        ) : <span className="text-xs text-conteudo-suave">—</span>}
                      </td>
                      <td className="px-4 py-3 text-sm text-conteudo-suave">{lanc.item.cartaoNome}</td>
                      <td className="px-4 py-3">
                        <span className={`text-xs px-1.5 py-0.5 rounded-full font-medium ${lanc.item.cancelado ? 'bg-gray-500/15 text-gray-400' : 'bg-violet-500/15 text-violet-400'}`}>
                          {lanc.item.cancelado ? 'Cancelado' : 'No cartão'}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-right">
                        <span className={`font-bold whitespace-nowrap ${lanc.item.cancelado ? 'text-conteudo-suave line-through' : 'text-red-500'}`}>
                          -{fmt(lanc.item.valor)}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex gap-0.5 justify-end opacity-0 group-hover:opacity-100 transition-opacity">
                          {!lanc.item.cancelado && (
                            <>
                              <button onClick={() => cancelarItemMutation.mutate(lanc.item.id)} title="Cancelar"
                                className="p-1.5 rounded-lg hover:bg-orange-900/40 text-conteudo-suave hover:text-orange-500 transition-colors">
                                <Ban size={14} />
                              </button>
                              <button onClick={() => { setEditing({ origem: 'ITEM_FATURA', item: lanc.item }); setShowForm(true) }}
                                className="p-1.5 rounded-lg hover:bg-superficie-2 text-conteudo-suave hover:text-conteudo transition-colors">
                                <Pencil size={14} />
                              </button>
                              <button onClick={() => setConfirmarExcluirItem(lanc.item.id)}
                                className="p-1.5 rounded-lg hover:bg-red-900/40 text-conteudo-suave hover:text-red-500 transition-colors">
                                <Trash2 size={14} />
                              </button>
                            </>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {/* Paginação da tabela */}
            {totalPaginas > 1 && <div className="border-t border-borda px-4 py-3 flex items-center justify-between">
              <span className="text-xs text-conteudo-suave">
                {visiveis.length} transações · Página {pagina} de {totalPaginas}
              </span>
              <div className="flex items-center gap-1">
                <button onClick={() => setPagina(p => Math.max(1, p - 1))} disabled={pagina === 1}
                  className="w-8 h-8 flex items-center justify-center rounded-lg border border-borda text-conteudo-suave hover:border-acento hover:text-acento disabled:opacity-40 transition-colors">
                  <ChevronLeft size={13} />
                </button>
                {Array.from({ length: totalPaginas }, (_, i) => i + 1)
                  .filter(p => p === 1 || p === totalPaginas || Math.abs(p - pagina) <= 1)
                  .map((p, idx, arr) => (
                    <>
                      {idx > 0 && arr[idx - 1] !== p - 1 && (
                        <span key={`dots-${p}`} className="text-xs text-conteudo-suave px-1">...</span>
                      )}
                      <button key={p} onClick={() => setPagina(p)}
                        className={`w-8 h-8 flex items-center justify-center rounded-lg border text-sm transition-colors ${p === pagina ? 'bg-acento text-white border-acento' : 'border-borda text-conteudo-suave hover:border-acento hover:text-acento'}`}>
                        {p}
                      </button>
                    </>
                  ))}
                <button onClick={() => setPagina(p => Math.min(totalPaginas, p + 1))} disabled={pagina === totalPaginas}
                  className="w-8 h-8 flex items-center justify-center rounded-lg border border-borda text-conteudo-suave hover:border-acento hover:text-acento disabled:opacity-40 transition-colors">
                  <ChevronRight size={13} />
                </button>
              </div>
            </div>}
          </div>

          {/* Mobile: cards */}
          <div className="md:hidden space-y-2">
            {visivelsPaginados.map(lanc => lanc.origem === 'FATURA' ? (
              <div key={lanc.id} className="card flex items-center gap-3 px-4 py-3">
                <div className="w-9 h-9 rounded-xl flex items-center justify-center shrink-0"
                  style={{ background: `${lanc.fatura.cartao.cor}20` }}>
                  <CreditCard size={16} style={{ color: lanc.fatura.cartao.cor }} />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="font-medium text-conteudo text-sm truncate">Fatura {lanc.fatura.cartao.nome}</div>
                  <div className="text-xs text-conteudo-suave mt-0.5">
                    {format(parseISO(lanc.fatura.dataFechamento), 'dd/MM')} · {lanc.fatura.cartao.contaPagamento.nome}
                  </div>
                </div>
                <div className="text-right shrink-0">
                  <div className="font-bold text-red-500 text-sm">-{fmt(lanc.fatura.valor)}</div>
                  <BadgeStatus status={lanc.fatura.status} />
                </div>
              </div>
            ) : lanc.origem === 'TRANSACAO' ? (
              <div key={lanc.id} className={`card flex items-center gap-3 px-4 py-3 ${lanc.tx.status === 'CANCELADA' ? 'opacity-60' : ''}`}>
                <div className="w-9 h-9 rounded-xl flex items-center justify-center shrink-0"
                  style={{ background: `${lanc.tx.tipo === 'TRANSFERENCIA' ? '#3b82f6' : (lanc.tx.categoria?.cor ?? '#6b7280')}20` }}>
                  {lanc.tx.tipo === 'RECEITA' ? <TrendingUp size={16} className="text-emerald-500" />
                    : lanc.tx.tipo === 'TRANSFERENCIA' ? <ArrowRightLeft size={16} className="text-blue-500" />
                    : <TrendingDown size={16} className="text-red-500" />}
                </div>
                <div className="flex-1 min-w-0">
                  <div className="font-medium text-conteudo text-sm truncate flex items-center gap-1.5">
                    {lanc.tx.tipo === 'TRANSFERENCIA' ? (lanc.tx.descricao || 'Transferência') : (lanc.tx.descricao || lanc.tx.categoria?.nome || '—')}
                    {lanc.tx.fixa && <Repeat size={11} className="text-acento shrink-0" aria-label="Fixa" />}
                  </div>
                  <div className="text-xs text-conteudo-suave mt-0.5">
                    {format(parseISO(lanc.tx.data), 'dd/MM')} · {lanc.tx.conta.nome}
                    {lanc.tx.categoria ? ` · ${lanc.tx.categoria.nome}` : ''}
                  </div>
                </div>
                <div className="text-right shrink-0">
                  <div className={`font-bold text-sm ${lanc.tx.tipo === 'TRANSFERENCIA' ? 'text-blue-500' : lanc.tx.tipo === 'RECEITA' ? 'text-emerald-500' : 'text-red-500'}`}>
                    {lanc.tx.tipo === 'TRANSFERENCIA'
                      ? (lanc.tx.direcaoTransferencia === 'ENTRADA' ? '+' : '-')
                      : (lanc.tx.tipo === 'RECEITA' ? '+' : '-')}
                    {fmt(lanc.tx.valor + (lanc.tx.multa ?? 0))}
                  </div>
                  <BadgeStatus status={lanc.tx.status} />
                </div>
              </div>
            ) : (
              <div key={lanc.id} className="card flex items-center gap-3 px-4 py-3">
                <div className="w-9 h-9 rounded-xl flex items-center justify-center shrink-0"
                  style={{ background: `${lanc.item.categoria?.cor ?? '#6b7280'}20` }}>
                  <CreditCard size={16} style={{ color: lanc.item.categoria?.cor ?? '#6b7280' }} />
                </div>
                <div className="flex-1 min-w-0">
                  <div className={`font-medium text-conteudo text-sm truncate flex items-center gap-1.5 ${lanc.item.cancelado ? 'line-through text-conteudo-suave' : ''}`}>
                    {lanc.item.descricao || lanc.item.categoria?.nome || '—'}
                    {lanc.item.fixa && <Repeat size={11} className="text-acento shrink-0" aria-label="Fixa" />}
                  </div>
                  <div className="text-xs text-conteudo-suave mt-0.5">
                    {format(parseISO(lanc.item.data), 'dd/MM')} · {lanc.item.cartaoNome}
                  </div>
                </div>
                <div className="text-right shrink-0">
                  <div className={`font-bold text-sm ${lanc.item.cancelado ? 'text-conteudo-suave line-through' : 'text-red-500'}`}>
                    -{fmt(lanc.item.valor)}
                  </div>
                  <span className="text-xs text-violet-400">{lanc.item.cancelado ? 'Cancelado' : 'No cartão'}</span>
                </div>
              </div>
            ))}
            {/* Paginação mobile */}
            {totalPaginas > 1 && (
              <div className="card p-3 flex items-center justify-between">
                <span className="text-xs text-conteudo-suave">
                  {visiveis.length} transações · Pág. {pagina}/{totalPaginas}
                </span>
                <div className="flex items-center gap-1">
                  <button onClick={() => setPagina(p => Math.max(1, p - 1))} disabled={pagina === 1}
                    className="w-8 h-8 flex items-center justify-center rounded-lg border border-borda text-conteudo-suave hover:border-acento hover:text-acento disabled:opacity-40 transition-colors">
                    <ChevronLeft size={13} />
                  </button>
                  <span className="text-sm text-conteudo px-2">{pagina} / {totalPaginas}</span>
                  <button onClick={() => setPagina(p => Math.min(totalPaginas, p + 1))} disabled={pagina === totalPaginas}
                    className="w-8 h-8 flex items-center justify-center rounded-lg border border-borda text-conteudo-suave hover:border-acento hover:text-acento disabled:opacity-40 transition-colors">
                    <ChevronRight size={13} />
                  </button>
                </div>
              </div>
            )}
          </div>
        </>
      )}

      {showForm && (
        <FormularioTransacao
          onClose={() => { setShowForm(false); setEditing(undefined) }}
          editing={editing}
        />
      )}

      {/* Modal de pagamento (com multa opcional) */}
      {pagarModal && (
        <PagarModal
          tipo={pagarModal.tipo}
          status={pagarModal.status}
          aoFechar={() => setPagarModal(null)}
          aoConfirmar={(dataPagamento, multa) => pagarMutation.mutate({ id: pagarModal.id, dataPagamento, multa })}
          carregando={pagarMutation.isPending}
        />
      )}

      {/* Modal de confirmação de exclusão de item de fatura */}
      {confirmarExcluirItem !== null && (
        <SobreposicaoModal aoFechar={() => setConfirmarExcluirItem(null)}>
          <div className="cartao-modal max-w-sm">
            <div className="cartao-modal-cabecalho">
              <h3 className="text-base font-semibold text-conteudo">Excluir compra</h3>
            </div>
            <div className="cartao-modal-corpo space-y-4">
              <p className="text-sm text-conteudo-suave">Tem certeza que deseja excluir esta compra?</p>
              <div className="space-y-2">
                <button
                  onClick={() => excluirItemMutation.mutate(confirmarExcluirItem)}
                  disabled={excluirItemMutation.isPending}
                  className="w-full py-2 px-4 rounded-lg border border-red-800 text-red-400 hover:bg-red-900/30 transition-colors text-sm font-medium disabled:opacity-60">
                  {excluirItemMutation.isPending ? 'Excluindo...' : 'Excluir'}
                </button>
                <button
                  onClick={() => setConfirmarExcluirItem(null)}
                  disabled={excluirItemMutation.isPending}
                  className="w-full py-2 px-4 rounded-lg border border-borda text-conteudo-suave hover:bg-superficie-2 transition-colors text-sm disabled:opacity-60">
                  Cancelar
                </button>
              </div>
            </div>
          </div>
        </SobreposicaoModal>
      )}

      {/* Modal de exclusão */}
      {deleteModal && (
        <SobreposicaoModal aoFechar={() => setDeleteModal(null)}>
          <div className="cartao-modal max-w-sm">
            <div className="cartao-modal-cabecalho">
              <h3 className="text-base font-semibold text-conteudo">Excluir lançamento</h3>
            </div>
            <div className="cartao-modal-corpo space-y-3">
              <p className="text-sm text-conteudo-suave">
                {deleteModal.tx.tipo === 'TRANSFERENCIA'
                  ? 'Isso exclui as duas pontas da transferência (saída e entrada).'
                  : deleteModal.tx.grupoParcelaId
                    ? 'Este lançamento faz parte de um parcelamento.'
                    : deleteModal.tx.fixa
                      ? 'Este é um lançamento fixo.'
                      : 'Tem certeza que deseja excluir?'}
              </p>
              {deleteModal.carregando && (
                <p className="text-sm text-conteudo-suave animate-pulse">Verificando impacto...</p>
              )}
              {deleteModal.impacto?.bloqueado && (
                <div className="rounded-lg border border-red-800/50 bg-red-900/20 p-3 text-sm text-red-400">
                  {deleteModal.impacto.motivoBloqueio}
                </div>
              )}
              {!deleteModal.impacto?.bloqueado && deleteModal.impacto?.origem && (
                <div className="rounded-lg border border-orange-800/50 bg-orange-900/20 p-3 text-sm text-orange-400">
                  ⚠ {deleteModal.impacto.origem.efeito}
                </div>
              )}
              {deleteModal.erro && (
                <div className="rounded-lg border border-red-800/50 bg-red-900/20 p-3 text-sm text-red-400">
                  {deleteModal.erro}
                </div>
              )}
              <div className="space-y-2">
                {!deleteModal.impacto?.bloqueado && (
                  <>
                    <button onClick={() => deleteMutation.mutate({ id: deleteModal.tx.id, scope: 'UNICA' })}
                      className="w-full btn-danger">
                      {deleteModal.tx.tipo === 'TRANSFERENCIA' ? 'Excluir transferência' : 'Excluir só este'}
                    </button>
                    {deleteModal.tx.grupoParcelaId && (
                      <button onClick={() => deleteMutation.mutate({ id: deleteModal.tx.id, scope: 'FUTURAS' })}
                        className="w-full py-2.5 rounded-lg border border-red-800 text-red-500 hover:bg-red-900/20 transition-colors text-sm font-medium">
                        Excluir este e os próximos
                      </button>
                    )}
                    {deleteModal.tx.grupoParcelaId && (
                      <button onClick={() => deleteMutation.mutate({ id: deleteModal.tx.id, scope: 'GRUPO' })}
                        className="w-full py-2.5 rounded-lg border border-red-800 text-red-500 hover:bg-red-900/20 transition-colors text-sm font-medium">
                        Excluir todas as parcelas
                      </button>
                    )}
                    {deleteModal.tx.fixa && (
                      <button onClick={() => deleteMutation.mutate({ id: deleteModal.tx.id, scope: 'FUTURAS' })}
                        className="w-full py-2.5 rounded-lg border border-red-800 text-red-500 hover:bg-red-900/20 transition-colors text-sm font-medium">
                        Excluir este e os próximos meses
                      </button>
                    )}
                  </>
                )}
                <button onClick={() => setDeleteModal(null)}
                  className="w-full py-2.5 rounded-lg border border-borda text-conteudo-suave hover:text-conteudo transition-colors text-sm font-medium">
                  {deleteModal.impacto?.bloqueado ? 'Voltar' : 'Cancelar'}
                </button>
              </div>
            </div>
          </div>
        </SobreposicaoModal>
      )}

      {/* Modal de cancelamento */}
      {cancelModal && (
        <SobreposicaoModal aoFechar={() => setCancelModal(null)}>
          <div className="cartao-modal max-w-sm">
            <div className="cartao-modal-cabecalho">
              <h3 className="text-base font-semibold text-conteudo">Cancelar lançamento</h3>
            </div>
            <div className="cartao-modal-corpo space-y-3">
              <p className="text-sm text-conteudo-suave">
                {cancelModal.tx.tipo === 'TRANSFERENCIA'
                  ? 'As duas pontas da transferência (saída e entrada) ficam marcadas como canceladas e o saldo é revertido, se já estava paga.'
                  : 'O lançamento fica marcado como cancelado (e o saldo é revertido, se já estava pago), mas continua no histórico — diferente de excluir.'}
              </p>
              {cancelModal.carregando && (
                <p className="text-sm text-conteudo-suave animate-pulse">Verificando impacto...</p>
              )}
              {cancelModal.impacto?.bloqueado && (
                <div className="rounded-lg border border-red-800/50 bg-red-900/20 p-3 text-sm text-red-400">
                  {cancelModal.impacto.motivoBloqueio}
                </div>
              )}
              {!cancelModal.impacto?.bloqueado && cancelModal.impacto?.origem && (
                <div className="rounded-lg border border-orange-800/50 bg-orange-900/20 p-3 text-sm text-orange-400">
                  ⚠ {cancelModal.impacto.origem.efeito}
                </div>
              )}
              {cancelModal.erro && (
                <div className="rounded-lg border border-red-800/50 bg-red-900/20 p-3 text-sm text-red-400">
                  {cancelModal.erro}
                </div>
              )}
              <div className="space-y-2">
                {!cancelModal.impacto?.bloqueado && (
                  <>
                    <button onClick={() => cancelarMutation.mutate({ id: cancelModal.tx.id, scope: 'UNICA' })}
                      className="w-full py-2.5 rounded-lg border border-orange-800 text-orange-500 hover:bg-orange-900/20 transition-colors text-sm font-medium">
                      {cancelModal.tx.tipo === 'TRANSFERENCIA' ? 'Cancelar transferência' : 'Cancelar só este'}
                    </button>
                    {cancelModal.tx.grupoParcelaId && (
                      <button onClick={() => cancelarMutation.mutate({ id: cancelModal.tx.id, scope: 'FUTURAS' })}
                        className="w-full py-2.5 rounded-lg border border-orange-800 text-orange-500 hover:bg-orange-900/20 transition-colors text-sm font-medium">
                        Cancelar este e os próximos
                      </button>
                    )}
                    {cancelModal.tx.grupoParcelaId && (
                      <button onClick={() => cancelarMutation.mutate({ id: cancelModal.tx.id, scope: 'GRUPO' })}
                        className="w-full py-2.5 rounded-lg border border-orange-800 text-orange-500 hover:bg-orange-900/20 transition-colors text-sm font-medium">
                        Cancelar todas as parcelas
                      </button>
                    )}
                    {cancelModal.tx.fixa && (
                      <button onClick={() => cancelarMutation.mutate({ id: cancelModal.tx.id, scope: 'FUTURAS' })}
                        className="w-full py-2.5 rounded-lg border border-orange-800 text-orange-500 hover:bg-orange-900/20 transition-colors text-sm font-medium">
                        Cancelar este e os próximos meses
                      </button>
                    )}
                  </>
                )}
                <button onClick={() => setCancelModal(null)}
                  className="w-full py-2.5 rounded-lg border border-borda text-conteudo-suave hover:text-conteudo transition-colors text-sm font-medium">
                  Voltar
                </button>
              </div>
            </div>
          </div>
        </SobreposicaoModal>
      )}
    </div>
  )
}

function PagarModal({ tipo, status, aoFechar, aoConfirmar, carregando }: {
  tipo: TipoTransacao
  status: StatusTransacao
  aoFechar: () => void
  aoConfirmar: (dataPagamento: string, multa?: number) => void
  carregando: boolean
}) {
  const hoje = format(new Date(), 'yyyy-MM-dd')
  const [dataPagamento, setDataPagamento] = useState(hoje)
  const [multa, setMulta] = useState('')
  const atrasada = status === 'ATRASADA'

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    aoConfirmar(dataPagamento, multa ? Number(multa) : undefined)
  }

  return (
    <SobreposicaoModal aoFechar={aoFechar}>
      <div className="cartao-modal max-w-sm">
        <div className="cartao-modal-cabecalho">
          <h3 className="text-base font-semibold text-conteudo">
            {tipo === 'RECEITA' ? 'Marcar como recebida' : 'Marcar como paga'}
          </h3>
        </div>
        <form onSubmit={handleSubmit} className="cartao-modal-corpo">
          <div>
            <label className="label">Data de pagamento</label>
            <input
              className="input" type="date" max={hoje}
              value={dataPagamento} onChange={e => setDataPagamento(e.target.value)} required
            />
          </div>
          {tipo === 'DESPESA' && (
            <div>
              <label className="label">Multa por atraso (R$, opcional)</label>
              <input
                className="input" type="number" step="0.01" min="0" placeholder="0,00"
                value={multa} onChange={e => setMulta(e.target.value)}
              />
              {atrasada && (
                <p className="text-xs text-orange-500 mt-1">
                  Esse lançamento está atrasado — a multa é somada ao valor debitado da conta.
                </p>
              )}
            </div>
          )}
          <div className="flex gap-3 pt-2">
            <button type="button" onClick={aoFechar} className="flex-1 py-2.5 rounded-lg border border-borda text-conteudo-suave hover:text-conteudo hover:border-conteudo-suave transition-colors text-sm font-medium">
              Cancelar
            </button>
            <button type="submit" disabled={carregando} className="flex-1 btn-primary">
              {carregando ? 'Salvando...' : 'Confirmar'}
            </button>
          </div>
        </form>
      </div>
    </SobreposicaoModal>
  )
}
