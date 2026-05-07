import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, Pencil, Trash2, Wallet, PiggyBank, CreditCard, TrendingUp } from 'lucide-react'
import { getAccounts, createAccount, updateAccount, deleteAccount } from '../api/accounts'
import type { Account, AccountType } from '../types'

const fmt = (v: number) =>
  new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(v)

const ACCOUNT_TYPES: { value: AccountType; label: string; icon: typeof Wallet }[] = [
  { value: 'CHECKING', label: 'Conta Corrente', icon: Wallet },
  { value: 'SAVINGS', label: 'Poupança', icon: PiggyBank },
  { value: 'WALLET', label: 'Carteira', icon: CreditCard },
  { value: 'INVESTMENT', label: 'Investimentos', icon: TrendingUp },
]

const COLORS = ['#6366f1', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#06b6d4', '#ec4899', '#84cc16']

const defaultForm = { name: '', type: 'CHECKING' as AccountType, balance: '', color: COLORS[0], icon: 'wallet' }

export default function Accounts() {
  const qc = useQueryClient()
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<Account | null>(null)
  const [form, setForm] = useState(defaultForm)

  const { data: accounts = [] } = useQuery({ queryKey: ['accounts'], queryFn: getAccounts })

  const saveMutation = useMutation({
    mutationFn: (data: Omit<Account, 'id'>) =>
      editing ? updateAccount(editing.id, data) : createAccount(data),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['accounts'] }); closeForm() },
  })

  const deleteMutation = useMutation({
    mutationFn: deleteAccount,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['accounts'] }),
  })

  const openCreate = () => { setEditing(null); setForm(defaultForm); setShowForm(true) }
  const openEdit = (a: Account) => {
    setEditing(a)
    setForm({ name: a.name, type: a.type, balance: String(a.balance), color: a.color, icon: a.icon })
    setShowForm(true)
  }
  const closeForm = () => { setShowForm(false); setEditing(null) }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    saveMutation.mutate({ name: form.name, type: form.type, balance: Number(form.balance), color: form.color, icon: form.icon })
  }

  const totalBalance = accounts.reduce((s, a) => s + a.balance, 0)

  return (
    <div className="p-6 space-y-5">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white">Contas</h1>
          <p className="text-sm text-gray-500 mt-0.5">Saldo total: <span className={`font-semibold ${totalBalance >= 0 ? 'text-emerald-400' : 'text-red-400'}`}>{fmt(totalBalance)}</span></p>
        </div>
        <button onClick={openCreate} className="btn-primary flex items-center gap-2">
          <Plus size={16} /> Nova Conta
        </button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {accounts.map(account => {
          const TypeIcon = ACCOUNT_TYPES.find(t => t.value === account.type)?.icon ?? Wallet
          return (
            <div key={account.id} className="card hover:border-gray-700 transition-colors group">
              <div className="flex items-start justify-between mb-4">
                <div className="flex items-center gap-3">
                  <div className="p-2.5 rounded-xl" style={{ background: `${account.color}20` }}>
                    <TypeIcon size={20} style={{ color: account.color }} />
                  </div>
                  <div>
                    <p className="font-semibold text-white">{account.name}</p>
                    <p className="text-xs text-gray-500">{ACCOUNT_TYPES.find(t => t.value === account.type)?.label}</p>
                  </div>
                </div>
                <div className="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                  <button onClick={() => openEdit(account)} className="p-1.5 rounded-lg hover:bg-gray-700 text-gray-400 hover:text-white transition-colors">
                    <Pencil size={14} />
                  </button>
                  <button onClick={() => { if (confirm('Excluir esta conta?')) deleteMutation.mutate(account.id) }}
                    className="p-1.5 rounded-lg hover:bg-red-900/40 text-gray-400 hover:text-red-400 transition-colors">
                    <Trash2 size={14} />
                  </button>
                </div>
              </div>
              <p className={`text-2xl font-bold ${account.balance >= 0 ? 'text-white' : 'text-red-400'}`}>
                {fmt(account.balance)}
              </p>
              <div className="mt-3 h-1 rounded-full" style={{ background: account.color, opacity: 0.4 }} />
            </div>
          )
        })}

        {accounts.length === 0 && (
          <div className="card col-span-3 text-center py-12 text-gray-600">
            Nenhuma conta cadastrada. Crie a primeira!
          </div>
        )}
      </div>

      {/* Form Modal */}
      {showForm && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 p-4">
          <div className="card w-full max-w-md">
            <div className="flex items-center justify-between mb-5">
              <h2 className="text-lg font-semibold text-white">{editing ? 'Editar Conta' : 'Nova Conta'}</h2>
              <button onClick={closeForm} className="btn-ghost p-1.5 text-sm">✕</button>
            </div>
            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label className="label">Nome</label>
                <input className="input" placeholder="Ex: Nubank, Carteira..." required
                  value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))} />
              </div>
              <div>
                <label className="label">Tipo</label>
                <select className="select" value={form.type} onChange={e => setForm(f => ({ ...f, type: e.target.value as AccountType }))}>
                  {ACCOUNT_TYPES.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}
                </select>
              </div>
              <div>
                <label className="label">{editing ? 'Saldo (R$)' : 'Saldo inicial (R$)'}</label>
                <input className="input" type="number" step="0.01" placeholder="0,00"
                  value={form.balance} onChange={e => setForm(f => ({ ...f, balance: e.target.value }))} required />
              </div>
              <div>
                <label className="label">Cor</label>
                <div className="flex gap-2 flex-wrap">
                  {COLORS.map(c => (
                    <button key={c} type="button"
                      onClick={() => setForm(f => ({ ...f, color: c }))}
                      className={`w-8 h-8 rounded-full border-2 transition-all ${form.color === c ? 'border-white scale-110' : 'border-transparent'}`}
                      style={{ background: c }} />
                  ))}
                </div>
              </div>
              <div className="flex gap-3 pt-2">
                <button type="button" onClick={closeForm}
                  className="flex-1 py-2.5 rounded-lg border border-gray-700 text-gray-400 hover:text-white transition-colors text-sm font-medium">
                  Cancelar
                </button>
                <button type="submit" disabled={saveMutation.isPending} className="flex-1 btn-primary">
                  {saveMutation.isPending ? 'Salvando...' : 'Salvar'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
