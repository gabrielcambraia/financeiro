import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { format } from 'date-fns'
import { Pencil, Trash2, Ban, ArrowDownCircle, ArrowUpCircle, Target } from 'lucide-react'
import { buscarMetas, criarMeta, atualizarMeta, excluirMeta, cancelarMeta, aportarMeta, resgatarMeta, impactoCancelamentoMeta, impactoExclusaoMeta } from '../api/metas'
import { buscarContas } from '../api/contas'
import SobreposicaoModal from '../components/SobreposicaoModal'
import ModalConfirmacao from '../components/ModalConfirmacao'
import SeletorCor from '../components/SeletorCor'
import AcaoNova from '../components/AcaoNova'
import CampoEntidade from '../components/forms/CampoEntidade'
import type { Meta, RespostaImpacto } from '../types'

const fmt = (v: number) =>
  new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(v)

const CORES = ['#6366f1', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#06b6d4', '#ec4899', '#84cc16']
const formPadrao = { nome: '', valorAlvo: '', prazo: '', cor: CORES[0], icone: 'target', entidadeId: undefined as number | null | undefined }
const movimentoPadrao = { valor: '', contaId: '', data: format(new Date(), 'yyyy-MM-dd') }

export default function Metas() {
  const qc = useQueryClient()
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<Meta | null>(null)
  const [form, setForm] = useState(formPadrao)
  const [movimento, setMovimento] = useState<{ meta: Meta; tipo: 'aportar' | 'resgatar' } | null>(null)
  const [movForm, setMovForm] = useState(movimentoPadrao)
  const [modalAcao, setModalAcao] = useState<{
    tipo: 'cancelar' | 'excluir'; item: Meta; impacto: RespostaImpacto | null; carregando: boolean
  } | null>(null)

  const { data: metas = [] } = useQuery({ queryKey: ['metas'], queryFn: buscarMetas })
  const { data: contas = [] } = useQuery({ queryKey: ['contas'], queryFn: buscarContas })

  const invalidar = () => Promise.all([
    qc.invalidateQueries({ queryKey: ['metas'] }),
    qc.invalidateQueries({ queryKey: ['contas'] }),
    qc.invalidateQueries({ queryKey: ['transacoes'] }),
    qc.invalidateQueries({ queryKey: ['painel'] }),
  ])

  const saveMutation = useMutation({
    mutationFn: (data: { nome: string; valorAlvo: number; prazo?: string; cor: string; icone: string; entidadeId?: number | null }) =>
      editing ? atualizarMeta(editing.id, data) : criarMeta(data),
    onSuccess: async () => { await invalidar(); toast.success('Meta salva'); closeForm() },
  })

  const deleteMutation = useMutation({
    mutationFn: excluirMeta,
    onSuccess: async () => { await invalidar(); setModalAcao(null); toast.success('Meta excluída') },
  })

  const cancelarMutation = useMutation({
    mutationFn: cancelarMeta,
    onSuccess: async () => { await invalidar(); setModalAcao(null); toast.success('Meta cancelada') },
  })

  const movimentoMutation = useMutation({
    mutationFn: ({ id, tipo, dados }: { id: number; tipo: 'aportar' | 'resgatar'; dados: { valor: number; contaId: number; data?: string } }) =>
      tipo === 'aportar' ? aportarMeta(id, dados) : resgatarMeta(id, dados),
    onSuccess: async () => { await invalidar(); toast.success('Operação realizada'); closeMovimento() },
  })

  const openCreate = () => { setEditing(null); setForm(formPadrao); setShowForm(true) }
  const openEdit = (m: Meta) => {
    setEditing(m)
    setForm({ nome: m.nome, valorAlvo: String(m.valorAlvo), prazo: m.prazo ?? '', cor: m.cor, icone: m.icone, entidadeId: m.entidadeId })
    setShowForm(true)
  }
  const closeForm = () => { setShowForm(false); setEditing(null) }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    saveMutation.mutate({
      nome: form.nome, valorAlvo: Number(form.valorAlvo),
      prazo: form.prazo || undefined, cor: form.cor, icone: form.icone,
      entidadeId: form.entidadeId ?? null,
    })
  }

  const abrirModalAcao = async (meta: Meta, tipo: 'cancelar' | 'excluir') => {
    setModalAcao({ tipo, item: meta, impacto: null, carregando: true })
    try {
      const impacto = tipo === 'cancelar'
        ? await impactoCancelamentoMeta(meta.id)
        : await impactoExclusaoMeta(meta.id)
      setModalAcao({ tipo, item: meta, impacto, carregando: false })
    } catch {
      setModalAcao({ tipo, item: meta, impacto: null, carregando: false })
    }
  }

  const openMovimento = (meta: Meta, tipo: 'aportar' | 'resgatar') => {
    setMovimento({ meta, tipo })
    setMovForm({ ...movimentoPadrao, contaId: contas[0] ? String(contas[0].id) : '' })
  }
  const closeMovimento = () => setMovimento(null)

  const handleMovimentoSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!movimento) return
    movimentoMutation.mutate({
      id: movimento.meta.id,
      tipo: movimento.tipo,
      dados: { valor: Number(movForm.valor), contaId: Number(movForm.contaId), data: movForm.data },
    })
  }

  return (
    <div className="p-6 space-y-5">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-conteudo">Metas</h1>
        <AcaoNova aoClicar={openCreate} rotulo="Nova meta" />
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {metas.map(meta => (
          <div key={meta.id} className="card group">
            <div className="flex items-start justify-between mb-3">
              <div className="flex items-center gap-3 min-w-0">
                <div className="p-2.5 rounded-xl shrink-0" style={{ background: `${meta.cor}20` }}>
                  <Target size={20} style={{ color: meta.cor }} />
                </div>
                <div className="min-w-0">
                  <p className="font-semibold text-conteudo truncate">{meta.nome}</p>
                  {meta.dataCancelamento ? (
                    <p className="text-xs text-conteudo-suave">Cancelada</p>
                  ) : meta.concluida ? (
                    <p className="text-xs text-emerald-500">Concluída 🎉</p>
                  ) : meta.prazo ? (
                    <p className="text-xs text-conteudo-suave">Prazo: {format(new Date(meta.prazo + 'T00:00'), 'dd/MM/yyyy')}</p>
                  ) : meta.mesesEstimados ? (
                    <p className="text-xs text-conteudo-suave">~{meta.mesesEstimados} {meta.mesesEstimados === 1 ? 'mês' : 'meses'} pra concluir</p>
                  ) : null}
                </div>
              </div>
              <div className="flex gap-1 md:opacity-0 md:group-hover:opacity-100 transition-opacity shrink-0">
                <button onClick={() => openEdit(meta)} className="p-1.5 rounded-lg hover:bg-superficie-2 text-conteudo-suave hover:text-conteudo transition-colors">
                  <Pencil size={14} />
                </button>
                {!meta.dataCancelamento && (
                  <button onClick={() => abrirModalAcao(meta, 'cancelar')}
                    className="p-1.5 rounded-lg hover:bg-orange-900/40 text-conteudo-suave hover:text-orange-500 transition-colors">
                    <Ban size={14} />
                  </button>
                )}
                <button onClick={() => abrirModalAcao(meta, 'excluir')}
                  className="p-1.5 rounded-lg hover:bg-red-900/40 text-conteudo-suave hover:text-red-400 transition-colors">
                  <Trash2 size={14} />
                </button>
              </div>
            </div>

            <p className="text-xl font-bold text-conteudo">{fmt(meta.valorAtual)} <span className="text-sm font-normal text-conteudo-suave">de {fmt(meta.valorAlvo)}</span></p>
            <div className="h-2 rounded-full bg-superficie-2 overflow-hidden mt-2">
              <div className="h-full rounded-full transition-all" style={{ width: `${meta.percentualConcluido}%`, background: meta.cor }} />
            </div>

            {!meta.dataCancelamento && (
              <div className="flex gap-2 mt-3">
                <button onClick={() => openMovimento(meta, 'aportar')}
                  className="flex-1 flex items-center justify-center gap-1.5 py-2 rounded-lg border border-borda text-sm font-medium text-emerald-500 hover:bg-emerald-900/10 transition-colors">
                  <ArrowUpCircle size={14} /> Aportar
                </button>
                <button onClick={() => openMovimento(meta, 'resgatar')} disabled={meta.valorAtual <= 0}
                  className="flex-1 flex items-center justify-center gap-1.5 py-2 rounded-lg border border-borda text-sm font-medium text-amber-500 hover:bg-amber-900/10 transition-colors disabled:opacity-40 disabled:cursor-not-allowed">
                  <ArrowDownCircle size={14} /> Resgatar
                </button>
              </div>
            )}
          </div>
        ))}

        {metas.length === 0 && (
          <div className="card col-span-full text-center py-12 text-conteudo-suave">
            Nenhuma meta cadastrada. Crie a primeira!
          </div>
        )}
      </div>

      <ModalConfirmacao
        aberto={modalAcao !== null}
        titulo={modalAcao?.tipo === 'cancelar' ? 'Cancelar meta' : 'Excluir meta'}
        aoFechar={() => setModalAcao(null)}
        carregandoImpacto={modalAcao?.carregando}
        impacto={modalAcao?.impacto}
        botoes={modalAcao ? [
          modalAcao.tipo === 'cancelar'
            ? { rotulo: 'Cancelar meta', variante: 'atencao' as const, aoClicar: () => cancelarMutation.mutate(modalAcao.item.id), carregando: cancelarMutation.isPending }
            : { rotulo: 'Excluir meta', variante: 'perigo' as const, aoClicar: () => deleteMutation.mutate(modalAcao.item.id), carregando: deleteMutation.isPending },
        ] : []}
      >
        {modalAcao?.tipo === 'cancelar'
          ? `Deseja cancelar a meta "${modalAcao?.item.nome}"?`
          : `Deseja excluir a meta "${modalAcao?.item.nome}"? Esta ação não pode ser desfeita.`}
      </ModalConfirmacao>

      {showForm && (
        <SobreposicaoModal aoFechar={closeForm}>
          <div className="cartao-modal max-w-md">
            <div className="cartao-modal-cabecalho">
              <h2 className="text-lg font-semibold text-conteudo">{editing ? 'Editar Meta' : 'Nova Meta'}</h2>
              <button onClick={closeForm} className="btn-ghost p-1.5 text-sm">✕</button>
            </div>
            <form onSubmit={handleSubmit} className="cartao-modal-corpo">
              <CampoEntidade value={form.entidadeId} onChange={v => setForm(f => ({ ...f, entidadeId: v }))} />
              <div>
                <label className="label">Nome</label>
                <input className="input" placeholder="Ex: Reserva de emergência" required
                  value={form.nome} onChange={e => setForm(f => ({ ...f, nome: e.target.value }))} />
              </div>
              <div>
                <label className="label">Valor alvo (R$)</label>
                <input className="input" type="number" step="0.01" min="0.01" placeholder="0,00" required
                  value={form.valorAlvo} onChange={e => setForm(f => ({ ...f, valorAlvo: e.target.value }))} />
              </div>
              <div>
                <label className="label">Prazo (opcional)</label>
                <input className="input" type="date"
                  value={form.prazo} onChange={e => setForm(f => ({ ...f, prazo: e.target.value }))} />
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
              <h2 className="text-lg font-semibold text-conteudo">
                {movimento.tipo === 'aportar' ? 'Aportar em' : 'Resgatar de'} {movimento.meta.nome}
              </h2>
              <button onClick={closeMovimento} className="btn-ghost p-1.5 text-sm">✕</button>
            </div>
            <form onSubmit={handleMovimentoSubmit} className="cartao-modal-corpo">
              <div>
                <label className="label">{movimento.tipo === 'aportar' ? 'Conta de origem' : 'Conta de destino'}</label>
                <select className="select" required
                  value={movForm.contaId} onChange={e => setMovForm(f => ({ ...f, contaId: e.target.value }))}>
                  <option value="">Selecione...</option>
                  {[...contas].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')).map(c => <option key={c.id} value={c.id}>{c.nome}</option>)}
                </select>
              </div>
              <div>
                <label className="label">Valor (R$)</label>
                <input className="input" type="number" step="0.01" min="0.01"
                  max={movimento.tipo === 'resgatar' ? movimento.meta.valorAtual : undefined}
                  placeholder="0,00" required
                  value={movForm.valor} onChange={e => setMovForm(f => ({ ...f, valor: e.target.value }))} />
                {movimento.tipo === 'resgatar' && (
                  <p className="text-xs text-conteudo-suave mt-1">Disponível: {fmt(movimento.meta.valorAtual)}</p>
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
