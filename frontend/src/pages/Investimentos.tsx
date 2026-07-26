import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'
import { format } from 'date-fns'
import { Pencil, Trash2, Ban, ArrowUpCircle, ArrowDownCircle, TrendingUp, Wallet } from 'lucide-react'
import {
  buscarAtivos, buscarPatrimonio, criarAtivo, atualizarAtivo, excluirAtivo, cancelarAtivo,
  aportarAtivo, resgatarAtivo, registrarRendimentoAtivo,
} from '../api/ativos'
import { buscarContas } from '../api/contas'
import { useLojaTema } from '../store/lojaTema'
import SobreposicaoModal from '../components/SobreposicaoModal'
import SeletorCor from '../components/SeletorCor'
import AcaoNova from '../components/AcaoNova'
import type { Ativo, TipoAtivo } from '../types'

const fmt = (v: number) =>
  new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(v)

const coresGrafico = (tema: 'claro' | 'escuro') =>
  tema === 'escuro'
    ? { grade: '#1f2937', eixo: '#6b7280', tooltipFundo: '#111827', tooltipBorda: '#374151', linha: '#4472d4' }
    : { grade: '#e2e8f0', eixo: '#64748b', tooltipFundo: '#ffffff', tooltipBorda: '#e2e8f0', linha: '#123a75' }

const CORES = ['#6366f1', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#06b6d4', '#ec4899', '#84cc16']
const TIPOS_ATIVO: { value: TipoAtivo; label: string }[] = [
  { value: 'RESERVA', label: 'Reserva de emergência' },
  { value: 'RENDA_FIXA', label: 'Renda fixa' },
  { value: 'RENDA_VARIAVEL', label: 'Renda variável' },
]

const formPadrao = { nome: '', tipo: 'RENDA_FIXA' as TipoAtivo, contaId: '', cor: CORES[0], icone: 'trending-up' }
const movimentoPadrao = { valor: '', contaId: '', data: format(new Date(), 'yyyy-MM-dd') }

export default function Investimentos() {
  const qc = useQueryClient()
  const tema = useLojaTema(s => s.tema)
  const cores = coresGrafico(tema)
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<Ativo | null>(null)
  const [form, setForm] = useState(formPadrao)
  const [movimento, setMovimento] = useState<{ ativo: Ativo; tipo: 'aportar' | 'resgatar' | 'rendimento' } | null>(null)
  const [movForm, setMovForm] = useState(movimentoPadrao)

  const { data: ativos = [] } = useQuery({ queryKey: ['ativos'], queryFn: buscarAtivos })
  const { data: patrimonio } = useQuery({ queryKey: ['patrimonio'], queryFn: () => buscarPatrimonio(6) })
  const { data: contas = [] } = useQuery({ queryKey: ['contas'], queryFn: buscarContas })

  const invalidar = () => Promise.all([
    qc.invalidateQueries({ queryKey: ['ativos'] }),
    qc.invalidateQueries({ queryKey: ['patrimonio'] }),
    qc.invalidateQueries({ queryKey: ['contas'] }),
    qc.invalidateQueries({ queryKey: ['transacoes'] }),
    qc.invalidateQueries({ queryKey: ['painel'] }),
  ])

  const saveMutation = useMutation({
    mutationFn: (data: { nome: string; tipo: TipoAtivo; contaId: number; cor: string; icone: string }) =>
      editing ? atualizarAtivo(editing.id, data) : criarAtivo(data),
    onSuccess: async () => { await invalidar(); closeForm() },
  })
  const deleteMutation = useMutation({
    mutationFn: excluirAtivo,
    onSuccess: invalidar,
    onError: (err: any) => alert(err?.response?.data?.mensagem || 'Não foi possível excluir'),
  })
  const cancelarMutation = useMutation({ mutationFn: cancelarAtivo, onSuccess: invalidar })

  const movimentoMutation = useMutation({
    mutationFn: ({ id, tipo, dados }: { id: number; tipo: 'aportar' | 'resgatar' | 'rendimento'; dados: { valor: number; contaId?: number; data?: string } }) => {
      if (tipo === 'aportar') return aportarAtivo(id, dados)
      if (tipo === 'resgatar') return resgatarAtivo(id, dados)
      return registrarRendimentoAtivo(id, dados)
    },
    onSuccess: async () => { await invalidar(); closeMovimento() },
    onError: (err: any) => alert(err?.response?.data?.mensagem || 'Não foi possível concluir a operação'),
  })

  const openCreate = () => { setEditing(null); setForm(formPadrao); setShowForm(true) }
  const openEdit = (a: Ativo) => {
    setEditing(a)
    setForm({ nome: a.nome, tipo: a.tipo, contaId: String(a.contaId), cor: a.cor, icone: a.icone })
    setShowForm(true)
  }
  const closeForm = () => { setShowForm(false); setEditing(null) }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    saveMutation.mutate({ nome: form.nome, tipo: form.tipo, contaId: Number(form.contaId), cor: form.cor, icone: form.icone })
  }

  const openMovimento = (ativo: Ativo, tipo: 'aportar' | 'resgatar' | 'rendimento') => {
    setMovimento({ ativo, tipo })
    setMovForm({ ...movimentoPadrao, contaId: contas[0] ? String(contas[0].id) : '' })
  }
  const closeMovimento = () => setMovimento(null)

  const handleMovimentoSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!movimento) return
    movimentoMutation.mutate({
      id: movimento.ativo.id,
      tipo: movimento.tipo,
      dados: {
        valor: Number(movForm.valor),
        contaId: movimento.tipo !== 'rendimento' ? Number(movForm.contaId) : undefined,
        data: movForm.data,
      },
    })
  }

  const rotuloTipo = (t: TipoAtivo) => TIPOS_ATIVO.find(o => o.value === t)?.label ?? t
  const rotuloMovimento = { aportar: 'Aportar em', resgatar: 'Resgatar de', rendimento: 'Registrar rendimento em' }

  return (
    <div className="p-6 space-y-5">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-conteudo">Investimentos</h1>
        <AcaoNova aoClicar={openCreate} rotulo="Novo ativo" />
      </div>

      {patrimonio && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
          <div className="card">
            <p className="text-xs text-conteudo-suave mb-1">Patrimônio total</p>
            <p className="text-2xl font-bold text-conteudo">{fmt(patrimonio.totalPatrimonio)}</p>
            <div className="mt-3 space-y-1.5">
              {patrimonio.porTipo.map(p => (
                <div key={p.tipo} className="flex items-center justify-between text-xs">
                  <span className="text-conteudo-suave">{rotuloTipo(p.tipo)}</span>
                  <span className="text-conteudo font-medium">{fmt(p.valor)}</span>
                </div>
              ))}
            </div>
          </div>
          <div className="card lg:col-span-2">
            <h3 className="text-sm font-semibold text-conteudo mb-4">Evolução do patrimônio</h3>
            <ResponsiveContainer width="100%" height={160}>
              <AreaChart data={patrimonio.evolucaoMensal}>
                <defs>
                  <linearGradient id="patGrad" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor={cores.linha} stopOpacity={0.3} />
                    <stop offset="95%" stopColor={cores.linha} stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke={cores.grade} />
                <XAxis dataKey="mes" tick={{ fill: cores.eixo, fontSize: 10 }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fill: cores.eixo, fontSize: 10 }} axisLine={false} tickLine={false}
                  tickFormatter={v => `${(v / 1000).toFixed(0)}k`} />
                <Tooltip
                  contentStyle={{ background: cores.tooltipFundo, border: `1px solid ${cores.tooltipBorda}`, borderRadius: 8 }}
                  formatter={(v) => fmt(Number(v))}
                />
                <Area type="monotone" dataKey="valor" name="Patrimônio" stroke={cores.linha} strokeWidth={2} fill="url(#patGrad)" />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {ativos.map(ativo => (
          <div key={ativo.id} className="card group">
            <div className="flex items-start justify-between mb-3">
              <div className="flex items-center gap-3 min-w-0">
                <div className="p-2.5 rounded-xl shrink-0" style={{ background: `${ativo.cor}20` }}>
                  <TrendingUp size={20} style={{ color: ativo.cor }} />
                </div>
                <div className="min-w-0">
                  <p className="font-semibold text-conteudo truncate">{ativo.nome}</p>
                  <p className="text-xs text-conteudo-suave">{rotuloTipo(ativo.tipo)}{ativo.dataCancelamento ? ' · Cancelado' : ''}</p>
                </div>
              </div>
              <div className="flex gap-1 md:opacity-0 md:group-hover:opacity-100 transition-opacity shrink-0">
                <button onClick={() => openEdit(ativo)} className="p-1.5 rounded-lg hover:bg-superficie-2 text-conteudo-suave hover:text-conteudo transition-colors">
                  <Pencil size={14} />
                </button>
                {!ativo.dataCancelamento && (
                  <button onClick={() => { if (confirm('Cancelar este ativo?')) cancelarMutation.mutate(ativo.id) }}
                    className="p-1.5 rounded-lg hover:bg-orange-900/40 text-conteudo-suave hover:text-orange-500 transition-colors">
                    <Ban size={14} />
                  </button>
                )}
                <button onClick={() => { if (confirm('Excluir este ativo?')) deleteMutation.mutate(ativo.id) }}
                  className="p-1.5 rounded-lg hover:bg-red-900/40 text-conteudo-suave hover:text-red-400 transition-colors">
                  <Trash2 size={14} />
                </button>
              </div>
            </div>

            <p className="text-xl font-bold text-conteudo">{fmt(ativo.valorAtual)}</p>
            <p className="text-xs text-conteudo-suave">{ativo.percentualCarteira.toFixed(1)}% da carteira</p>

            {!ativo.dataCancelamento && (
              <div className="flex gap-2 mt-3">
                <button onClick={() => openMovimento(ativo, 'aportar')}
                  className="flex-1 flex items-center justify-center gap-1 py-2 rounded-lg border border-borda text-xs font-medium text-emerald-500 hover:bg-emerald-900/10 transition-colors">
                  <ArrowUpCircle size={13} /> Aportar
                </button>
                <button onClick={() => openMovimento(ativo, 'rendimento')}
                  className="flex-1 flex items-center justify-center gap-1 py-2 rounded-lg border border-borda text-xs font-medium text-blue-500 hover:bg-blue-900/10 transition-colors">
                  <Wallet size={13} /> Rendimento
                </button>
                <button onClick={() => openMovimento(ativo, 'resgatar')} disabled={ativo.valorAtual <= 0}
                  className="flex-1 flex items-center justify-center gap-1 py-2 rounded-lg border border-borda text-xs font-medium text-amber-500 hover:bg-amber-900/10 transition-colors disabled:opacity-40 disabled:cursor-not-allowed">
                  <ArrowDownCircle size={13} /> Resgatar
                </button>
              </div>
            )}
          </div>
        ))}

        {ativos.length === 0 && (
          <div className="card col-span-full text-center py-12 text-conteudo-suave">
            Nenhum ativo cadastrado. Crie o primeiro!
          </div>
        )}
      </div>

      {showForm && (
        <SobreposicaoModal aoFechar={closeForm}>
          <div className="cartao-modal max-w-md">
            <div className="cartao-modal-cabecalho">
              <h2 className="text-lg font-semibold text-conteudo">{editing ? 'Editar Ativo' : 'Novo Ativo'}</h2>
              <button onClick={closeForm} className="btn-ghost p-1.5 text-sm">✕</button>
            </div>
            <form onSubmit={handleSubmit} className="cartao-modal-corpo">
              <div>
                <label className="label">Nome</label>
                <input className="input" placeholder="Ex: Tesouro Selic, PETR4..." required
                  value={form.nome} onChange={e => setForm(f => ({ ...f, nome: e.target.value }))} />
              </div>
              <div>
                <label className="label">Tipo</label>
                <select className="select" value={form.tipo} onChange={e => setForm(f => ({ ...f, tipo: e.target.value as TipoAtivo }))}>
                  {TIPOS_ATIVO.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}
                </select>
              </div>
              <div>
                <label className="label">Conta vinculada (origem dos aportes)</label>
                <select className="select" required
                  value={form.contaId} onChange={e => setForm(f => ({ ...f, contaId: e.target.value }))}>
                  <option value="">Selecione...</option>
                  {[...contas].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')).map(c => <option key={c.id} value={c.id}>{c.nome}</option>)}
                </select>
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

      {movimento && (
        <SobreposicaoModal aoFechar={closeMovimento}>
          <div className="cartao-modal max-w-sm">
            <div className="cartao-modal-cabecalho">
              <h2 className="text-lg font-semibold text-conteudo">{rotuloMovimento[movimento.tipo]} {movimento.ativo.nome}</h2>
              <button onClick={closeMovimento} className="btn-ghost p-1.5 text-sm">✕</button>
            </div>
            <form onSubmit={handleMovimentoSubmit} className="cartao-modal-corpo">
              {movimento.tipo !== 'rendimento' && (
                <div>
                  <label className="label">{movimento.tipo === 'aportar' ? 'Conta de origem' : 'Conta de destino'}</label>
                  <select className="select" required
                    value={movForm.contaId} onChange={e => setMovForm(f => ({ ...f, contaId: e.target.value }))}>
                    <option value="">Selecione...</option>
                    {[...contas].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')).map(c => <option key={c.id} value={c.id}>{c.nome}</option>)}
                  </select>
                </div>
              )}
              <div>
                <label className="label">Valor (R$)</label>
                <input className="input" type="number" step="0.01" min="0.01"
                  max={movimento.tipo === 'resgatar' ? movimento.ativo.valorAtual : undefined}
                  placeholder="0,00" required
                  value={movForm.valor} onChange={e => setMovForm(f => ({ ...f, valor: e.target.value }))} />
                {movimento.tipo === 'resgatar' && (
                  <p className="text-xs text-conteudo-suave mt-1">Disponível: {fmt(movimento.ativo.valorAtual)}</p>
                )}
              </div>
              <div>
                <label className="label">Data</label>
                <input className="input" type="date" max={format(new Date(), 'yyyy-MM-dd')} required
                  value={movForm.data} onChange={e => setMovForm(f => ({ ...f, data: e.target.value }))} />
              </div>
              <div className="flex gap-3 pt-2">
                <button type="button" onClick={closeMovimento}
                  className="flex-1 py-2.5 rounded-lg border border-borda text-conteudo-suave hover:text-conteudo transition-colors text-sm font-medium">
                  Cancelar
                </button>
                <button type="submit" disabled={movimentoMutation.isPending} className="flex-1 btn-primary">
                  {movimentoMutation.isPending ? 'Salvando...' : 'Confirmar'}
                </button>
              </div>
            </form>
          </div>
        </SobreposicaoModal>
      )}
    </div>
  )
}
