import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Pencil, Trash2 } from 'lucide-react'
import { buscarOrcamentos, criarOrcamento, atualizarOrcamento, excluirOrcamento } from '../api/orcamentos'
import { buscarCategorias } from '../api/categorias'
import { useLojaFiltro } from '../store/lojaFiltro'
import SeletorMes from '../components/SeletorMes'
import SobreposicaoModal from '../components/SobreposicaoModal'
import ModalConfirmacao from '../components/ModalConfirmacao'
import AcaoNova from '../components/AcaoNova'
import CampoEntidade from '../components/forms/CampoEntidade'
import Spinner from '../components/Spinner'
import type { Orcamento } from '../types'

const fmt = (v: number) =>
  new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(v)

const formPadrao = { categoriaId: '', limite: '', entidadeId: undefined as number | null | undefined }

function corBarra(percentual: number) {
  if (percentual >= 100) return 'bg-red-500'
  if (percentual >= 80) return 'bg-amber-500'
  return 'bg-emerald-500'
}

export default function Orcamentos() {
  const qc = useQueryClient()
  const { mes } = useLojaFiltro()
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<Orcamento | null>(null)
  const [form, setForm] = useState(formPadrao)
  const [confirmarExcluir, setConfirmarExcluir] = useState<Orcamento | null>(null)

  const { data: orcamentos = [], isLoading } = useQuery({ queryKey: ['orcamentos', mes], queryFn: () => buscarOrcamentos(mes) })
  const { data: categorias = [] } = useQuery({ queryKey: ['categorias', 'DESPESA'], queryFn: () => buscarCategorias('DESPESA') })

  const saveMutation = useMutation({
    mutationFn: (data: { categoriaId: number; mes: string; limite: number; entidadeId?: number | null }) =>
      editing ? atualizarOrcamento(editing.id, data) : criarOrcamento(data),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ['orcamentos'] })
      toast.success('Orçamento salvo')
      closeForm()
    },
  })

  const deleteMutation = useMutation({
    mutationFn: excluirOrcamento,
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ['orcamentos'] })
      setConfirmarExcluir(null)
      toast.success('Orçamento excluído')
    },
  })

  const categoriasDisponiveis = categorias.filter(c =>
    editing?.categoriaId === c.id || !orcamentos.some(o => o.categoriaId === c.id))

  const openCreate = () => { setEditing(null); setForm(formPadrao); setShowForm(true) }
  const openEdit = (o: Orcamento) => {
    setEditing(o)
    setForm({ categoriaId: String(o.categoriaId), limite: String(o.limite), entidadeId: o.entidadeId })
    setShowForm(true)
  }
  const closeForm = () => { setShowForm(false); setEditing(null) }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    saveMutation.mutate({ categoriaId: Number(form.categoriaId), mes, limite: Number(form.limite), entidadeId: form.entidadeId ?? null })
  }

  const totalLimite = orcamentos.reduce((s, o) => s + o.limite, 0)
  const totalGasto = orcamentos.reduce((s, o) => s + o.gasto, 0)

  return (
    <div className="p-6 space-y-5">
      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <h1 className="text-2xl font-bold text-conteudo">Orçamento</h1>
        <div className="flex items-center gap-3 md:w-auto">
          <SeletorMes />
          <AcaoNova aoClicar={openCreate} rotulo="Novo orçamento" />
        </div>
      </div>

      {orcamentos.length > 0 && (
        <div className="card">
          <div className="flex items-center justify-between mb-2">
            <p className="text-sm text-conteudo-suave">Total do mês</p>
            <p className="text-sm font-semibold text-conteudo">{fmt(totalGasto)} de {fmt(totalLimite)}</p>
          </div>
          <div className="h-2 rounded-full bg-superficie-2 overflow-hidden">
            <div
              className={`h-full rounded-full transition-all ${totalGasto > totalLimite ? 'bg-red-500' : corBarra(totalLimite ? (totalGasto / totalLimite) * 100 : 0)}`}
              style={{ width: `${Math.min(100, totalLimite ? (totalGasto / totalLimite) * 100 : 0)}%` }}
            />
          </div>
        </div>
      )}

      <div className="space-y-3">
        {orcamentos.map(o => (
          <div key={o.id} className="card group">
            <div className="flex items-center justify-between mb-2">
              <div className="flex items-center gap-2">
                <span className="w-2.5 h-2.5 rounded-full" style={{ background: o.categoria.cor }} />
                <span className="font-medium text-conteudo">{o.categoria.nome}</span>
              </div>
              <div className="flex items-center gap-3">
                <span className="text-sm text-conteudo-suave">
                  <span className={o.estourado ? 'text-red-500 font-semibold' : 'text-conteudo font-semibold'}>{fmt(o.gasto)}</span> de {fmt(o.limite)}
                </span>
                <div className="flex gap-1 md:opacity-0 md:group-hover:opacity-100 transition-opacity">
                  <button onClick={() => openEdit(o)} className="p-1.5 rounded-lg hover:bg-superficie-2 text-conteudo-suave hover:text-conteudo transition-colors">
                    <Pencil size={14} />
                  </button>
                  <button onClick={() => setConfirmarExcluir(o)}
                    className="p-1.5 rounded-lg hover:bg-red-900/40 text-conteudo-suave hover:text-red-400 transition-colors">
                    <Trash2 size={14} />
                  </button>
                </div>
              </div>
            </div>
            <div className="h-2 rounded-full bg-superficie-2 overflow-hidden">
              <div
                className={`h-full rounded-full transition-all ${corBarra(o.percentualUsado)}`}
                style={{ width: `${Math.min(100, o.percentualUsado)}%` }}
              />
            </div>
            {o.estourado && (
              <p className="text-xs text-red-500 mt-1.5">Orçamento estourado</p>
            )}
          </div>
        ))}

        {isLoading && (
          <div className="flex justify-center py-12"><Spinner /></div>
        )}
        {!isLoading && orcamentos.length === 0 && (
          <div className="card text-center py-12 text-conteudo-suave">
            Nenhum orçamento definido para este mês.
          </div>
        )}
      </div>

      <ModalConfirmacao
        aberto={confirmarExcluir !== null}
        titulo="Excluir orçamento"
        aoFechar={() => !deleteMutation.isPending && setConfirmarExcluir(null)}
        botoes={[{ rotulo: 'Excluir', variante: 'perigo', aoClicar: () => deleteMutation.mutate(confirmarExcluir!.id), carregando: deleteMutation.isPending }]}
      >
        {`Deseja excluir o orçamento de "${confirmarExcluir?.categoria?.nome}"?`}
      </ModalConfirmacao>

      {showForm && (
        <SobreposicaoModal aoFechar={closeForm}>
          <div className="cartao-modal max-w-md">
            <div className="cartao-modal-cabecalho">
              <h2 className="text-lg font-semibold text-conteudo">{editing ? 'Editar Orçamento' : 'Novo Orçamento'}</h2>
              <button onClick={closeForm} className="btn-ghost p-1.5 text-sm">✕</button>
            </div>
            <form onSubmit={handleSubmit} className="cartao-modal-corpo">
              <CampoEntidade value={form.entidadeId} onChange={v => setForm(f => ({ ...f, entidadeId: v }))} />
              <div>
                <label className="label">Categoria</label>
                <select className="select" required
                  value={form.categoriaId} onChange={e => setForm(f => ({ ...f, categoriaId: e.target.value }))}>
                  <option value="">Selecione...</option>
                  {[...categoriasDisponiveis].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')).map(c => (
                    <option key={c.id} value={c.id}>{c.nome}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="label">Limite mensal (R$)</label>
                <input className="input" type="number" step="0.01" min="0.01" placeholder="0,00" required
                  value={form.limite} onChange={e => setForm(f => ({ ...f, limite: e.target.value }))} />
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
