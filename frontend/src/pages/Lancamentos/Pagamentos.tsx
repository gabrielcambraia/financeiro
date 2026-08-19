import { useState, useEffect } from 'react'
import type React from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Pencil, CheckCircle2, Ban, Undo2, Repeat, Layers, ChevronLeft, ChevronRight, Plus, Trash2 } from 'lucide-react'
import { format, parseISO } from 'date-fns'
import {
  buscarTransacoes, pagarTransacao, estornarTransacao, cancelarTransacao, excluirTransacao,
  impactoExclusaoTransacao, impactoCancelamentoTransacao,
  type EscopoExclusao,
} from '../../api/transacoes'
import { buscarContas } from '../../api/contas'
import { buscarCategorias } from '../../api/categorias'
import { buscarCentrosCusto } from '../../api/centrosCusto'
import { useLojaFiltro } from '../../store/lojaFiltro'
import SeletorMes from '../../components/SeletorMes'
import FormularioTransacao, { type EdicaoLancamento } from '../../components/forms/FormularioTransacao'
import SobreposicaoModal from '../../components/SobreposicaoModal'
import Spinner from '../../components/Spinner'
import type { Transacao, StatusTransacao, TipoPagamento, RespostaImpacto } from '../../types'

const fmt = (v: number) =>
  new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(v)

const corStatus: Record<StatusTransacao, string> = {
  PAGA: 'bg-emerald-500/15 text-emerald-500',
  PENDENTE: 'bg-amber-500/15 text-amber-500',
  ATRASADA: 'bg-red-500/15 text-red-500',
  CANCELADA: 'bg-gray-500/15 text-gray-400',
}
const rotuloStatus: Record<StatusTransacao, string> = {
  PAGA: 'Paga', PENDENTE: 'Pendente', ATRASADA: 'Atrasada', CANCELADA: 'Cancelada',
}

const chipBase = 'text-xs px-3 py-1.5 rounded-full font-medium transition-colors'
const chipAtivo = 'bg-acento text-white border border-acento'
const chipInativo = 'border border-borda text-conteudo-suave hover:border-acento hover:text-acento bg-superficie'

const ITENS_POR_PAGINA = 20

export default function Pagamentos() {
  const qc = useQueryClient()
  const { mes, contaId, definirContaId } = useLojaFiltro()
  const [filtroTipoPagamento, setFiltroTipoPagamento] = useState<TipoPagamento | ''>('')
  const [filtroStatus, setFiltroStatus] = useState<StatusTransacao | ''>('')
  const [filtroCategoria, setFiltroCategoria] = useState<number | ''>('')
  const [filtroCentroCusto, setFiltroCentroCusto] = useState<number | ''>('')
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<EdicaoLancamento | undefined>()
  const [pagarModal, setPagarModal] = useState<{ id: number; status: StatusTransacao } | null>(null)
  const [deleteModal, setDeleteModal] = useState<{ tx: Transacao; impacto: RespostaImpacto | null; carregando: boolean; erro?: string } | null>(null)
  const [cancelModal, setCancelModal] = useState<{ tx: Transacao; impacto: RespostaImpacto | null; carregando: boolean; erro?: string } | null>(null)
  const [pagina, setPagina] = useState(1)

  useEffect(() => { setPagina(1) }, [mes, contaId, filtroTipoPagamento, filtroStatus, filtroCategoria, filtroCentroCusto])

  const { data: todas = [], isLoading } = useQuery({
    queryKey: ['transacoes', mes, contaId, 'DESPESA', filtroCategoria, filtroCentroCusto],
    queryFn: () => buscarTransacoes({ month: mes, contaId, tipo: 'DESPESA', categoriaId: filtroCategoria || undefined, centroCustoId: filtroCentroCusto || undefined }),
  })

  const transacoes = todas
    .filter(t => !filtroTipoPagamento || t.tipoPagamento === filtroTipoPagamento)
    .filter(t => !filtroStatus || t.status === filtroStatus)

  const { data: contas = [] } = useQuery({ queryKey: ['contas'], queryFn: buscarContas })
  const { data: categorias = [] } = useQuery({ queryKey: ['categorias'], queryFn: () => buscarCategorias() })
  const { data: centrosCusto = [] } = useQuery({ queryKey: ['centros-custo'], queryFn: buscarCentrosCusto })

  const invalidar = async () => {
    await Promise.all([
      qc.invalidateQueries({ queryKey: ['transacoes'] }),
      qc.invalidateQueries({ queryKey: ['painel'] }),
      qc.invalidateQueries({ queryKey: ['contas'] }),
    ])
  }

  const pagarMutation = useMutation({
    mutationFn: ({ id, dataPagamento, multa }: { id: number; dataPagamento: string; multa?: number }) =>
      pagarTransacao(id, { dataPagamento, multa }),
    onSuccess: async () => { await invalidar(); setPagarModal(null); toast.success('Pagamento registrado') },
  })

  const estornarMutation = useMutation({
    mutationFn: (id: number) => estornarTransacao(id),
    onSuccess: async () => { await invalidar(); toast.success('Pagamento estornado') },
  })

  const deleteMutation = useMutation({
    mutationFn: ({ id, scope }: { id: number; scope: EscopoExclusao }) => excluirTransacao(id, scope),
    onSuccess: async () => { await invalidar(); setDeleteModal(null); toast.success('Transação excluída') },
    onError: (e: unknown) => {
      const msg = (e as { response?: { data?: { mensagem?: string } } })?.response?.data?.mensagem
      setDeleteModal(prev => prev ? { ...prev, erro: msg ?? 'Erro ao excluir.' } : prev)
    },
  })

  const cancelarMutation = useMutation({
    mutationFn: ({ id, scope }: { id: number; scope: EscopoExclusao }) => cancelarTransacao(id, scope),
    onSuccess: async () => { await invalidar(); setCancelModal(null); toast.success('Transação cancelada') },
    onError: (e: unknown) => {
      const msg = (e as { response?: { data?: { mensagem?: string } } })?.response?.data?.mensagem
      setCancelModal(prev => prev ? { ...prev, erro: msg ?? 'Erro ao cancelar.' } : prev)
    },
  })

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

  const ordenados = [...transacoes].sort((a, b) => b.data.localeCompare(a.data))
  const totalPaginas = Math.max(1, Math.ceil(ordenados.length / ITENS_POR_PAGINA))
  const paginados = ordenados.slice((pagina - 1) * ITENS_POR_PAGINA, pagina * ITENS_POR_PAGINA)

  const totalPendente = transacoes.filter(t => t.status === 'PENDENTE' || t.status === 'ATRASADA').reduce((s, t) => s + t.valor + (t.multa ?? 0), 0)
  const totalPago = transacoes.filter(t => t.status === 'PAGA').reduce((s, t) => s + t.valor + (t.multa ?? 0), 0)

  return (
    <div className="p-6 space-y-5">
      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-conteudo">Pagamentos</h1>
          <p className="text-sm text-conteudo-suave mt-0.5">Todas as despesas do período</p>
        </div>
        <div className="flex items-center gap-3">
          <SeletorMes />
          <button
            className="btn-primary text-xs py-1.5 px-3 flex items-center gap-1"
            onClick={() => { setEditing(undefined); setShowForm(true) }}>
            <Plus size={14} />
            Nova
          </button>
        </div>
      </div>

      {/* Resumo */}
      <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
        <div className="card p-4">
          <p className="text-xs text-conteudo-suave mb-1">Pendente / Atrasada</p>
          <p className="text-lg font-bold text-perigo">{fmt(totalPendente)}</p>
          <p className="text-xs text-conteudo-suave mt-1">{transacoes.filter(t => t.status === 'PENDENTE' || t.status === 'ATRASADA').length} lançamentos</p>
        </div>
        <div className="card p-4">
          <p className="text-xs text-conteudo-suave mb-1">Pago</p>
          <p className="text-lg font-bold text-sucesso">{fmt(totalPago)}</p>
          <p className="text-xs text-conteudo-suave mt-1">{transacoes.filter(t => t.status === 'PAGA').length} lançamentos</p>
        </div>
        <div className="card p-4 col-span-2 md:col-span-1">
          <p className="text-xs text-conteudo-suave mb-1">Total no período</p>
          <p className="text-lg font-bold text-conteudo">{fmt(totalPendente + totalPago)}</p>
          <p className="text-xs text-conteudo-suave mt-1">{transacoes.filter(t => t.status !== 'CANCELADA').length} lançamentos</p>
        </div>
      </div>

      {/* Filtros */}
      <div className="card p-3 flex flex-wrap gap-2 items-center">
        <button className={`${chipBase} ${filtroTipoPagamento === '' ? chipAtivo : chipInativo}`} onClick={() => setFiltroTipoPagamento('')}>Todos</button>
        <button className={`${chipBase} ${filtroTipoPagamento === 'DEBITO' ? chipAtivo : chipInativo}`} onClick={() => setFiltroTipoPagamento('DEBITO')}>Débito</button>
        <button className={`${chipBase} ${filtroTipoPagamento === 'CREDITO' ? chipAtivo : chipInativo}`} onClick={() => setFiltroTipoPagamento('CREDITO')}>Crédito</button>
        <div className="w-px h-5 bg-borda shrink-0" />
        <select
          className="text-xs px-3 py-1.5 rounded-full font-medium border border-borda bg-superficie text-conteudo-suave focus:border-acento focus:outline-none cursor-pointer"
          value={contaId ?? ''}
          onChange={e => definirContaId(e.target.value ? Number(e.target.value) : undefined)}>
          <option value="">Conta</option>
          {[...contas].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')).map(c => (
            <option key={c.id} value={c.id}>{c.nome}</option>
          ))}
        </select>
        <select
          className="text-xs px-3 py-1.5 rounded-full font-medium border border-borda bg-superficie text-conteudo-suave focus:border-acento focus:outline-none cursor-pointer"
          value={filtroCategoria}
          onChange={e => setFiltroCategoria(e.target.value ? Number(e.target.value) : '')}>
          <option value="">Categoria</option>
          {[...categorias].filter(c => c.tipo === 'DESPESA').sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')).map(c => (
            <option key={c.id} value={c.id}>{c.nome}</option>
          ))}
        </select>
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
        <select
          className="text-xs px-3 py-1.5 rounded-full font-medium border border-borda bg-superficie text-conteudo-suave focus:border-acento focus:outline-none cursor-pointer"
          value={filtroStatus}
          onChange={e => setFiltroStatus(e.target.value as StatusTransacao | '')}>
          <option value="">Status</option>
          <option value="PENDENTE">Pendente</option>
          <option value="ATRASADA">Atrasada</option>
          <option value="PAGA">Paga</option>
          <option value="CANCELADA">Cancelada</option>
        </select>
      </div>

      {/* Tabela / Lista */}
      {isLoading ? (
        <div className="flex justify-center py-12"><Spinner /></div>
      ) : transacoes.length === 0 ? (
        <div className="card text-center py-12 text-conteudo-suave">
          <p>Nenhuma despesa encontrada neste período.</p>
          <button onClick={() => setShowForm(true)} className="mt-3 text-acento hover:opacity-80 text-sm">
            + Adicionar lançamento
          </button>
        </div>
      ) : (
        <>
          <div className="hidden md:block card p-0 overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-sm border-collapse">
                <thead>
                  <tr className="border-b border-borda">
                    <th className="px-4 py-3 text-left text-xs font-semibold text-conteudo-suave uppercase tracking-wide w-[110px]">Data</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-conteudo-suave uppercase tracking-wide">Descrição</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-conteudo-suave uppercase tracking-wide">Categoria</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-conteudo-suave uppercase tracking-wide">Conta</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-conteudo-suave uppercase tracking-wide">Status</th>
                    <th className="px-4 py-3 text-right text-xs font-semibold text-conteudo-suave uppercase tracking-wide">Valor</th>
                    <th className="px-4 py-3 w-[80px]" />
                  </tr>
                </thead>
                <tbody>
                  {paginados.map(tx => (
                    <tr key={tx.id} className={`group border-b border-borda last:border-b-0 hover:bg-superficie-2 transition-colors ${tx.status === 'CANCELADA' ? 'opacity-60' : ''}`}>
                      <td className="px-4 py-3 text-xs text-conteudo-suave whitespace-nowrap">
                        {format(parseISO(tx.dataVencimento ?? tx.data), 'dd/MM/yyyy')}
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-1.5 font-medium text-conteudo">
                          {tx.descricao || tx.categoria?.nome || '—'}
                          {tx.origemRecorrenciaId && <Repeat size={11} className="text-acento shrink-0" aria-label="Recorrente" />}
                          {tx.totalParcelas && (
                            <span className="text-xs text-conteudo-suave flex items-center gap-0.5">
                              <Layers size={10} />{tx.numeroParcela}/{tx.totalParcelas}
                            </span>
                          )}
                        </div>
                        <div className="text-xs text-conteudo-suave mt-0.5">
                          {tx.conta.nome}{tx.tipoPagamento === 'CREDITO' ? ' · Crédito' : ''}
                        </div>
                      </td>
                      <td className="px-4 py-3">
                        {tx.categoria ? (
                          <div className="flex items-center gap-1.5">
                            <div className="w-2 h-2 rounded-full shrink-0" style={{ background: tx.categoria.cor }} />
                            <span className="text-xs text-conteudo whitespace-nowrap">{tx.categoria.nome}</span>
                          </div>
                        ) : <span className="text-xs text-conteudo-suave">—</span>}
                      </td>
                      <td className="px-4 py-3 text-sm text-conteudo-suave whitespace-nowrap">{tx.conta.nome}</td>
                      <td className="px-4 py-3">
                        <span className={`text-xs px-1.5 py-0.5 rounded-full font-medium ${corStatus[tx.status]}`}>
                          {rotuloStatus[tx.status]}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-right">
                        <span className="font-bold text-red-500 whitespace-nowrap">
                          -{fmt(tx.valor + (tx.multa ?? 0))}
                        </span>
                        {tx.multa != null && <div className="text-xs text-orange-500">+ multa {fmt(tx.multa)}</div>}
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex gap-0.5 justify-end opacity-0 group-hover:opacity-100 transition-opacity">
                          {(tx.status === 'PENDENTE' || tx.status === 'ATRASADA') && (
                            <button onClick={() => setPagarModal({ id: tx.id, status: tx.status })} title="Marcar como paga"
                              className="p-1.5 rounded-lg hover:bg-emerald-900/40 text-conteudo-suave hover:text-emerald-500 transition-colors">
                              <CheckCircle2 size={14} />
                            </button>
                          )}
                          {tx.status === 'PAGA' && (
                            <button onClick={() => estornarMutation.mutate(tx.id)} title="Estornar pagamento"
                              className="p-1.5 rounded-lg hover:bg-amber-900/40 text-conteudo-suave hover:text-amber-500 transition-colors">
                              <Undo2 size={14} />
                            </button>
                          )}
                          {tx.status !== 'CANCELADA' && (
                            <button onClick={() => abrirCancelModal(tx)} title="Cancelar"
                              className="p-1.5 rounded-lg hover:bg-orange-900/40 text-conteudo-suave hover:text-orange-500 transition-colors">
                              <Ban size={14} />
                            </button>
                          )}
                          <button
                            onClick={() => { setEditing({ origem: 'TRANSACAO', tx }); setShowForm(true) }}
                            disabled={tx.status === 'CANCELADA'}
                            title="Editar"
                            className="p-1.5 rounded-lg hover:bg-superficie-2 text-conteudo-suave hover:text-conteudo transition-colors disabled:opacity-30 disabled:cursor-not-allowed">
                            <Pencil size={14} />
                          </button>
                          {tx.status === 'CANCELADA' && (
                            <button onClick={() => abrirDeleteModal(tx)} title="Excluir"
                              className="p-1.5 rounded-lg hover:bg-red-900/40 text-conteudo-suave hover:text-red-500 transition-colors">
                              <Trash2 size={14} />
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {totalPaginas > 1 && (
              <Paginacao pagina={pagina} totalPaginas={totalPaginas} total={transacoes.length} setPagina={setPagina} />
            )}
          </div>

          {/* Mobile */}
          <div className="md:hidden space-y-2">
            {paginados.map(tx => (
              <div key={tx.id} className={`card flex items-center gap-3 px-4 py-3 ${tx.status === 'CANCELADA' ? 'opacity-60' : ''}`}>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-1.5 font-medium text-conteudo text-sm truncate">
                    {tx.descricao || tx.categoria?.nome || '—'}
                    {tx.origemRecorrenciaId && <Repeat size={11} className="text-acento shrink-0" />}
                  </div>
                  <div className="text-xs text-conteudo-suave mt-0.5">
                    {format(parseISO(tx.data), 'dd/MM')} · {tx.conta.nome}
                  </div>
                </div>
                <div className="text-right shrink-0 space-y-1">
                  <div className="font-bold text-red-500 text-sm">-{fmt(tx.valor)}</div>
                  <span className={`text-xs px-1.5 py-0.5 rounded-full font-medium ${corStatus[tx.status]}`}>
                    {rotuloStatus[tx.status]}
                  </span>
                </div>
                <div className="flex flex-col gap-1 shrink-0">
                  {(tx.status === 'PENDENTE' || tx.status === 'ATRASADA') && (
                    <button onClick={() => setPagarModal({ id: tx.id, status: tx.status })}
                      className="p-1.5 rounded-lg text-conteudo-suave hover:text-emerald-500 transition-colors">
                      <CheckCircle2 size={16} />
                    </button>
                  )}
                  {tx.status === 'PAGA' && (
                    <button onClick={() => estornarMutation.mutate(tx.id)}
                      className="p-1.5 rounded-lg text-conteudo-suave hover:text-amber-500 transition-colors">
                      <Undo2 size={16} />
                    </button>
                  )}
                  <button onClick={() => { setEditing({ origem: 'TRANSACAO', tx }); setShowForm(true) }}
                    disabled={tx.status === 'CANCELADA'}
                    className="p-1.5 rounded-lg text-conteudo-suave hover:text-conteudo transition-colors disabled:opacity-30">
                    <Pencil size={16} />
                  </button>
                </div>
              </div>
            ))}
            {totalPaginas > 1 && (
              <Paginacao pagina={pagina} totalPaginas={totalPaginas} total={transacoes.length} setPagina={setPagina} />
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

      {pagarModal && (
        <PagarModal
          status={pagarModal.status}
          aoFechar={() => setPagarModal(null)}
          aoConfirmar={(dataPagamento, multa) => pagarMutation.mutate({ id: pagarModal.id, dataPagamento, multa })}
          carregando={pagarMutation.isPending}
        />
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
                {deleteModal.tx.grupoParcelaId ? 'Este lançamento faz parte de um parcelamento.' : 'Tem certeza que deseja excluir?'}
              </p>
              {deleteModal.carregando && <p className="text-sm text-conteudo-suave animate-pulse">Verificando impacto...</p>}
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
                <div className="rounded-lg border border-red-800/50 bg-red-900/20 p-3 text-sm text-red-400">{deleteModal.erro}</div>
              )}
              <div className="space-y-2">
                {!deleteModal.impacto?.bloqueado && (
                  <>
                    <button onClick={() => deleteMutation.mutate({ id: deleteModal.tx.id, scope: 'UNICA' })} className="w-full btn-danger">
                      Excluir só este
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
                  ? 'As duas pontas da transferência ficam canceladas e o saldo é revertido.'
                  : 'O lançamento fica marcado como cancelado (saldo revertido se já pago), mas continua no histórico.'}
              </p>
              {cancelModal.carregando && <p className="text-sm text-conteudo-suave animate-pulse">Verificando impacto...</p>}
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
                <div className="rounded-lg border border-red-800/50 bg-red-900/20 p-3 text-sm text-red-400">{cancelModal.erro}</div>
              )}
              <div className="space-y-2">
                {!cancelModal.impacto?.bloqueado && (
                  <>
                    <button onClick={() => cancelarMutation.mutate({ id: cancelModal.tx.id, scope: 'UNICA' })}
                      className="w-full py-2.5 rounded-lg border border-orange-800 text-orange-500 hover:bg-orange-900/20 transition-colors text-sm font-medium">
                      Cancelar só este
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
                  </>
                )}
                <button onClick={() => setCancelModal(null)}
                  className="w-full py-2.5 rounded-lg border border-borda text-conteudo-suave hover:text-conteudo transition-colors text-sm font-medium">
                  {cancelModal.impacto?.bloqueado ? 'Voltar' : 'Fechar'}
                </button>
              </div>
            </div>
          </div>
        </SobreposicaoModal>
      )}
    </div>
  )
}

function Paginacao({ pagina, totalPaginas, total, setPagina }: {
  pagina: number; totalPaginas: number; total: number; setPagina: React.Dispatch<React.SetStateAction<number>>
}) {
  return (
    <div className="border-t border-borda px-4 py-3 flex items-center justify-between">
      <span className="text-xs text-conteudo-suave">{total} lançamentos · Página {pagina} de {totalPaginas}</span>
      <div className="flex items-center gap-1">
        <button onClick={() => setPagina(p => Math.max(1, p - 1))} disabled={pagina === 1}
          className="w-8 h-8 flex items-center justify-center rounded-lg border border-borda text-conteudo-suave hover:border-acento hover:text-acento disabled:opacity-40 transition-colors">
          <ChevronLeft size={13} />
        </button>
        <button onClick={() => setPagina(p => Math.min(totalPaginas, p + 1))} disabled={pagina === totalPaginas}
          className="w-8 h-8 flex items-center justify-center rounded-lg border border-borda text-conteudo-suave hover:border-acento hover:text-acento disabled:opacity-40 transition-colors">
          <ChevronRight size={13} />
        </button>
      </div>
    </div>
  )
}

function PagarModal({ status, aoFechar, aoConfirmar, carregando }: {
  status: StatusTransacao
  aoFechar: () => void
  aoConfirmar: (dataPagamento: string, multa?: number) => void
  carregando: boolean
}) {
  const hoje = format(new Date(), 'yyyy-MM-dd')
  const [dataPagamento, setDataPagamento] = useState(hoje)
  const [multa, setMulta] = useState('')

  return (
    <SobreposicaoModal aoFechar={aoFechar}>
      <div className="cartao-modal max-w-sm">
        <div className="cartao-modal-cabecalho">
          <h3 className="text-base font-semibold text-conteudo">Marcar como paga</h3>
        </div>
        <form onSubmit={e => { e.preventDefault(); aoConfirmar(dataPagamento, multa ? Number(multa) : undefined) }}
          className="cartao-modal-corpo">
          <div>
            <label className="label">Data de pagamento</label>
            <input className="input" type="date" max={hoje}
              value={dataPagamento} onChange={e => setDataPagamento(e.target.value)} required />
          </div>
          <div>
            <label className="label">Multa por atraso (R$, opcional)</label>
            <input className="input" type="number" step="0.01" min="0" placeholder="0,00"
              value={multa} onChange={e => setMulta(e.target.value)} />
            {status === 'ATRASADA' && (
              <p className="text-xs text-orange-500 mt-1">
                Este lançamento está atrasado — a multa é somada ao valor debitado da conta.
              </p>
            )}
          </div>
          <div className="flex gap-3 pt-2">
            <button type="button" onClick={aoFechar}
              className="flex-1 py-2.5 rounded-lg border border-borda text-conteudo-suave hover:text-conteudo transition-colors text-sm font-medium">
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
