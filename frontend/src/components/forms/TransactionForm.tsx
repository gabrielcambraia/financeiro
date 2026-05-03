import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { X } from 'lucide-react'
import { format } from 'date-fns'
import { getAccounts } from '../../api/accounts'
import { getCategories } from '../../api/categories'
import { createTransaction, updateTransaction } from '../../api/transactions'
import type { Transaction, TransactionType, PaymentType } from '../../types'

interface Props {
  onClose: () => void
  editing?: Transaction
}

export default function TransactionForm({ onClose, editing }: Props) {
  const qc = useQueryClient()
  const [type, setType] = useState<TransactionType>(editing?.type ?? 'EXPENSE')
  const [form, setForm] = useState({
    accountId: editing?.accountId ?? '',
    categoryId: editing?.categoryId ?? '',
    paymentType: (editing?.paymentType ?? 'DEBIT') as PaymentType,
    amount: editing?.amount ?? '',
    description: editing?.description ?? '',
    date: editing?.date ?? format(new Date(), 'yyyy-MM-dd'),
    fixed: editing?.fixed ?? false,
    installmentTotal: editing?.installmentTotal ?? '',
  })

  const { data: accounts = [] } = useQuery({ queryKey: ['accounts'], queryFn: getAccounts })
  const { data: categories = [] } = useQuery({
    queryKey: ['categories', type],
    queryFn: () => getCategories(type),
  })

  const mutation = useMutation({
    mutationFn: async (payload: Parameters<typeof createTransaction>[0]) => {
      if (editing) { await updateTransaction(editing.id, payload); return [] }
      return createTransaction(payload)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['transactions'] })
      qc.invalidateQueries({ queryKey: ['dashboard'] })
      qc.invalidateQueries({ queryKey: ['accounts'] })
      onClose()
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    mutation.mutate({
      accountId: Number(form.accountId),
      categoryId: form.categoryId ? Number(form.categoryId) : undefined,
      type,
      paymentType: form.paymentType,
      amount: Number(form.amount),
      description: form.description || undefined,
      date: form.date,
      fixed: form.fixed,
      installmentTotal: form.installmentTotal ? Number(form.installmentTotal) : undefined,
    })
  }

  const set = (k: string, v: unknown) => setForm(f => ({ ...f, [k]: v }))

  return (
    <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 p-4">
      <div className="card w-full max-w-lg">
        <div className="flex items-center justify-between mb-5">
          <h2 className="text-lg font-semibold text-white">
            {editing ? 'Editar Lançamento' : 'Novo Lançamento'}
          </h2>
          <button onClick={onClose} className="btn-ghost p-1.5"><X size={18} /></button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          {/* Tipo */}
          <div className="flex rounded-xl overflow-hidden border border-gray-700">
            {(['EXPENSE', 'INCOME'] as TransactionType[]).map(t => (
              <button
                key={t}
                type="button"
                onClick={() => { setType(t); set('categoryId', '') }}
                className={`flex-1 py-2.5 text-sm font-medium transition-colors
                  ${type === t
                    ? t === 'EXPENSE' ? 'bg-red-600 text-white' : 'bg-emerald-600 text-white'
                    : 'text-gray-400 hover:text-white'}`}
              >
                {t === 'EXPENSE' ? 'Despesa' : 'Receita'}
              </button>
            ))}
          </div>

          {/* Conta e Categoria */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="label">Conta</label>
              <select className="select" value={form.accountId} onChange={e => set('accountId', e.target.value)} required>
                <option value="">Selecione...</option>
                {accounts.map(a => <option key={a.id} value={a.id}>{a.name}</option>)}
              </select>
            </div>
            <div>
              <label className="label">Categoria</label>
              <select className="select" value={form.categoryId} onChange={e => set('categoryId', e.target.value)}>
                <option value="">Sem categoria</option>
                {categories.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            </div>
          </div>

          {/* Valor e Data */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="label">Valor (R$)</label>
              <input
                className="input" type="number" step="0.01" min="0.01" placeholder="0,00"
                value={form.amount} onChange={e => set('amount', e.target.value)} required
              />
            </div>
            <div>
              <label className="label">Data</label>
              <input
                className="input" type="date"
                value={form.date} onChange={e => set('date', e.target.value)} required
              />
            </div>
          </div>

          {/* Descrição */}
          <div>
            <label className="label">Descrição</label>
            <input
              className="input" placeholder="Ex: Mercado, Aluguel..."
              value={form.description} onChange={e => set('description', e.target.value)}
            />
          </div>

          {/* Débito / Crédito */}
          <div>
            <label className="label">Forma de pagamento</label>
            <div className="flex gap-2">
              {(['DEBIT', 'CREDIT'] as PaymentType[]).map(p => (
                <button
                  key={p} type="button"
                  onClick={() => set('paymentType', p)}
                  className={`flex-1 py-2 text-sm rounded-lg border transition-colors
                    ${form.paymentType === p
                      ? 'border-indigo-500 bg-indigo-600/20 text-indigo-300'
                      : 'border-gray-700 text-gray-400 hover:border-gray-600'}`}
                >
                  {p === 'DEBIT' ? 'Débito' : 'Crédito'}
                </button>
              ))}
            </div>
          </div>

          {/* Fixa / Parcelada — só para despesas ou sem parcelamento para receitas */}
          {!editing && (
            <div className="space-y-3">
              <div className="flex items-center gap-3 p-3 rounded-xl bg-gray-800">
                <input
                  id="fixed" type="checkbox"
                  checked={form.fixed}
                  onChange={e => { set('fixed', e.target.checked); if (e.target.checked) set('installmentTotal', '') }}
                  className="w-4 h-4 accent-indigo-500"
                />
                <label htmlFor="fixed" className="text-sm text-gray-300">
                  Repetir todo mês (fixa)
                </label>
              </div>

              {!form.fixed && (
                <div>
                  <label className="label">Parcelar em quantas vezes? (deixe em branco = à vista)</label>
                  <input
                    className="input" type="number" min="2" max="60" placeholder="Ex: 3"
                    value={form.installmentTotal}
                    onChange={e => set('installmentTotal', e.target.value)}
                  />
                </div>
              )}
            </div>
          )}

          <div className="flex gap-3 pt-2">
            <button type="button" onClick={onClose} className="flex-1 py-2.5 rounded-lg border border-gray-700 text-gray-400 hover:text-white hover:border-gray-600 transition-colors text-sm font-medium">
              Cancelar
            </button>
            <button type="submit" disabled={mutation.isPending} className="flex-1 btn-primary">
              {mutation.isPending ? 'Salvando...' : 'Salvar'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
