import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Pencil, Trash2, Search } from 'lucide-react'
import { buscarCartoes, criarCartao, atualizarCartao, excluirCartao } from '../api/cartoes'
import { buscarContas } from '../api/contas'
import SobreposicaoModal from '../components/SobreposicaoModal'
import SeletorBanco from '../components/SeletorBanco'
import LogoBanco from '../components/LogoBanco'
import AcaoNova from '../components/AcaoNova'
import CampoEntidade from '../components/forms/CampoEntidade'
import type { Cartao } from '../types'

const fmt = (v: number) =>
  new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(v)

const formPadrao = {
  nome: '', limite: '', diaFechamento: '', diaVencimento: '', contaPagamentoId: '',
  cor: '#6366f1', icone: 'credit-card', bancoId: undefined as number | undefined,
  entidadeId: undefined as number | null | undefined,
}

export default function Cartoes() {
  const qc = useQueryClient()
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<Cartao | null>(null)
  const [form, setForm] = useState(formPadrao)
  const [busca, setBusca] = useState('')

  const { data: cartoes = [] } = useQuery({ queryKey: ['cartoes'], queryFn: buscarCartoes })
  const { data: contas = [] } = useQuery({ queryKey: ['contas'], queryFn: buscarContas })

  const saveMutation = useMutation({
    mutationFn: (data: Parameters<typeof criarCartao>[0]) =>
      editing ? atualizarCartao(editing.id, data) : criarCartao(data),
    onSuccess: async () => { await qc.invalidateQueries({ queryKey: ['cartoes'] }); closeForm() },
  })

  const deleteMutation = useMutation({
    mutationFn: excluirCartao,
    onSuccess: async () => { await qc.invalidateQueries({ queryKey: ['cartoes'] }) },
  })

  const openCreate = () => { setEditing(null); setForm(formPadrao); setShowForm(true) }
  const openEdit = (c: Cartao) => {
    setEditing(c)
    setForm({
      nome: c.nome, limite: String(c.limite), diaFechamento: String(c.diaFechamento),
      diaVencimento: String(c.diaVencimento), contaPagamentoId: String(c.contaPagamentoId),
      cor: c.cor, icone: c.icone, bancoId: c.bancoId, entidadeId: c.entidadeId,
    })
    setShowForm(true)
  }
  const closeForm = () => { setShowForm(false); setEditing(null) }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    saveMutation.mutate({
      nome: form.nome,
      limite: Number(form.limite),
      diaFechamento: Number(form.diaFechamento),
      diaVencimento: Number(form.diaVencimento),
      contaPagamentoId: Number(form.contaPagamentoId),
      cor: form.cor,
      icone: form.icone,
      bancoId: form.bancoId,
      entidadeId: form.entidadeId ?? null,
    })
  }

  const cartoesFiltrados = cartoes.filter(c => c.nome.toLowerCase().includes(busca.toLowerCase()))

  return (
    <div className="p-6 space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-conteudo">Cartões</h1>
        <AcaoNova aoClicar={openCreate} rotulo="Novo cartão" />
      </div>

      <div className="relative max-w-sm">
        <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-conteudo-suave" />
        <input
          className="input pl-9" placeholder="Buscar cartões..."
          value={busca} onChange={e => setBusca(e.target.value)}
        />
      </div>

      <div className="card p-0 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-superficie-2 text-xs text-conteudo-suave uppercase tracking-wider">
                <th className="text-left font-semibold px-5 py-3">Cartão</th>
                <th className="text-left font-semibold px-5 py-3">Conta de pagamento</th>
                <th className="text-right font-semibold px-5 py-3">Fatura atual</th>
                <th className="text-right font-semibold px-5 py-3">Disponível</th>
                <th className="text-right font-semibold px-5 py-3">Ações</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-borda">
              {cartoesFiltrados.map(cartao => (
                <tr key={cartao.id} className="hover:bg-superficie-2 transition-colors group">
                  <td className="px-5 py-3">
                    <Link to={`/cartoes/${cartao.id}`} className="flex items-center gap-3 min-w-0">
                      <LogoBanco banco={cartao.banco} tamanho={36} />
                      <div className="min-w-0">
                        <p className="font-medium text-conteudo truncate">{cartao.nome}</p>
                        <p className="text-xs text-conteudo-suave">fecha dia {cartao.diaFechamento} · vence dia {cartao.diaVencimento}</p>
                      </div>
                    </Link>
                  </td>
                  <td className="px-5 py-3 text-conteudo-suave">{cartao.contaPagamento.nome}</td>
                  <td className="px-5 py-3 text-right text-conteudo">{fmt(cartao.faturaAtualTotal)}</td>
                  <td className="px-5 py-3 text-right font-semibold text-conteudo">
                    {fmt(cartao.limiteDisponivel)}
                    <span className="block text-xs font-normal text-conteudo-suave">de {fmt(cartao.limite)}</span>
                  </td>
                  <td className="px-5 py-3">
                    <div className="flex gap-1 justify-end md:opacity-0 md:group-hover:opacity-100 transition-opacity">
                      <button onClick={() => openEdit(cartao)} className="p-1.5 rounded-lg hover:bg-superficie-2 text-conteudo-suave hover:text-conteudo transition-colors">
                        <Pencil size={14} />
                      </button>
                      <button onClick={() => { if (confirm('Excluir este cartão?')) deleteMutation.mutate(cartao.id) }}
                        className="p-1.5 rounded-lg hover:bg-red-900/40 text-conteudo-suave hover:text-red-400 transition-colors">
                        <Trash2 size={14} />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {cartoesFiltrados.length === 0 && (
            <p className="text-center py-12 text-conteudo-suave text-sm">
              {cartoes.length === 0 ? 'Nenhum cartão cadastrado. Crie o primeiro!' : 'Nenhum cartão encontrado.'}
            </p>
          )}
        </div>
      </div>
      {cartoesFiltrados.length > 0 && (
        <p className="text-xs text-conteudo-suave">{cartoesFiltrados.length} registro{cartoesFiltrados.length !== 1 ? 's' : ''}</p>
      )}

      {showForm && (
        <SobreposicaoModal aoFechar={closeForm}>
          <div className="cartao-modal max-w-md">
            <div className="cartao-modal-cabecalho">
              <h2 className="text-lg font-semibold text-conteudo">{editing ? 'Editar Cartão' : 'Novo Cartão'}</h2>
              <button onClick={closeForm} className="btn-ghost p-1.5 text-sm">✕</button>
            </div>
            <form onSubmit={handleSubmit} className="cartao-modal-corpo">
              <CampoEntidade value={form.entidadeId} onChange={v => setForm(f => ({ ...f, entidadeId: v }))} />
              <div>
                <label className="label">Banco</label>
                <SeletorBanco bancoSelecionado={form.bancoId} aoSelecionar={b => setForm(f => ({ ...f, bancoId: b }))} />
              </div>
              <div>
                <label className="label">Nome</label>
                <input className="input" placeholder="Ex: Nubank, Inter..." required
                  value={form.nome} onChange={e => setForm(f => ({ ...f, nome: e.target.value }))} />
              </div>
              <div>
                <label className="label">Limite (R$)</label>
                <input className="input" type="number" step="0.01" min="0.01" placeholder="0,00" required
                  value={form.limite} onChange={e => setForm(f => ({ ...f, limite: e.target.value }))} />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="label">Dia do fechamento</label>
                  <input className="input" type="number" min="1" max="31" required
                    value={form.diaFechamento} onChange={e => setForm(f => ({ ...f, diaFechamento: e.target.value }))} />
                </div>
                <div>
                  <label className="label">Dia do vencimento</label>
                  <input className="input" type="number" min="1" max="31" required
                    value={form.diaVencimento} onChange={e => setForm(f => ({ ...f, diaVencimento: e.target.value }))} />
                </div>
              </div>
              <div>
                <label className="label">Conta de pagamento</label>
                <select className="select" required
                  value={form.contaPagamentoId} onChange={e => setForm(f => ({ ...f, contaPagamentoId: e.target.value }))}>
                  <option value="">Selecione...</option>
                  {[...contas].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')).map(c => <option key={c.id} value={c.id}>{c.nome}</option>)}
                </select>
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
