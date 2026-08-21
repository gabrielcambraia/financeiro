import { useState, useMemo, useEffect, useRef } from 'react'
import { useParams, Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { ChevronLeft, Upload, Download, Plus } from 'lucide-react'
import { format, parseISO, addMonths, differenceInCalendarDays } from 'date-fns'
import { ptBR } from 'date-fns/locale'
import { buscarCartoes } from '../api/cartoes'
import { buscarItensFaturaPorCiclo, excluirItemFatura, cancelarItemFatura } from '../api/itensFatura'
import { buscarFaturas, buscarFatura } from '../api/faturas'
import { pagarTransacao, estornarTransacao } from '../api/transacoes'
import FormularioCompraCartao from '../components/forms/FormularioCompraCartao'
import ModalConfirmacao from '../components/ModalConfirmacao'
import Spinner from '../components/Spinner'
import LinhaCompraCartao from '../components/LinhaCompraCartao'
import { corStatusFatura as corStatus, rotuloStatusFatura as rotuloStatus } from '../utils/statusTransacao'
import type { ItemFatura } from '../types'

const fmt = (v: number) =>
  new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(v)

export default function CartaoDetalhe() {
  const { id } = useParams()
  const cartaoId = Number(id)
  const qc = useQueryClient()
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<ItemFatura | undefined>()
  const [confirmarExcluirItem, setConfirmarExcluirItem] = useState<ItemFatura | null>(null)

  const hoje = useMemo(() => new Date(), [])
  const mesAtual = useMemo(() => format(hoje, 'yyyy-MM'), [hoje])
  const [mesSelecionado, setMesSelecionado] = useState(mesAtual)
  const [chipBase, setChipBase] = useState(mesAtual)
  const cicloInicializado = useRef(false)

  const { data: cartoes = [], isLoading: carregandoCartoes } = useQuery({
    queryKey: ['cartoes'],
    queryFn: buscarCartoes,
  })
  const cartao = cartoes.find(c => c.id === cartaoId)

  // Assim que o cartão carrega, salta para o ciclo de fatura aberto (pode ser
  // o próximo mês se o fechamento deste mês já tiver passado hoje).
  useEffect(() => {
    if (!cartao || cicloInicializado.current) return
    cicloInicializado.current = true
    const fechamentoMesAtual = new Date(hoje.getFullYear(), hoje.getMonth(), cartao.diaFechamento)
    const mesAberto = hoje > fechamentoMesAtual
      ? format(addMonths(new Date(hoje.getFullYear(), hoje.getMonth(), 1), 1), 'yyyy-MM')
      : mesAtual
    setMesSelecionado(mesAberto)
    setChipBase(mesAberto)
  }, [cartao, hoje, mesAtual])

  // 12 meses: 6 de histórico + ciclo aberto + 5 futuros
  const chips = useMemo(() => {
    const base = parseISO(`${chipBase}-01`)
    return Array.from({ length: 12 }, (_, i) => format(addMonths(base, i - 6), 'yyyy-MM'))
  }, [chipBase])

  const { data: itensAbertos = [] } = useQuery({
    queryKey: ['itensFatura', 'ciclo', cartaoId, mesSelecionado],
    queryFn: () => buscarItensFaturaPorCiclo(cartaoId, mesSelecionado),
    enabled: !!cartaoId,
  })

  const { data: faturas = [] } = useQuery({
    queryKey: ['faturas', cartaoId, mesSelecionado],
    queryFn: () => buscarFaturas(cartaoId, mesSelecionado),
    enabled: !!cartaoId,
  })

  const faturaFechada = faturas[0] ?? null

  const { data: faturaDetalhe } = useQuery({
    queryKey: ['fatura', faturaFechada?.id],
    queryFn: () => buscarFatura(faturaFechada!.id),
    enabled: faturaFechada !== null,
  })

  const faturaAberta = faturaFechada === null
  const itensMostrar: ItemFatura[] = faturaFechada ? (faturaDetalhe?.itens ?? []) : itensAbertos
  const totalFatura = itensMostrar.filter(i => !i.cancelado).reduce((s, i) => s + i.valor, 0)

  const itensPorData = useMemo(() => {
    const grupos = new Map<string, ItemFatura[]>()
    for (const item of itensMostrar) {
      const data = item.data.slice(0, 10)
      if (!grupos.has(data)) grupos.set(data, [])
      grupos.get(data)!.push(item)
    }
    return Array.from(grupos.entries()).sort(([a], [b]) => a.localeCompare(b))
  }, [itensMostrar])

  const limiteUsado = cartao ? cartao.limite - cartao.limiteDisponivel : 0
  const percentualUsado = cartao && cartao.limite > 0 ? (limiteUsado / cartao.limite) * 100 : 0

  const infoFechamento = useMemo(() => {
    if (!cartao) return { dias: 0, data: '' }
    let proximo = new Date(hoje.getFullYear(), hoje.getMonth(), cartao.diaFechamento)
    if (hoje >= proximo) proximo = new Date(hoje.getFullYear(), hoje.getMonth() + 1, cartao.diaFechamento)
    return {
      dias: differenceInCalendarDays(proximo, hoje),
      data: format(proximo, 'dd/MM', { locale: ptBR }),
    }
  }, [cartao, hoje])

  const proximoVencimento = useMemo(() => {
    if (!cartao) return ''
    let data = new Date(hoje.getFullYear(), hoje.getMonth(), cartao.diaVencimento)
    if (hoje > data) data = new Date(hoje.getFullYear(), hoje.getMonth() + 1, cartao.diaVencimento)
    return format(data, 'dd/MM', { locale: ptBR })
  }, [cartao, hoje])

  const invalidarTudo = async () => {
    await Promise.all([
      qc.invalidateQueries({ queryKey: ['itensFatura'] }),
      qc.invalidateQueries({ queryKey: ['faturas'] }),
      qc.invalidateQueries({ queryKey: ['cartoes'] }),
      qc.invalidateQueries({ queryKey: ['fatura'] }),
      qc.invalidateQueries({ queryKey: ['transacoes'] }),
      qc.invalidateQueries({ queryKey: ['painel'] }),
      qc.invalidateQueries({ queryKey: ['contas'] }),
    ])
  }

  const excluirMutation = useMutation({
    mutationFn: excluirItemFatura,
    onSuccess: async () => { await invalidarTudo(); setConfirmarExcluirItem(null); toast.success('Compra excluída') },
  })
  const cancelarMutation = useMutation({
    mutationFn: cancelarItemFatura,
    onSuccess: async () => { await invalidarTudo(); toast.success('Compra cancelada') },
  })
  const pagarMutation = useMutation({
    mutationFn: (id: number) => pagarTransacao(id),
    onSuccess: async () => { await invalidarTudo(); toast.success('Fatura marcada como paga') },
  })
  const estornarMutation = useMutation({
    mutationFn: (id: number) => estornarTransacao(id),
    onSuccess: async () => { await invalidarTudo(); toast.success('Pagamento estornado') },
  })

  if (carregandoCartoes || !cartao) {
    return <div className="flex justify-center items-center h-64"><Spinner tamanho="lg" /></div>
  }

  return (
    <div className="p-4 md:p-6 space-y-5">
      {/* Header */}
      <div className="flex items-center gap-3">
        <Link to="/cartoes" className="p-1.5 rounded-lg hover:bg-superficie-2 text-conteudo-suave hover:text-conteudo transition-colors">
          <ChevronLeft size={20} />
        </Link>
        <div>
          <h1 className="text-xl font-bold text-conteudo">{cartao.nome}</h1>
          <p className="text-sm text-conteudo-suave">Detalhes e fatura do cartão</p>
        </div>
      </div>

      {/* Credit card visual + limit info */}
      <div className="grid grid-cols-1 md:grid-cols-[auto_1fr] gap-5 items-start">
        {/* Visual card */}
        <div
          className="rounded-2xl p-5 text-white flex flex-col justify-between relative overflow-hidden"
          style={{ background: cartao.cor, minWidth: '260px', maxWidth: '300px', minHeight: '164px' }}
        >
          <div
            className="absolute inset-0 pointer-events-none"
            style={{ background: 'radial-gradient(circle at 80% 20%, rgba(255,255,255,0.2) 0%, transparent 55%)' }}
          />
          <div className="relative flex justify-between items-start mb-7">
            <div>
              <div className="text-xs mb-1" style={{ opacity: 0.6 }}>Nexus360</div>
              <div className="text-base font-bold">{cartao.nome}</div>
            </div>
            {cartao.banco && (
              <div className="text-sm font-bold" style={{ opacity: 0.9 }}>{cartao.banco.nome}</div>
            )}
          </div>
          <div className="relative text-sm tracking-widest mb-5" style={{ opacity: 0.85 }}>
            •••• •••• •••• ••••
          </div>
          <div className="relative flex justify-between items-end">
            <div>
              <div className="text-xs mb-0.5" style={{ opacity: 0.5 }}>FECHAMENTO</div>
              <div className="text-sm">Dia {cartao.diaFechamento}</div>
            </div>
            <div className="text-right">
              <div className="text-xs mb-0.5" style={{ opacity: 0.5 }}>VENCIMENTO</div>
              <div className="text-sm">Dia {cartao.diaVencimento}</div>
            </div>
          </div>
        </div>

        {/* Limit + 3 stats */}
        <div className="flex flex-col gap-3">
          <div className="card">
            <div className="flex justify-between items-center mb-2.5">
              <div>
                <div className="text-xs font-semibold uppercase tracking-wide text-conteudo-suave">Limite utilizado</div>
                <div className="flex items-baseline gap-1.5 mt-1">
                  <span className="text-2xl font-bold text-conteudo">{fmt(limiteUsado)}</span>
                  <span className="text-sm text-conteudo-suave">de {fmt(cartao.limite)}</span>
                </div>
              </div>
              <div className="text-right">
                <div className="text-2xl font-bold text-sucesso">{percentualUsado.toFixed(1)}%</div>
                <div className="text-xs text-conteudo-suave">utilizado</div>
              </div>
            </div>
            <div className="h-2 bg-superficie-2 rounded-full overflow-hidden">
              <div
                className="h-full rounded-full bg-acento transition-all"
                style={{ width: `${Math.min(percentualUsado, 100)}%` }}
              />
            </div>
            <div className="flex justify-between text-xs text-conteudo-suave mt-1.5">
              <span>{fmt(limiteUsado)} usado</span>
              <span>{fmt(cartao.limiteDisponivel)} disponível</span>
            </div>
          </div>

          <div className="flex rounded-xl overflow-hidden border border-borda divide-x divide-borda">
            <div className="flex flex-col items-center py-3 px-4 flex-1 bg-superficie">
              <div className="text-lg font-bold text-perigo">{fmt(cartao.faturaAtualTotal)}</div>
              <div className="text-xs text-conteudo-suave mt-0.5 text-center">Fatura atual</div>
            </div>
            <div className="flex flex-col items-center py-3 px-4 flex-1 bg-superficie">
              <div className="text-lg font-bold text-aviso">{proximoVencimento}</div>
              <div className="text-xs text-conteudo-suave mt-0.5 text-center">Vencimento</div>
            </div>
            <div className="flex flex-col items-center py-3 px-4 flex-1 bg-superficie">
              <div className="text-lg font-bold text-conteudo">{infoFechamento.dias}d</div>
              <div className="text-xs text-conteudo-suave mt-0.5 text-center">Para fechar ({infoFechamento.data})</div>
            </div>
          </div>
        </div>
      </div>

      {/* Month chip selector + status + actions */}
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <div className="flex items-center gap-2 overflow-x-auto pb-1 max-w-full scrollbar-hide">
          {chips.map(mes => (
            <button
              key={mes}
              onClick={() => setMesSelecionado(mes)}
              className={`shrink-0 px-4 py-1.5 rounded-full border text-sm font-medium transition-all capitalize ${
                mes === mesSelecionado
                  ? 'bg-sb-fundo text-white border-sb-fundo'
                  : mes > chipBase
                  ? 'border-borda bg-superficie text-conteudo-suave opacity-60 hover:opacity-80'
                  : 'border-borda bg-superficie text-conteudo-suave hover:text-conteudo hover:border-conteudo-suave'
              }`}
            >
              {format(parseISO(`${mes}-01`), 'MMM yyyy', { locale: ptBR })}
            </button>
          ))}
        </div>
        <div className="flex items-center gap-2 flex-wrap">
          {faturaFechada ? (
            <span className={`text-xs px-2.5 py-1 rounded-full font-medium ${corStatus[faturaFechada.status]}`}>
              {rotuloStatus[faturaFechada.status]}
            </span>
          ) : (
            <span className="text-xs px-2.5 py-1 rounded-full font-medium bg-aviso-fundo text-aviso">
              Fatura aberta
            </span>
          )}
          <button className="flex items-center gap-1.5 text-xs py-1.5 px-3 rounded-lg border border-borda bg-superficie text-conteudo-suave hover:text-conteudo transition-colors">
            <Upload size={13} />
            Importar
          </button>
          <button className="flex items-center gap-1.5 text-xs py-1.5 px-3 rounded-lg border border-borda bg-superficie text-conteudo-suave hover:text-conteudo transition-colors">
            <Download size={13} />
            Exportar
          </button>
          {faturaAberta && (
            <button
              onClick={() => { setEditing(undefined); setShowForm(true) }}
              className="btn-primary flex items-center gap-1.5 text-sm py-1.5 px-3"
            >
              <Plus size={14} />
              Nova compra
            </button>
          )}
        </div>
      </div>

      {/* Items grouped by day */}
      <div className="card">
        <div className="flex items-center justify-between pb-3 border-b border-borda mb-1">
          <div className="text-sm font-semibold text-conteudo capitalize">
            {format(parseISO(`${mesSelecionado}-01`), 'MMMM yyyy', { locale: ptBR })}
          </div>
          <div className="text-sm font-bold text-conteudo">
            Total: <span className="text-perigo">{fmt(totalFatura)}</span>
          </div>
        </div>

        {itensPorData.length === 0 ? (
          <p className="text-center py-8 text-conteudo-suave text-sm">Nenhuma compra nesta fatura.</p>
        ) : (
          itensPorData.map(([data, itens]) => {
            const subtotal = itens.filter(i => !i.cancelado).reduce((s, i) => s + i.valor, 0)
            return (
              <div key={data}>
                <div className="flex justify-between items-center py-3 border-b border-borda/50 mt-1">
                  <span className="text-xs font-bold uppercase tracking-wider text-conteudo-suave">
                    {format(parseISO(data), "dd 'de' MMMM", { locale: ptBR })}
                  </span>
                  <span className="text-sm font-bold text-conteudo">{fmt(subtotal)}</span>
                </div>
                {itens.map(item => (
                  <LinhaCompraCartao
                    key={item.id}
                    item={item}
                    editavel={faturaAberta}
                    onEdit={faturaAberta ? () => { setEditing(item); setShowForm(true) } : undefined}
                    onExcluir={faturaAberta ? () => setConfirmarExcluirItem(item) : undefined}
                    onCancelar={faturaAberta ? () => cancelarMutation.mutate(item.id) : undefined}
                  />
                ))}
              </div>
            )
          })
        )}

        <div className="flex justify-end items-center gap-4 pt-3.5 border-t-2 border-borda mt-2">
          <span className="text-sm text-conteudo-suave">Total da fatura</span>
          <span className="text-xl font-bold text-perigo">{fmt(totalFatura)}</span>
          {faturaFechada && (faturaFechada.status === 'PENDENTE' || faturaFechada.status === 'ATRASADA') && (
            <button
              onClick={() => pagarMutation.mutate(faturaFechada.transacaoDespesaId)}
              disabled={pagarMutation.isPending}
              className="btn-primary py-1.5 px-4 text-sm disabled:opacity-60"
            >
              {pagarMutation.isPending ? 'Processando...' : 'Pagar fatura'}
            </button>
          )}
          {faturaFechada && faturaFechada.status === 'PAGA' && (
            <button
              onClick={() => estornarMutation.mutate(faturaFechada.transacaoDespesaId)}
              disabled={estornarMutation.isPending}
              className="py-1.5 px-4 rounded-lg border border-aviso text-aviso hover:bg-aviso/10 text-sm font-medium disabled:opacity-60"
            >
              {estornarMutation.isPending ? 'Processando...' : 'Estornar pagamento'}
            </button>
          )}
        </div>
      </div>

      <ModalConfirmacao
        aberto={confirmarExcluirItem !== null}
        titulo="Excluir compra"
        aoFechar={() => !excluirMutation.isPending && setConfirmarExcluirItem(null)}
        botoes={[{ rotulo: 'Excluir', variante: 'perigo', aoClicar: () => excluirMutation.mutate(confirmarExcluirItem!.id), carregando: excluirMutation.isPending }]}
      >
        {`Deseja excluir a compra "${confirmarExcluirItem?.descricao || confirmarExcluirItem?.categoria?.nome}"?`}
      </ModalConfirmacao>

      {showForm && (
        <FormularioCompraCartao
          cartaoId={cartaoId}
          filialId={cartao?.contaPagamento?.filialId}
          editing={editing}
          onClose={() => { setShowForm(false); setEditing(undefined) }}
        />
      )}
    </div>
  )
}
