import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, Trash2, Pencil, CreditCard, Banknote, Repeat, Layers } from 'lucide-react'
import { format, parseISO } from 'date-fns'
import { ptBR } from 'date-fns/locale'
import { getTransactions, deleteTransaction, type DeleteScope } from '../api/transactions'
import { getCategories } from '../api/categories'
import { useFilterStore } from '../store/filterStore'
import MonthPicker from '../components/MonthPicker'
import TransactionForm from '../components/forms/TransactionForm'
import type { Transaction, TransactionType } from '../types'

const fmt = (v: number) =>
  new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(v)

const groupByDate = (txs: Transaction[]) => {
  const map = new Map<string, Transaction[]>()
  txs.forEach(t => {
    const key = t.date
    if (!map.has(key)) map.set(key, [])
    map.get(key)!.push(t)
  })
  return [...map.entries()].sort((a, b) => b[0].localeCompare(a[0]))
}

export default function Transactions() {
  const qc = useQueryClient()
  const { month, accountId } = useFilterStore()
  const [typeFilter, setTypeFilter] = useState<TransactionType | ''>('')
  const [categoryFilter, setCategoryFilter] = useState<number | ''>('')
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<Transaction | undefined>()
  const [deleteModal, setDeleteModal] = useState<{ tx: Transaction } | null>(null)

  const { data: transactions = [], isLoading } = useQuery({
    queryKey: ['transactions', month, accountId, typeFilter, categoryFilter],
    queryFn: () => getTransactions({
      month,
      accountId,
      type: typeFilter || undefined,
      categoryId: categoryFilter || undefined,
    }),
  })

  const { data: categories = [] } = useQuery({ queryKey: ['categories'], queryFn: () => getCategories() })

  const deleteMutation = useMutation({
    mutationFn: ({ id, scope }: { id: number; scope: DeleteScope }) => deleteTransaction(id, scope),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['transactions'] })
      qc.invalidateQueries({ queryKey: ['dashboard'] })
      qc.invalidateQueries({ queryKey: ['accounts'] })
      setDeleteModal(null)
    },
  })

  const grouped = groupByDate(transactions)

  const totalIncome = transactions.filter(t => t.type === 'INCOME').reduce((s, t) => s + t.amount, 0)
  const totalExpense = transactions.filter(t => t.type === 'EXPENSE').reduce((s, t) => s + t.amount, 0)

  return (
    <div className="p-6 space-y-5">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-white">Lançamentos</h1>
        <div className="flex items-center gap-3">
          <MonthPicker />
          <button onClick={() => { setEditing(undefined); setShowForm(true) }} className="btn-primary flex items-center gap-2">
            <Plus size={16} /> Novo
          </button>
        </div>
      </div>

      {/* Summary bar */}
      <div className="grid grid-cols-3 gap-4">
        <div className="card text-center">
          <p className="text-xs text-gray-500 mb-1">Receitas</p>
          <p className="text-lg font-bold text-emerald-400">{fmt(totalIncome)}</p>
        </div>
        <div className="card text-center">
          <p className="text-xs text-gray-500 mb-1">Despesas</p>
          <p className="text-lg font-bold text-red-400">{fmt(totalExpense)}</p>
        </div>
        <div className="card text-center">
          <p className="text-xs text-gray-500 mb-1">Saldo</p>
          <p className={`text-lg font-bold ${totalIncome - totalExpense >= 0 ? 'text-indigo-400' : 'text-orange-400'}`}>
            {fmt(totalIncome - totalExpense)}
          </p>
        </div>
      </div>

      {/* Filters */}
      <div className="flex gap-3">
        <select className="select w-40" value={typeFilter} onChange={e => setTypeFilter(e.target.value as any)}>
          <option value="">Todos</option>
          <option value="INCOME">Receitas</option>
          <option value="EXPENSE">Despesas</option>
        </select>
        <select className="select w-48" value={categoryFilter} onChange={e => setCategoryFilter(e.target.value ? Number(e.target.value) : '')}>
          <option value="">Todas categorias</option>
          {categories.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
        </select>
      </div>

      {/* Transaction list */}
      {isLoading ? (
        <div className="flex justify-center py-12">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-500" />
        </div>
      ) : grouped.length === 0 ? (
        <div className="card text-center py-12 text-gray-600">
          <p>Nenhum lançamento encontrado.</p>
          <button onClick={() => setShowForm(true)} className="mt-3 text-indigo-400 hover:text-indigo-300 text-sm">
            + Adicionar o primeiro
          </button>
        </div>
      ) : (
        <div className="space-y-4">
          {grouped.map(([date, txs]) => (
            <div key={date}>
              <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">
                {format(parseISO(date), "EEEE, dd 'de' MMMM", { locale: ptBR })}
              </p>
              <div className="card p-0 overflow-hidden divide-y divide-gray-800">
                {txs.map(tx => (
                  <div key={tx.id} className="flex items-center gap-4 px-5 py-3.5 hover:bg-gray-800/50 transition-colors group">
                    {/* Color dot */}
                    <div className="w-3 h-3 rounded-full shrink-0"
                      style={{ background: tx.category?.color ?? '#6b7280' }} />

                    {/* Info */}
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-medium text-gray-200 truncate">
                          {tx.description || tx.category?.name || '—'}
                        </span>
                        {tx.fixed && <Repeat size={12} className="text-indigo-400 shrink-0" aria-label="Fixa" />}
                        {tx.installmentTotal && (
                          <span className="text-xs text-gray-500 flex items-center gap-0.5">
                            <Layers size={11} />
                            {tx.installmentNumber}/{tx.installmentTotal}
                          </span>
                        )}
                      </div>
                      <div className="flex items-center gap-2 mt-0.5">
                        <span className="text-xs text-gray-500">{tx.account.name}</span>
                        {tx.category && (
                          <span className="text-xs px-1.5 py-0.5 rounded-full" style={{
                            background: `${tx.category.color}20`, color: tx.category.color
                          }}>
                            {tx.category.name}
                          </span>
                        )}
                        {tx.paymentType === 'CREDIT' ? (
                          <CreditCard size={11} className="text-gray-600" />
                        ) : (
                          <Banknote size={11} className="text-gray-600" />
                        )}
                      </div>
                    </div>

                    {/* Amount */}
                    <span className={`text-base font-bold ${tx.type === 'INCOME' ? 'text-emerald-400' : 'text-red-400'}`}>
                      {tx.type === 'INCOME' ? '+' : '-'}{fmt(tx.amount)}
                    </span>

                    {/* Actions */}
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
        <TransactionForm
          onClose={() => { setShowForm(false); setEditing(undefined) }}
          editing={editing}
        />
      )}

      {/* Delete Modal */}
      {deleteModal && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 p-4">
          <div className="card w-full max-w-sm">
            <h3 className="text-base font-semibold text-white mb-2">Excluir lançamento</h3>
            <p className="text-sm text-gray-400 mb-5">
              {deleteModal.tx.installmentGroupId
                ? 'Este lançamento faz parte de um parcelamento.'
                : deleteModal.tx.fixed
                  ? 'Este é um lançamento fixo.'
                  : 'Tem certeza que deseja excluir?'}
            </p>
            <div className="space-y-2">
              <button onClick={() => deleteMutation.mutate({ id: deleteModal.tx.id, scope: 'SINGLE' })}
                className="w-full btn-danger">
                Excluir só este
              </button>
              {deleteModal.tx.installmentGroupId && (
                <button onClick={() => deleteMutation.mutate({ id: deleteModal.tx.id, scope: 'FUTURE' })}
                  className="w-full py-2.5 rounded-lg border border-red-800 text-red-400 hover:bg-red-900/20 transition-colors text-sm font-medium">
                  Excluir este e os próximos
                </button>
              )}
              {deleteModal.tx.installmentGroupId && (
                <button onClick={() => deleteMutation.mutate({ id: deleteModal.tx.id, scope: 'GROUP' })}
                  className="w-full py-2.5 rounded-lg border border-red-800 text-red-400 hover:bg-red-900/20 transition-colors text-sm font-medium">
                  Excluir todas as parcelas
                </button>
              )}
              {deleteModal.tx.fixed && (
                <button onClick={() => deleteMutation.mutate({ id: deleteModal.tx.id, scope: 'FUTURE' })}
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
