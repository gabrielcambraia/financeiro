import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Pencil, Trash2, Wallet, PiggyBank, CreditCard, TrendingUp } from 'lucide-react'
import { buscarContas, criarConta, atualizarConta, excluirConta } from '../api/contas'
import SobreposicaoModal from '../components/SobreposicaoModal'
import SeletorCor from '../components/SeletorCor'
import AcaoNova from '../components/AcaoNova'
import type { Conta, TipoConta } from '../types'

const fmt = (v: number) =>
  new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(v)

const TIPOS_CONTA: { value: TipoConta; label: string; icon: typeof Wallet }[] = [
  { value: 'CORRENTE', label: 'Conta Corrente', icon: Wallet },
  { value: 'POUPANCA', label: 'Poupança', icon: PiggyBank },
  { value: 'CARTEIRA', label: 'Carteira', icon: CreditCard },
  { value: 'INVESTIMENTO', label: 'Investimentos', icon: TrendingUp },
]

const CORES = ['#6366f1', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#06b6d4', '#ec4899', '#84cc16']

const formPadrao = { nome: '', tipo: 'CORRENTE' as TipoConta, saldo: '', cor: CORES[0], icone: 'wallet' }

export default function Contas() {
  const qc = useQueryClient()
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<Conta | null>(null)
  const [form, setForm] = useState(formPadrao)

  const { data: contas = [] } = useQuery({ queryKey: ['contas'], queryFn: buscarContas })

  const saveMutation = useMutation({
    mutationFn: (data: Omit<Conta, 'id'>) =>
      editing ? atualizarConta(editing.id, data) : criarConta(data),
    onSuccess: async () => { await qc.invalidateQueries({ queryKey: ['contas'] }); closeForm() },
  })

  const deleteMutation = useMutation({
    mutationFn: excluirConta,
    onSuccess: async () => { await qc.invalidateQueries({ queryKey: ['contas'] }) },
  })

  const openCreate = () => { setEditing(null); setForm(formPadrao); setShowForm(true) }
  const openEdit = (c: Conta) => {
    setEditing(c)
    setForm({ nome: c.nome, tipo: c.tipo, saldo: String(c.saldo), cor: c.cor, icone: c.icone })
    setShowForm(true)
  }
  const closeForm = () => { setShowForm(false); setEditing(null) }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    saveMutation.mutate({ nome: form.nome, tipo: form.tipo, saldo: Number(form.saldo), cor: form.cor, icone: form.icone })
  }

  const saldoTotal = contas.reduce((s, c) => s + c.saldo, 0)

  return (
    <div className="p-6 space-y-5">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-conteudo">Contas</h1>
          <p className="text-sm text-conteudo-suave mt-0.5">Saldo total: <span className={`font-semibold ${saldoTotal >= 0 ? 'text-emerald-500' : 'text-red-500'}`}>{fmt(saldoTotal)}</span></p>
        </div>
        <AcaoNova aoClicar={openCreate} rotulo="Nova conta" />
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {contas.map(conta => {
          const IconeTipo = TIPOS_CONTA.find(t => t.value === conta.tipo)?.icon ?? Wallet
          return (
            <div key={conta.id} className="card hover:border-conteudo-suave transition-colors group">
              <div className="flex items-start justify-between mb-4">
                <div className="flex items-center gap-3">
                  <div className="p-2.5 rounded-xl" style={{ background: `${conta.cor}20` }}>
                    <IconeTipo size={20} style={{ color: conta.cor }} />
                  </div>
                  <div>
                    <p className="font-semibold text-conteudo">{conta.nome}</p>
                    <p className="text-xs text-conteudo-suave">{TIPOS_CONTA.find(t => t.value === conta.tipo)?.label}</p>
                  </div>
                </div>
                <div className="flex gap-1 md:opacity-0 md:group-hover:opacity-100 transition-opacity">
                  <button onClick={() => openEdit(conta)} className="p-1.5 rounded-lg hover:bg-superficie-2 text-conteudo-suave hover:text-conteudo transition-colors">
                    <Pencil size={14} />
                  </button>
                  <button onClick={() => { if (confirm('Excluir esta conta?')) deleteMutation.mutate(conta.id) }}
                    className="p-1.5 rounded-lg hover:bg-red-900/40 text-conteudo-suave hover:text-red-400 transition-colors">
                    <Trash2 size={14} />
                  </button>
                </div>
              </div>
              <p className={`text-2xl font-bold ${conta.saldo >= 0 ? 'text-conteudo' : 'text-red-500'}`}>
                {fmt(conta.saldo)}
              </p>
              <div className="mt-3 h-1 rounded-full" style={{ background: conta.cor, opacity: 0.4 }} />
            </div>
          )
        })}

        {contas.length === 0 && (
          <div className="card col-span-full text-center py-12 text-conteudo-suave">
            Nenhuma conta cadastrada. Crie a primeira!
          </div>
        )}
      </div>

      {/* Modal do formulário */}
      {showForm && (
        <SobreposicaoModal aoFechar={closeForm}>
          <div className="cartao-modal max-w-md">
            <div className="cartao-modal-cabecalho">
              <h2 className="text-lg font-semibold text-conteudo">{editing ? 'Editar Conta' : 'Nova Conta'}</h2>
              <button onClick={closeForm} className="btn-ghost p-1.5 text-sm">✕</button>
            </div>
            <form onSubmit={handleSubmit} className="cartao-modal-corpo">
              <div>
                <label className="label">Nome</label>
                <input className="input" placeholder="Ex: Nubank, Carteira..." required
                  value={form.nome} onChange={e => setForm(f => ({ ...f, nome: e.target.value }))} />
              </div>
              <div>
                <label className="label">Tipo</label>
                <select className="select" value={form.tipo} onChange={e => setForm(f => ({ ...f, tipo: e.target.value as TipoConta }))}>
                  {TIPOS_CONTA.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}
                </select>
              </div>
              <div>
                <label className="label">{editing ? 'Saldo (R$)' : 'Saldo inicial (R$)'}</label>
                <input className="input" type="number" step="0.01" placeholder="0,00"
                  value={form.saldo} onChange={e => setForm(f => ({ ...f, saldo: e.target.value }))} required />
              </div>
              <div>
                <label className="label">Cor</label>
                <SeletorCor cores={CORES} corSelecionada={form.cor}
                  aoSelecionar={c => setForm(f => ({ ...f, cor: c }))} />
              </div>
              <div className="flex gap-3 pt-2">
                <button type="button" onClick={closeForm}
                  className="flex-1 py-2.5 rounded-lg border border-borda text-conteudo-suave hover:text-conteudo transition-colors text-sm font-medium">
                  Cancelar
                </button>
                <button type="submit" disabled={saveMutation.isPending} className="flex-1 btn-primary">
                  {saveMutation.isPending ? 'Salvando...' : 'Salvar'}
                </button>
              </div>
            </form>
          </div>
        </SobreposicaoModal>
      )}
    </div>
  )
}
