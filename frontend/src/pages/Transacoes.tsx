import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, Trash2, Pencil, CreditCard, Banknote, Repeat, Layers } from 'lucide-react'
import { format, parseISO } from 'date-fns'
import { ptBR } from 'date-fns/locale'
import { buscarTransacoes, excluirTransacao, type EscopoExclusao } from '../api/transacoes'
import { buscarCategorias } from '../api/categorias'
import { useLojaFiltro } from '../store/lojaFiltro'
import SeletorMes from '../components/SeletorMes'
import FormularioTransacao from '../components/forms/FormularioTransacao'
import type { Transacao, TipoTransacao } from '../types'

const fmt = (v: number) =>
  new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(v)

const agruparPorData = (txs: Transacao[]) => {
  const map = new Map<string, Transacao[]>()
  txs.forEach(t => {
    const key = t.data
    if (!map.has(key)) map.set(key, [])
    map.get(key)!.push(t)
  })
  return [...map.entries()].sort((a, b) => b[0].localeCompare(a[0]))
}

export default function Transacoes() {
  const qc = useQueryClient()
  const { mes, contaId } = useLojaFiltro()
  const [filtroTipo, setFiltroTipo] = useState<TipoTransacao | ''>('')
  const [filtroCategoria, setFiltroCategoria] = useState<number | ''>('')
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<Transacao | undefined>()
  const [deleteModal, setDeleteModal] = useState<{ tx: Transacao } | null>(null)

  const { data: transacoes = [], isLoading } = useQuery({
    queryKey: ['transacoes', mes, contaId, filtroTipo, filtroCategoria],
    queryFn: () => buscarTransacoes({
      month: mes,
      contaId,
      tipo: filtroTipo || undefined,
      categoriaId: filtroCategoria || undefined,
    }),
  })

  const { data: categorias = [] } = useQuery({ queryKey: ['categorias'], queryFn: () => buscarCategorias() })

  const deleteMutation = useMutation({
    mutationFn: ({ id, scope }: { id: number; scope: EscopoExclusao }) => excluirTransacao(id, scope),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['transacoes'] })
      qc.invalidateQueries({ queryKey: ['painel'] })
      qc.invalidateQueries({ queryKey: ['contas'] })
      setDeleteModal(null)
    },
  })

  const agrupadas = agruparPorData(transacoes)

  const hoje = new Date().toISOString().slice(0, 10)

  const totalReceitas = transacoes.filter(t => t.tipo === 'RECEITA').reduce((s, t) => s + t.valor, 0)
  const totalDespesas = transacoes.filter(t => t.tipo === 'DESPESA').reduce((s, t) => s + t.valor, 0)

  const realizadas = transacoes.filter(t => t.data <= hoje)
  const pendentes = transacoes.filter(t => t.data > hoje)

  const receitasRealizadas = realizadas.filter(t => t.tipo === 'RECEITA').reduce((s, t) => s + t.valor, 0)
  const despesasRealizadas = realizadas.filter(t => t.tipo === 'DESPESA').reduce((s, t) => s + t.valor, 0)
  const receitasPendentes = pendentes.filter(t => t.tipo === 'RECEITA').reduce((s, t) => s + t.valor, 0)
  const despesasPendentes = pendentes.filter(t => t.tipo === 'DESPESA').reduce((s, t) => s + t.valor, 0)

  return (
    <div className="p-6 space-y-5">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-white">Lançamentos</h1>
        <div className="flex items-center gap-3">
          <SeletorMes />
          <button onClick={() => { setEditing(undefined); setShowForm(true) }} className="btn-primary flex items-center gap-2">
            <Plus size={16} /> Novo
          </button>
        </div>
      </div>

      {/* Barra de resumo */}
      <div className="space-y-2">
        <div className="grid grid-cols-3 gap-4">
          <div className="card text-center">
            <p className="text-xs text-gray-500 mb-1">Receitas</p>
            <p className="text-lg font-bold text-emerald-400">{fmt(totalReceitas)}</p>
          </div>
          <div className="card text-center">
            <p className="text-xs text-gray-500 mb-1">Despesas</p>
            <p className="text-lg font-bold text-red-400">{fmt(totalDespesas)}</p>
          </div>
          <div className="card text-center">
            <p className="text-xs text-gray-500 mb-1">Saldo</p>
            <p className={`text-lg font-bold ${totalReceitas - totalDespesas >= 0 ? 'text-indigo-400' : 'text-orange-400'}`}>
              {fmt(totalReceitas - totalDespesas)}
            </p>
          </div>
        </div>

        <div className="grid grid-cols-3 gap-4">
          <div className="card text-center border border-emerald-400/20">
            <p className="text-xs text-gray-500 mb-1">Receitas recebidas</p>
            <p className="text-base font-bold text-emerald-400">{fmt(receitasRealizadas)}</p>
          </div>
          <div className="card text-center border border-emerald-400/20">
            <p className="text-xs text-gray-500 mb-1">Despesas pagas</p>
            <p className="text-base font-bold text-red-400">{fmt(despesasRealizadas)}</p>
          </div>
          <div className="card text-center border border-emerald-400/20">
            <p className="text-xs text-gray-500 mb-1">Saldo atual</p>
            <p className={`text-base font-bold ${receitasRealizadas - despesasRealizadas >= 0 ? 'text-indigo-400' : 'text-orange-400'}`}>
              {fmt(receitasRealizadas - despesasRealizadas)}
            </p>
          </div>
        </div>

        <div className="grid grid-cols-3 gap-4">
          <div className="card text-center border border-amber-400/20">
            <p className="text-xs text-gray-500 mb-1">Receitas a receber</p>
            <p className="text-base font-bold text-emerald-400">{fmt(receitasPendentes)}</p>
          </div>
          <div className="card text-center border border-amber-400/20">
            <p className="text-xs text-gray-500 mb-1">Despesas a pagar</p>
            <p className="text-base font-bold text-red-400">{fmt(despesasPendentes)}</p>
          </div>
          <div className="card text-center border border-amber-400/20">
            <p className="text-xs text-gray-500 mb-1">Saldo futuro</p>
            <p className={`text-base font-bold ${receitasPendentes - despesasPendentes >= 0 ? 'text-indigo-400' : 'text-orange-400'}`}>
              {fmt(receitasPendentes - despesasPendentes)}
            </p>
          </div>
        </div>
      </div>

      {/* Filtros */}
      <div className="flex gap-3">
        <select className="select w-40" value={filtroTipo} onChange={e => setFiltroTipo(e.target.value as any)}>
          <option value="">Todos</option>
          <option value="RECEITA">Receitas</option>
          <option value="DESPESA">Despesas</option>
        </select>
        <select className="select w-48" value={filtroCategoria} onChange={e => setFiltroCategoria(e.target.value ? Number(e.target.value) : '')}>
          <option value="">Todas categorias</option>
          {categorias.map(c => <option key={c.id} value={c.id}>{c.nome}</option>)}
        </select>
      </div>

      {/* Lista de lançamentos */}
      {isLoading ? (
        <div className="flex justify-center py-12">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-500" />
        </div>
      ) : agrupadas.length === 0 ? (
        <div className="card text-center py-12 text-gray-600">
          <p>Nenhum lançamento encontrado.</p>
          <button onClick={() => setShowForm(true)} className="mt-3 text-indigo-400 hover:text-indigo-300 text-sm">
            + Adicionar o primeiro
          </button>
        </div>
      ) : (
        <div className="space-y-4">
          {agrupadas.map(([data, txs]) => (
            <div key={data}>
              <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">
                {format(parseISO(data), "EEEE, dd 'de' MMMM", { locale: ptBR })}
              </p>
              <div className="card p-0 overflow-hidden divide-y divide-gray-800">
                {txs.map(tx => (
                  <div key={tx.id} className="flex items-center gap-4 px-5 py-3.5 hover:bg-gray-800/50 transition-colors group">
                    {/* Bolinha de cor */}
                    <div className="w-3 h-3 rounded-full shrink-0"
                      style={{ background: tx.categoria?.cor ?? '#6b7280' }} />

                    {/* Info */}
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-medium text-gray-200 truncate">
                          {tx.descricao || tx.categoria?.nome || '—'}
                        </span>
                        {tx.fixa && <Repeat size={12} className="text-indigo-400 shrink-0" aria-label="Fixa" />}
                        {tx.totalParcelas && (
                          <span className="text-xs text-gray-500 flex items-center gap-0.5">
                            <Layers size={11} />
                            {tx.numeroParcela}/{tx.totalParcelas}
                          </span>
                        )}
                      </div>
                      <div className="flex items-center gap-2 mt-0.5">
                        <span className="text-xs text-gray-500">{tx.conta.nome}</span>
                        {tx.categoria && (
                          <span className="text-xs px-1.5 py-0.5 rounded-full" style={{
                            background: `${tx.categoria.cor}20`, color: tx.categoria.cor
                          }}>
                            {tx.categoria.nome}
                          </span>
                        )}
                        {tx.tipoPagamento === 'CREDITO' ? (
                          <CreditCard size={11} className="text-gray-600" />
                        ) : (
                          <Banknote size={11} className="text-gray-600" />
                        )}
                      </div>
                    </div>

                    {/* Valor */}
                    <span className={`text-base font-bold ${tx.tipo === 'RECEITA' ? 'text-emerald-400' : 'text-red-400'}`}>
                      {tx.tipo === 'RECEITA' ? '+' : '-'}{fmt(tx.valor)}
                    </span>

                    {/* Ações */}
                    <div className="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                      <button onClick={() => { setEditing(tx); setShowForm(true) }}
                        className="p-1.5 rounded-lg hover:bg-gray-700 text-gray-400 hover:text-white transition-colors">
                        <Pencil size={14} />
                      </button>
                      <button onClick={() => setDeleteModal({ tx })}
                        className="p-1.5 rounded-lg hover:bg-red-900/40 text-gray-400 hover:text-red-400 transition-colors">
                        <Trash2 size={14} />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}

      {showForm && (
        <FormularioTransacao
          onClose={() => { setShowForm(false); setEditing(undefined) }}
          editing={editing}
        />
      )}

      {/* Modal de exclusão */}
      {deleteModal && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 p-4">
          <div className="card w-full max-w-sm">
            <h3 className="text-base font-semibold text-white mb-2">Excluir lançamento</h3>
            <p className="text-sm text-gray-400 mb-5">
              {deleteModal.tx.grupoParcelaId
                ? 'Este lançamento faz parte de um parcelamento.'
                : deleteModal.tx.fixa
                  ? 'Este é um lançamento fixo.'
                  : 'Tem certeza que deseja excluir?'}
            </p>
            <div className="space-y-2">
              <button onClick={() => deleteMutation.mutate({ id: deleteModal.tx.id, scope: 'UNICA' })}
                className="w-full btn-danger">
                Excluir só este
              </button>
              {deleteModal.tx.grupoParcelaId && (
                <button onClick={() => deleteMutation.mutate({ id: deleteModal.tx.id, scope: 'FUTURAS' })}
                  className="w-full py-2.5 rounded-lg border border-red-800 text-red-400 hover:bg-red-900/20 transition-colors text-sm font-medium">
                  Excluir este e os próximos
                </button>
              )}
              {deleteModal.tx.grupoParcelaId && (
                <button onClick={() => deleteMutation.mutate({ id: deleteModal.tx.id, scope: 'GRUPO' })}
                  className="w-full py-2.5 rounded-lg border border-red-800 text-red-400 hover:bg-red-900/20 transition-colors text-sm font-medium">
                  Excluir todas as parcelas
                </button>
              )}
              {deleteModal.tx.fixa && (
                <button onClick={() => deleteMutation.mutate({ id: deleteModal.tx.id, scope: 'FUTURAS' })}
                  className="w-full py-2.5 rounded-lg border border-red-800 text-red-400 hover:bg-red-900/20 transition-colors text-sm font-medium">
                  Excluir este e os próximos meses
                </button>
              )}
              <button onClick={() => setDeleteModal(null)}
                className="w-full py-2.5 rounded-lg border border-gray-700 text-gray-400 hover:text-white transition-colors text-sm font-medium">
                Cancelar
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
