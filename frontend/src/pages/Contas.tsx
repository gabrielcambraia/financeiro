import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Pencil, Trash2, Search } from 'lucide-react'
import { buscarContas, criarConta, atualizarConta, excluirConta } from '../api/contas'
import SobreposicaoModal from '../components/SobreposicaoModal'
import SeletorBanco from '../components/SeletorBanco'
import LogoBanco from '../components/LogoBanco'
import AcaoNova from '../components/AcaoNova'
import type { Conta, TipoConta } from '../types'

const fmt = (v: number) =>
  new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(v)

const TIPOS_CONTA: { value: TipoConta; label: string }[] = [
  { value: 'CORRENTE', label: 'Conta Corrente' },
  { value: 'POUPANCA', label: 'Poupança' },
  { value: 'CARTEIRA', label: 'Carteira' },
  { value: 'INVESTIMENTO', label: 'Investimentos' },
]

const formPadrao = { nome: '', tipo: 'CORRENTE' as TipoConta, saldoInicial: '', cor: '#6366f1', icone: 'wallet', bancoId: undefined as number | undefined }

export default function Contas() {
  const qc = useQueryClient()
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<Conta | null>(null)
  const [form, setForm] = useState(formPadrao)
  const [busca, setBusca] = useState('')

  const { data: contas = [] } = useQuery({ queryKey: ['contas'], queryFn: buscarContas })

  const saveMutation = useMutation({
    mutationFn: () => {
      const base = { nome: form.nome, tipo: form.tipo, cor: form.cor, icone: form.icone, bancoId: form.bancoId }
      return editing
        ? atualizarConta(editing.id, base)
        : criarConta({ ...base, saldoInicial: Number(form.saldoInicial) })
    },
    onSuccess: async () => { await qc.invalidateQueries({ queryKey: ['contas'] }); closeForm() },
  })

  const deleteMutation = useMutation({
    mutationFn: excluirConta,
    onSuccess: async () => { await qc.invalidateQueries({ queryKey: ['contas'] }) },
  })

  const openCreate = () => { setEditing(null); setForm(formPadrao); setShowForm(true) }
  const openEdit = (c: Conta) => {
    setEditing(c)
    setForm({ nome: c.nome, tipo: c.tipo, saldoInicial: String(c.saldoInicial), cor: c.cor, icone: c.icone, bancoId: c.bancoId })
    setShowForm(true)
  }
  const closeForm = () => { setShowForm(false); setEditing(null) }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    saveMutation.mutate()
  }

  const contasFiltradas = contas.filter(c => c.nome.toLowerCase().includes(busca.toLowerCase()))

  return (
    <div className="p-6 space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-conteudo">Contas</h1>
        <AcaoNova aoClicar={openCreate} rotulo="Nova conta" />
      </div>

      <div className="relative max-w-sm">
        <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-conteudo-suave" />
        <input
          className="input pl-9" placeholder="Buscar contas..."
          value={busca} onChange={e => setBusca(e.target.value)}
        />
      </div>

      <div className="card p-0 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-superficie-2 text-xs text-conteudo-suave uppercase tracking-wider">
                <th className="text-left font-semibold px-5 py-3">Conta</th>
                <th className="text-left font-semibold px-5 py-3">Tipo</th>
                <th className="text-right font-semibold px-5 py-3">Saldo</th>
                <th className="text-right font-semibold px-5 py-3">Ações</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-borda">
              {contasFiltradas.map(conta => (
                <tr key={conta.id} className="hover:bg-superficie-2 transition-colors group">
                  <td className="px-5 py-3">
                    <div className="flex items-center gap-3">
                      <LogoBanco banco={conta.banco} tamanho={36} />
                      <span className="font-medium text-conteudo">{conta.nome}</span>
                    </div>
                  </td>
                  <td className="px-5 py-3">
                    <span className="text-xs px-2 py-1 rounded-full bg-superficie-2 text-conteudo-suave">
                      {TIPOS_CONTA.find(t => t.value === conta.tipo)?.label}
                    </span>
                  </td>
                  <td className={`px-5 py-3 text-right font-semibold ${conta.saldo >= 0 ? 'text-emerald-500' : 'text-red-500'}`}>
                    {fmt(conta.saldo)}
                  </td>
                  <td className="px-5 py-3">
                    <div className="flex gap-1 justify-end md:opacity-0 md:group-hover:opacity-100 transition-opacity">
                      <button onClick={() => openEdit(conta)} className="p-1.5 rounded-lg hover:bg-superficie-2 text-conteudo-suave hover:text-conteudo transition-colors">
                        <Pencil size={14} />
                      </button>
                      <button onClick={() => { if (confirm('Excluir esta conta?')) deleteMutation.mutate(conta.id) }}
                        className="p-1.5 rounded-lg hover:bg-red-900/40 text-conteudo-suave hover:text-red-400 transition-colors">
                        <Trash2 size={14} />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {contasFiltradas.length === 0 && (
            <p className="text-center py-12 text-conteudo-suave text-sm">
              {contas.length === 0 ? 'Nenhuma conta cadastrada. Crie a primeira!' : 'Nenhuma conta encontrada.'}
            </p>
          )}
        </div>
      </div>
      {contasFiltradas.length > 0 && (
        <p className="text-xs text-conteudo-suave">{contasFiltradas.length} registro{contasFiltradas.length !== 1 ? 's' : ''}</p>
      )}

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
                <label className="label">Banco</label>
                <SeletorBanco bancoSelecionado={form.bancoId} aoSelecionar={b => setForm(f => ({ ...f, bancoId: b }))} />
              </div>
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
                <label className="label">Saldo inicial (R$)</label>
                <input className="input" type="number" step="0.01" placeholder="0,00"
                  value={form.saldoInicial} onChange={e => setForm(f => ({ ...f, saldoInicial: e.target.value }))}
                  disabled={!!editing} readOnly={!!editing} required />
                {editing && (
                  <p className="text-xs text-conteudo-suave mt-1">
                    Saldo inicial e saldo atual não podem ser editados depois de criada a conta.
                    Saldo atual: {fmt(editing.saldo)}
                  </p>
                )}
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
