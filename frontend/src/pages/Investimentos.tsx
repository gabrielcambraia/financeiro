import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'
import { format } from 'date-fns'
import { Pencil, Trash2, Ban, ArrowUpCircle, ArrowDownCircle, TrendingUp, Wallet } from 'lucide-react'
import {
  buscarAtivos, buscarPatrimonio, criarAtivo, atualizarAtivo, excluirAtivo, cancelarAtivo,
  aportarAtivo, resgatarAtivo, registrarRendimentoAtivo,
  impactoCancelamentoAtivo, impactoExclusaoAtivo,
} from '../api/ativos'
import { buscarContas } from '../api/contas'
import { useLojaTema } from '../store/lojaTema'
import SobreposicaoModal from '../components/SobreposicaoModal'
import ModalConfirmacao from '../components/ModalConfirmacao'
import SeletorCor from '../components/SeletorCor'
import AcaoNova from '../components/AcaoNova'
import CampoEntidade from '../components/forms/CampoEntidade'
import Spinner from '../components/Spinner'
import type { Ativo, TipoAtivo, TipoRemuneracao, RespostaImpacto } from '../types'

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

const TIPOS_REMUNERACAO: { value: TipoRemuneracao; label: string }[] = [
  { value: 'NENHUMA', label: 'Nenhuma (manual)' },
  { value: 'PRE_FIXADA', label: 'Pré-fixada' },
  { value: 'POS_CDI', label: 'Pós-fixada (% do CDI)' },
  { value: 'POS_SELIC', label: 'Pós-fixada (% da Selic)' },
  { value: 'IPCA_MAIS', label: 'IPCA+' },
]

const rotuloTaxa = (tipo: TipoRemuneracao) => {
  if (tipo === 'POS_CDI') return '% do CDI'
  if (tipo === 'POS_SELIC') return '% da Selic'
  return '% ao ano' // PRE_FIXADA, IPCA_MAIS (spread)
}

const formPadrao = {
  nome: '', tipo: 'RENDA_FIXA' as TipoAtivo, contaId: '', cor: CORES[0], icone: 'trending-up',
  remuneracaoTipo: 'NENHUMA' as TipoRemuneracao, taxa: '', inicioRendimento: '', isentoIr: false,
  valorInicial: '', dataInicial: format(new Date(), 'yyyy-MM-dd'),
  entidadeId: undefined as number | null | undefined,
}
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
  const [modalAcao, setModalAcao] = useState<{
    tipo: 'cancelar' | 'excluir'; item: Ativo; impacto: RespostaImpacto | null; carregando: boolean
  } | null>(null)

  const [filtroStatus, setFiltroStatus] = useState<'ATIVOS' | 'CANCELADOS'>('ATIVOS')

  const { data: ativos = [], isLoading } = useQuery({ queryKey: ['ativos'], queryFn: buscarAtivos })
  const { data: patrimonio } = useQuery({ queryKey: ['patrimonio'], queryFn: () => buscarPatrimonio(6) })
  const { data: contas = [] } = useQuery({ queryKey: ['contas'], queryFn: buscarContas })

  const ativosFiltrados = ativos.filter(a =>
    filtroStatus === 'ATIVOS' ? !a.dataCancelamento : !!a.dataCancelamento
  )

  const invalidar = () => Promise.all([
    qc.invalidateQueries({ queryKey: ['ativos'] }),
    qc.invalidateQueries({ queryKey: ['patrimonio'] }),
    qc.invalidateQueries({ queryKey: ['contas'] }),
    qc.invalidateQueries({ queryKey: ['transacoes'] }),
    qc.invalidateQueries({ queryKey: ['painel'] }),
  ])

  const saveMutation = useMutation({
    mutationFn: (data: {
      nome: string; tipo: TipoAtivo; contaId: number; cor: string; icone: string
      remuneracaoTipo: TipoRemuneracao; taxa: number | null; inicioRendimento: string | null; isentoIr: boolean
      valorInicial?: number | null; dataInicial?: string | null; entidadeId?: number | null
    }) =>
      editing ? atualizarAtivo(editing.id, data) : criarAtivo(data),
    onSuccess: async () => { await invalidar(); toast.success('Ativo salvo'); closeForm() },
  })
  const deleteMutation = useMutation({
    mutationFn: excluirAtivo,
    onSuccess: async () => { await invalidar(); setModalAcao(null); toast.success('Ativo excluído') },
  })
  const cancelarMutation = useMutation({
    mutationFn: cancelarAtivo,
    onSuccess: async () => { await invalidar(); setModalAcao(null); toast.success('Ativo cancelado') },
  })

  const movimentoMutation = useMutation({
    mutationFn: ({ id, tipo, dados }: { id: number; tipo: 'aportar' | 'resgatar' | 'rendimento'; dados: { valor: number; contaId?: number; data?: string } }) => {
      if (tipo === 'aportar') return aportarAtivo(id, dados)
      if (tipo === 'resgatar') return resgatarAtivo(id, dados)
      return registrarRendimentoAtivo(id, dados)
    },
    onSuccess: async () => { await invalidar(); toast.success('Operação realizada'); closeMovimento() },
  })

  const openCreate = () => { setEditing(null); setForm(formPadrao); setShowForm(true) }
  const openEdit = (a: Ativo) => {
    setEditing(a)
    setForm({
      nome: a.nome, tipo: a.tipo, contaId: String(a.contaId), cor: a.cor, icone: a.icone,
      remuneracaoTipo: a.remuneracaoTipo ?? 'NENHUMA',
      taxa: a.taxa != null ? String(a.taxa) : '',
      inicioRendimento: a.inicioRendimento ?? '',
      isentoIr: a.isentoIr ?? false,
      valorInicial: '', dataInicial: format(new Date(), 'yyyy-MM-dd'),
      entidadeId: a.entidadeId,
    })
    setShowForm(true)
  }
  const closeForm = () => { setShowForm(false); setEditing(null) }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    saveMutation.mutate({
      nome: form.nome, tipo: form.tipo, contaId: Number(form.contaId), cor: form.cor, icone: form.icone,
      remuneracaoTipo: form.remuneracaoTipo,
      taxa: form.remuneracaoTipo !== 'NENHUMA' && form.taxa !== '' ? Number(form.taxa) : null,
      inicioRendimento: form.remuneracaoTipo !== 'NENHUMA' && form.inicioRendimento ? form.inicioRendimento : null,
      isentoIr: form.isentoIr,
      entidadeId: form.entidadeId ?? null,
      ...(!editing && form.valorInicial !== '' ? { valorInicial: Number(form.valorInicial), dataInicial: form.dataInicial } : {}),
    })
  }

  const abrirModalAcao = async (ativo: Ativo, tipo: 'cancelar' | 'excluir') => {
    setModalAcao({ tipo, item: ativo, impacto: null, carregando: true })
    try {
      const impacto = tipo === 'cancelar'
        ? await impactoCancelamentoAtivo(ativo.id)
        : await impactoExclusaoAtivo(ativo.id)
      setModalAcao({ tipo, item: ativo, impacto, carregando: false })
    } catch {
      setModalAcao({ tipo, item: ativo, impacto: null, carregando: false })
    }
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
        contaId: movimento.tipo !== 'rendimento' && !movimento.ativo.contaId ? Number(movForm.contaId) : undefined,
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
        <div className="flex items-center gap-3">
          <select className="select w-36" value={filtroStatus} onChange={e => setFiltroStatus(e.target.value as any)}>
            <option value="ATIVOS">Ativos</option>
            <option value="CANCELADOS">Cancelados</option>
          </select>
          <AcaoNova aoClicar={openCreate} rotulo="Novo ativo" />
        </div>
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

      {isLoading ? (
        <div className="flex justify-center py-16"><Spinner tamanho="lg" /></div>
      ) : (
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {ativosFiltrados.map(ativo => (
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
                  <button onClick={() => abrirModalAcao(ativo, 'cancelar')}
                    className="p-1.5 rounded-lg hover:bg-orange-900/40 text-conteudo-suave hover:text-orange-500 transition-colors">
                    <Ban size={14} />
                  </button>
                )}
                <button onClick={() => abrirModalAcao(ativo, 'excluir')}
                  className="p-1.5 rounded-lg hover:bg-red-900/40 text-conteudo-suave hover:text-red-400 transition-colors">
                  <Trash2 size={14} />
                </button>
              </div>
            </div>

            <p className="text-xl font-bold text-conteudo">{fmt(ativo.valorAtual)}</p>
            <p className="text-xs text-conteudo-suave">{ativo.percentualCarteira.toFixed(1)}% da carteira</p>

            {!!ativo.totalRendimento && ativo.totalRendimento !== 0 && (
              <div className="mt-2 space-y-0.5">
                <p className={`text-xs font-medium ${ativo.totalRendimento >= 0 ? 'text-emerald-500' : 'text-red-500'}`}>
                  {ativo.totalRendimento >= 0 ? '+' : ''}{fmt(ativo.totalRendimento)} ({(ativo.rentabilidadePercentual ?? 0).toFixed(2)}%)
                </p>
                {!ativo.isentoIr && !!ativo.irEstimado && ativo.irEstimado > 0 && (
                  <p className="text-[11px] text-conteudo-suave">
                    IR estimado · {fmt(ativo.irEstimado)} · líquido estimado {fmt(ativo.valorLiquidoEstimado ?? ativo.valorAtual)}
                  </p>
                )}
              </div>
            )}

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

        {ativosFiltrados.length === 0 && (
          <div className="card col-span-full text-center py-12 text-conteudo-suave">
            {filtroStatus === 'ATIVOS' ? 'Nenhum ativo cadastrado. Crie o primeiro!' : 'Nenhum ativo cancelado.'}
          </div>
        )}
      </div>
      )}

      <ModalConfirmacao
        aberto={modalAcao !== null}
        titulo={modalAcao?.tipo === 'cancelar' ? 'Cancelar ativo' : 'Excluir ativo'}
        aoFechar={() => !cancelarMutation.isPending && !deleteMutation.isPending && setModalAcao(null)}
        carregandoImpacto={modalAcao?.carregando}
        impacto={modalAcao?.impacto}
        botoes={modalAcao ? [
          modalAcao.tipo === 'cancelar'
            ? { rotulo: 'Cancelar ativo', variante: 'atencao' as const, aoClicar: () => cancelarMutation.mutate(modalAcao.item.id), carregando: cancelarMutation.isPending }
            : { rotulo: 'Excluir ativo', variante: 'perigo' as const, aoClicar: () => deleteMutation.mutate(modalAcao.item.id), carregando: deleteMutation.isPending },
        ] : []}
      >
        {modalAcao?.tipo === 'cancelar'
          ? `Deseja cancelar o ativo "${modalAcao?.item.nome}"?`
          : `Deseja excluir o ativo "${modalAcao?.item.nome}"? Esta ação não pode ser desfeita.`}
      </ModalConfirmacao>

      {showForm && (
        <SobreposicaoModal aoFechar={closeForm}>
          <div className="cartao-modal max-w-md">
            <div className="cartao-modal-cabecalho">
              <h2 className="text-lg font-semibold text-conteudo">{editing ? 'Editar Ativo' : 'Novo Ativo'}</h2>
              <button onClick={closeForm} className="btn-ghost p-1.5 text-sm">✕</button>
            </div>
            <form onSubmit={handleSubmit} className="cartao-modal-corpo">
              <CampoEntidade value={form.entidadeId} onChange={v => setForm(f => ({ ...f, entidadeId: v }))} />
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

              {!editing && (
                <div>
                  <label className="label">Valor inicial (opcional)</label>
                  <div className="flex gap-2">
                    <input className="input" type="number" step="0.01" min="0" placeholder="0,00"
                      value={form.valorInicial} onChange={e => setForm(f => ({ ...f, valorInicial: e.target.value }))} />
                    <input className="input" type="date" max={format(new Date(), 'yyyy-MM-dd')}
                      value={form.dataInicial} onChange={e => setForm(f => ({ ...f, dataInicial: e.target.value }))} />
                  </div>
                  <p className="text-xs text-conteudo-suave mt-1">
                    Já começa investido com esse valor — é debitado da conta vinculada como um aporte.
                  </p>
                </div>
              )}

              <div className="pt-1 border-t border-borda">
                <p className="text-xs font-semibold text-conteudo-suave mt-3 mb-2">Rendimento automático</p>
                <div className="space-y-3">
                  <div>
                    <label className="label">Indexador</label>
                    <select className="select" value={form.remuneracaoTipo}
                      onChange={e => setForm(f => ({ ...f, remuneracaoTipo: e.target.value as TipoRemuneracao }))}>
                      {TIPOS_REMUNERACAO.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}
                    </select>
                  </div>
                  {form.remuneracaoTipo !== 'NENHUMA' && (
                    <>
                      <div>
                        <label className="label">Taxa ({rotuloTaxa(form.remuneracaoTipo)})</label>
                        <input className="input" type="number" step="0.01" min="0" placeholder="0,00" required
                          value={form.taxa} onChange={e => setForm(f => ({ ...f, taxa: e.target.value }))} />
                      </div>
                      <div>
                        <label className="label">Início do rendimento</label>
                        <input className="input" type="date" max={format(new Date(), 'yyyy-MM-dd')}
                          value={form.inicioRendimento} onChange={e => setForm(f => ({ ...f, inicioRendimento: e.target.value }))} />
                        <p className="text-xs text-conteudo-suave mt-1">Se vazio, usa a data do primeiro aporte.</p>
                      </div>
                      <label className="flex items-center gap-2 text-sm text-conteudo">
                        <input type="checkbox" checked={form.isentoIr}
                          onChange={e => setForm(f => ({ ...f, isentoIr: e.target.checked }))} />
                        Isento de IR (LCI, LCA, poupança...)
                      </label>
                    </>
                  )}
                </div>
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
                  {movimento.ativo.contaId ? (
                    <p className="text-sm text-conteudo py-2 px-3 rounded-lg bg-superficie-2">{movimento.ativo.conta?.nome ?? '—'}</p>
                  ) : (
                    <select className="select" required
                      value={movForm.contaId} onChange={e => setMovForm(f => ({ ...f, contaId: e.target.value }))}>
                      <option value="">Selecione...</option>
                      {[...contas].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')).map(c => <option key={c.id} value={c.id}>{c.nome}</option>)}
                    </select>
                  )}
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
