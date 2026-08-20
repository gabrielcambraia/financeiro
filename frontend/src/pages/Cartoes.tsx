import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'
import { Pencil, Trash2, Search } from 'lucide-react'
import { buscarCartoes, criarCartao, atualizarCartao, excluirCartao } from '../api/cartoes'
import { buscarContas } from '../api/contas'
import SobreposicaoModal from '../components/SobreposicaoModal'
import ModalConfirmacao from '../components/ModalConfirmacao'
import SeletorBanco from '../components/SeletorBanco'
import SeletorCor from '../components/SeletorCor'
import LogoBanco from '../components/LogoBanco'
import AcaoNova from '../components/AcaoNova'
import Spinner from '../components/Spinner'
import type { Cartao } from '../types'

const fmt = (v: number) =>
  new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(v)

const CORES = ['#ef4444','#f97316','#eab308','#22c55e','#10b981','#06b6d4','#3b82f6','#8b5cf6','#ec4899','#6b7280','#6366f1','#84cc16','#000000']

const formPadrao = {
  nome: '', limite: '', diaFechamento: '', diaVencimento: '', contaPagamentoId: '',
  cor: '#6366f1', icone: 'credit-card', bancoId: undefined as number | undefined,
}

export default function Cartoes() {
  const qc = useQueryClient()
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<Cartao | null>(null)
  const [form, setForm] = useState(formPadrao)
  const [busca, setBusca] = useState('')
  const [confirmarExcluir, setConfirmarExcluir] = useState<Cartao | null>(null)

  const { data: cartoes = [], isLoading } = useQuery({ queryKey: ['cartoes'], queryFn: buscarCartoes })
  const { data: contas = [] } = useQuery({ queryKey: ['contas'], queryFn: buscarContas })

  const saveMutation = useMutation({
    mutationFn: (data: Parameters<typeof criarCartao>[0]) =>
      editing ? atualizarCartao(editing.id, data) : criarCartao(data),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ['cartoes'] })
      toast.success('Cartão salvo')
      closeForm()
    },
  })

  const deleteMutation = useMutation({
    mutationFn: excluirCartao,
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ['cartoes'] })
      setConfirmarExcluir(null)
      toast.success('Cartão excluído')
    },
  })

  const openCreate = () => { setEditing(null); setForm(formPadrao); setShowForm(true) }
  const openEdit = (c: Cartao) => {
    setEditing(c)
    setForm({
      nome: c.nome, limite: String(c.limite), diaFechamento: String(c.diaFechamento),
      diaVencimento: String(c.diaVencimento), contaPagamentoId: String(c.contaPagamentoId),
      cor: c.cor, icone: c.icone, bancoId: c.bancoId,
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

      {/* Desktop: tabela */}
      <div className="card p-0 overflow-hidden hidden md:block">
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
                      <button onClick={() => setConfirmarExcluir(cartao)}
                        className="p-1.5 rounded-lg hover:bg-red-900/40 text-conteudo-suave hover:text-red-400 transition-colors">
                        <Trash2 size={14} />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {isLoading && (
            <div className="flex justify-center py-12"><Spinner /></div>
          )}
          {!isLoading && cartoesFiltrados.length === 0 && (
            <p className="text-center py-12 text-conteudo-suave text-sm">
              {cartoes.length === 0 ? 'Nenhum cartão cadastrado. Crie o primeiro!' : 'Nenhum cartão encontrado.'}
            </p>
          )}
        </div>
      </div>

      {/* Mobile: cards */}
      <div className="md:hidden space-y-3">
        {isLoading && <div className="flex justify-center py-12"><Spinner /></div>}
        {!isLoading && cartoesFiltrados.length === 0 && (
          <p className="text-center py-12 text-conteudo-suave text-sm">
            {cartoes.length === 0 ? 'Nenhum cartão cadastrado. Crie o primeiro!' : 'Nenhum cartão encontrado.'}
          </p>
        )}
        {cartoesFiltrados.map(cartao => (
          <div key={cartao.id} className="card p-4 space-y-3">
            <div className="flex items-center gap-3">
              <Link to={`/cartoes/${cartao.id}`} className="flex items-center gap-3 flex-1 min-w-0">
                <LogoBanco banco={cartao.banco} tamanho={40} />
                <div className="min-w-0">
                  <p className="font-semibold text-conteudo truncate">{cartao.nome}</p>
                  <p className="text-xs text-conteudo-suave">fecha dia {cartao.diaFechamento} · vence dia {cartao.diaVencimento}</p>
                  <p className="text-xs text-conteudo-suave truncate">{cartao.contaPagamento.nome}</p>
                </div>
              </Link>
              <div className="flex gap-1 shrink-0">
                <button onClick={() => openEdit(cartao)} className="p-1.5 rounded-lg hover:bg-superficie-2 text-conteudo-suave hover:text-conteudo transition-colors">
                  <Pencil size={14} />
                </button>
                <button onClick={() => setConfirmarExcluir(cartao)}
                  className="p-1.5 rounded-lg hover:bg-red-900/40 text-conteudo-suave hover:text-red-400 transition-colors">
                  <Trash2 size={14} />
                </button>
              </div>
            </div>
            <div className="flex gap-0 border border-borda rounded-xl overflow-hidden">
              <div className="flex-1 px-4 py-2.5 text-center border-r border-borda">
                <p className="text-xs text-conteudo-suave mb-0.5">Fatura atual</p>
                <p className="text-sm font-semibold text-conteudo whitespace-nowrap">{fmt(cartao.faturaAtualTotal)}</p>
              </div>
              <div className="flex-1 px-4 py-2.5 text-center">
                <p className="text-xs text-conteudo-suave mb-0.5">Disponível</p>
                <p className="text-sm font-semibold text-conteudo whitespace-nowrap">{fmt(cartao.limiteDisponivel)}</p>
                <p className="text-xs text-conteudo-suave whitespace-nowrap">de {fmt(cartao.limite)}</p>
              </div>
            </div>
          </div>
        ))}
      </div>
      {cartoesFiltrados.length > 0 && (
        <p className="text-xs text-conteudo-suave">{cartoesFiltrados.length} registro{cartoesFiltrados.length !== 1 ? 's' : ''}</p>
      )}

      <ModalConfirmacao
        aberto={confirmarExcluir !== null}
        titulo="Excluir cartão"
        aoFechar={() => !deleteMutation.isPending && setConfirmarExcluir(null)}
        botoes={[{ rotulo: 'Excluir', variante: 'perigo', aoClicar: () => deleteMutation.mutate(confirmarExcluir!.id), carregando: deleteMutation.isPending }]}
      >
        {`Deseja excluir o cartão "${confirmarExcluir?.nome}"? Esta ação não pode ser desfeita.`}
      </ModalConfirmacao>

      {showForm && (
        <SobreposicaoModal aoFechar={closeForm}>
          <div className="cartao-modal max-w-md">
            <div className="cartao-modal-cabecalho">
              <h2 className="text-lg font-semibold text-conteudo">{editing ? 'Editar Cartão' : 'Novo Cartão'}</h2>
              <button onClick={closeForm} className="btn-ghost p-1.5 text-sm">✕</button>
            </div>
            <form onSubmit={handleSubmit} className="cartao-modal-corpo">
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
              <div>
                <label className="label">Cor</label>
                <SeletorCor cores={CORES} corSelecionada={form.cor} tamanho="sm"
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
