import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { format, parseISO } from 'date-fns'
import { Ban, Trash2, ChevronDown, ChevronUp, HandCoins } from 'lucide-react'
import { buscarDividas, buscarParcelasDivida, criarDivida, cancelarDivida, excluirDivida, impactoCancelamentoDivida, impactoExclusaoDivida } from '../api/dividas'
import { buscarContas } from '../api/contas'
import { buscarCategorias } from '../api/categorias'
import { pagarTransacao, estornarTransacao } from '../api/transacoes'
import SobreposicaoModal from '../components/SobreposicaoModal'
import ModalConfirmacao from '../components/ModalConfirmacao'
import AcaoNova from '../components/AcaoNova'
import CampoEntidade from '../components/forms/CampoEntidade'
import Spinner from '../components/Spinner'
import type { Divida, StatusTransacao, StatusDivida, RespostaImpacto } from '../types'

const fmt = (v: number) =>
  new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(v)

const rotuloStatus: Record<StatusDivida, string> = {
  EM_DIA: 'Em dia', ATRASADA: 'Atrasada', QUITADA: 'Quitada', CANCELADA: 'Cancelada',
}
const corStatus: Record<StatusDivida, string> = {
  EM_DIA: 'bg-emerald-500/15 text-emerald-500',
  ATRASADA: 'bg-red-500/15 text-red-500',
  QUITADA: 'bg-blue-500/15 text-blue-500',
  CANCELADA: 'bg-gray-500/15 text-gray-400 line-through',
}
const corStatusTx: Record<StatusTransacao, string> = {
  PAGA: 'bg-emerald-500/15 text-emerald-500',
  PENDENTE: 'bg-amber-500/15 text-amber-500',
  ATRASADA: 'bg-red-500/15 text-red-500',
  CANCELADA: 'bg-gray-500/15 text-gray-400 line-through',
}

const formPadrao = {
  descricao: '', credor: '', valorTotal: '', totalParcelas: '2', contaId: '', categoriaId: '',
  dataInicio: format(new Date(), 'yyyy-MM-dd'), entidadeId: undefined as number | null | undefined,
}

export default function Dividas() {
  const qc = useQueryClient()
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState(formPadrao)
  const [expandida, setExpandida] = useState<number | null>(null)
  const [modalAcao, setModalAcao] = useState<{
    tipo: 'cancelar' | 'excluir'; item: Divida; impacto: RespostaImpacto | null; carregando: boolean
  } | null>(null)

  const { data: dividas = [], isLoading } = useQuery({ queryKey: ['dividas'], queryFn: buscarDividas })
  const { data: contas = [] } = useQuery({ queryKey: ['contas'], queryFn: buscarContas })
  const { data: categorias = [] } = useQuery({ queryKey: ['categorias', 'DESPESA'], queryFn: () => buscarCategorias('DESPESA') })
  const { data: parcelas = [] } = useQuery({
    queryKey: ['dividaParcelas', expandida],
    queryFn: () => buscarParcelasDivida(expandida as number),
    enabled: expandida !== null,
  })

  const invalidar = () => Promise.all([
    qc.invalidateQueries({ queryKey: ['dividas'] }),
    qc.invalidateQueries({ queryKey: ['dividaParcelas'] }),
    qc.invalidateQueries({ queryKey: ['contas'] }),
    qc.invalidateQueries({ queryKey: ['transacoes'] }),
    qc.invalidateQueries({ queryKey: ['painel'] }),
  ])

  const saveMutation = useMutation({
    mutationFn: criarDivida,
    onSuccess: async () => { await invalidar(); toast.success('Dívida criada'); closeForm() },
  })
  const cancelarMutation = useMutation({
    mutationFn: cancelarDivida,
    onSuccess: async () => { await invalidar(); setModalAcao(null); toast.success('Dívida cancelada') },
  })
  const deleteMutation = useMutation({
    mutationFn: excluirDivida,
    onSuccess: async () => { await invalidar(); setModalAcao(null); toast.success('Dívida excluída') },
  })
  const pagarMutation = useMutation({
    mutationFn: (id: number) => pagarTransacao(id),
    onSuccess: async () => { await invalidar(); toast.success('Parcela paga') },
  })
  const estornarMutation = useMutation({
    mutationFn: (id: number) => estornarTransacao(id),
    onSuccess: async () => { await invalidar(); toast.success('Pagamento estornado') },
  })

  const abrirModalAcao = async (divida: Divida, tipo: 'cancelar' | 'excluir') => {
    setModalAcao({ tipo, item: divida, impacto: null, carregando: true })
    try {
      const impacto = tipo === 'cancelar'
        ? await impactoCancelamentoDivida(divida.id)
        : await impactoExclusaoDivida(divida.id)
      setModalAcao({ tipo, item: divida, impacto, carregando: false })
    } catch {
      setModalAcao({ tipo, item: divida, impacto: null, carregando: false })
    }
  }

  const closeForm = () => { setShowForm(false); setForm(formPadrao) }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    saveMutation.mutate({
      descricao: form.descricao,
      credor: form.credor || undefined,
      valorTotal: Number(form.valorTotal),
      totalParcelas: Number(form.totalParcelas),
      contaId: Number(form.contaId),
      categoriaId: form.categoriaId ? Number(form.categoriaId) : undefined,
      dataInicio: form.dataInicio,
      entidadeId: form.entidadeId ?? null,
    })
  }

  return (
    <div className="p-6 space-y-5">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-conteudo">Dívidas</h1>
        <AcaoNova aoClicar={() => setShowForm(true)} rotulo="Nova dívida" />
      </div>

      {isLoading ? (
        <div className="flex justify-center py-16"><Spinner tamanho="lg" /></div>
      ) : (
      <div className="space-y-3">
        {dividas.map((d: Divida) => {
          const expandido = expandida === d.id
          return (
            <div key={d.id} className="card p-0 overflow-hidden">
              <button onClick={() => setExpandida(expandido ? null : d.id)}
                className="w-full flex items-center gap-3 px-5 py-4 hover:bg-superficie-2 transition-colors text-left">
                <div className="p-2 rounded-xl bg-superficie-2 shrink-0">
                  <HandCoins size={18} className="text-conteudo-suave" />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="font-medium text-conteudo truncate">{d.descricao}</span>
                    <span className={`text-xs px-1.5 py-0.5 rounded-full font-medium ${corStatus[d.status]}`}>
                      {rotuloStatus[d.status]}
                    </span>
                  </div>
                  <p className="text-xs text-conteudo-suave mt-0.5">
                    {d.credor ? `${d.credor} · ` : ''}{d.parcelasPagas}/{d.totalParcelas} parcelas
                    {d.proximaParcelaData && ` · próxima em ${format(parseISO(d.proximaParcelaData), 'dd/MM/yyyy')}`}
                  </p>
                  <div className="h-1.5 rounded-full bg-superficie-2 overflow-hidden mt-2 max-w-xs">
                    <div className="h-full rounded-full bg-acento transition-all" style={{ width: `${(d.parcelasPagas / d.totalParcelas) * 100}%` }} />
                  </div>
                </div>
                <div className="text-right shrink-0">
                  <p className="text-sm font-bold text-conteudo">{fmt(d.valorRestante)}</p>
                  <p className="text-xs text-conteudo-suave">de {fmt(d.valorTotal)}</p>
                </div>
                {expandido ? <ChevronUp size={16} className="text-conteudo-suave shrink-0" /> : <ChevronDown size={16} className="text-conteudo-suave shrink-0" />}
              </button>

              {expandido && (
                <div className="border-t border-borda">
                  {d.status !== 'CANCELADA' && (
                    <div className="px-5 py-2.5 border-b border-borda flex gap-2">
                      <button onClick={() => abrirModalAcao(d, 'cancelar')}
                        className="flex items-center gap-1.5 py-1.5 px-3 rounded-lg border border-orange-800 text-orange-500 hover:bg-orange-900/20 transition-colors text-sm font-medium">
                        <Ban size={14} /> Cancelar dívida
                      </button>
                      {d.parcelasPagas === 0 && (
                        <button onClick={() => abrirModalAcao(d, 'excluir')}
                          className="flex items-center gap-1.5 py-1.5 px-3 rounded-lg border border-red-800 text-red-500 hover:bg-red-900/20 transition-colors text-sm font-medium">
                          <Trash2 size={14} /> Excluir
                        </button>
                      )}
                    </div>
                  )}
                  <div className="divide-y divide-borda">
                    {expandida === d.id && parcelas.map(p => (
                      <div key={p.id} className="flex items-center gap-4 px-5 py-3">
                        <span className="text-xs text-conteudo-suave w-14 shrink-0">{p.numeroParcela}/{p.totalParcelas}</span>
                        <div className="flex-1 min-w-0">
                          <span className={`text-xs px-1.5 py-0.5 rounded-full font-medium ${corStatusTx[p.status]}`}>
                            {p.status === 'PAGA' ? 'Paga' : p.status === 'ATRASADA' ? 'Atrasada' : p.status === 'CANCELADA' ? 'Cancelada' : 'Pendente'}
                          </span>
                          <span className="text-xs text-conteudo-suave ml-2">
                            vence {format(parseISO(p.dataVencimento ?? p.data), 'dd/MM/yyyy')}
                          </span>
                        </div>
                        <span className="text-sm font-semibold text-conteudo">{fmt(p.valor)}</span>
                        {(p.status === 'PENDENTE' || p.status === 'ATRASADA') && (
                          <button onClick={() => pagarMutation.mutate(p.id)}
                            className="text-xs py-1 px-2 rounded-lg border border-emerald-800 text-emerald-500 hover:bg-emerald-900/20 transition-colors">
                            Pagar
                          </button>
                        )}
                        {p.status === 'PAGA' && (
                          <button onClick={() => estornarMutation.mutate(p.id)}
                            className="text-xs py-1 px-2 rounded-lg border border-amber-800 text-amber-500 hover:bg-amber-900/20 transition-colors">
                            Estornar
                          </button>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )
        })}

        {dividas.length === 0 && (
          <div className="card text-center py-12 text-conteudo-suave">
            Nenhuma dívida cadastrada.
          </div>
        )}
      </div>
      )}

      <ModalConfirmacao
        aberto={modalAcao !== null}
        titulo={modalAcao?.tipo === 'cancelar' ? 'Cancelar dívida' : 'Excluir dívida'}
        aoFechar={() => setModalAcao(null)}
        carregandoImpacto={modalAcao?.carregando}
        impacto={modalAcao?.impacto}
        botoes={modalAcao ? [
          modalAcao.tipo === 'cancelar'
            ? { rotulo: 'Cancelar dívida', variante: 'atencao' as const, aoClicar: () => cancelarMutation.mutate(modalAcao.item.id), carregando: cancelarMutation.isPending }
            : { rotulo: 'Excluir dívida', variante: 'perigo' as const, aoClicar: () => deleteMutation.mutate(modalAcao.item.id), carregando: deleteMutation.isPending },
        ] : []}
      >
        {modalAcao?.tipo === 'cancelar'
          ? `Deseja cancelar a dívida "${modalAcao?.item.descricao}"? As parcelas pagas serão revertidas.`
          : `Deseja excluir a dívida "${modalAcao?.item.descricao}" e todas as suas parcelas?`}
      </ModalConfirmacao>

      {showForm && (
        <SobreposicaoModal aoFechar={closeForm}>
          <div className="cartao-modal max-w-md">
            <div className="cartao-modal-cabecalho">
              <h2 className="text-lg font-semibold text-conteudo">Nova Dívida</h2>
              <button onClick={closeForm} className="btn-ghost p-1.5 text-sm">✕</button>
            </div>
            <form onSubmit={handleSubmit} className="cartao-modal-corpo">
              <CampoEntidade value={form.entidadeId} onChange={v => setForm(f => ({ ...f, entidadeId: v }))} />
              <div>
                <label className="label">Descrição</label>
                <input className="input" placeholder="Ex: Acordo cartão antigo" required
                  value={form.descricao} onChange={e => setForm(f => ({ ...f, descricao: e.target.value }))} />
              </div>
              <div>
                <label className="label">Credor (opcional)</label>
                <input className="input" placeholder="Ex: Banco X"
                  value={form.credor} onChange={e => setForm(f => ({ ...f, credor: e.target.value }))} />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="label">Valor total (R$)</label>
                  <input className="input" type="number" step="0.01" min="0.01" required
                    value={form.valorTotal} onChange={e => setForm(f => ({ ...f, valorTotal: e.target.value }))} />
                </div>
                <div>
                  <label className="label">Parcelas</label>
                  <input className="input" type="number" min="1" max="360" required
                    value={form.totalParcelas} onChange={e => setForm(f => ({ ...f, totalParcelas: e.target.value }))} />
                </div>
              </div>
              <div>
                <label className="label">Conta de pagamento</label>
                <select className="select" required
                  value={form.contaId} onChange={e => setForm(f => ({ ...f, contaId: e.target.value }))}>
                  <option value="">Selecione...</option>
                  {[...contas].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')).map(c => <option key={c.id} value={c.id}>{c.nome}</option>)}
                </select>
              </div>
              <div>
                <label className="label">Categoria (opcional)</label>
                <select className="select"
                  value={form.categoriaId} onChange={e => setForm(f => ({ ...f, categoriaId: e.target.value }))}>
                  <option value="">Sem categoria</option>
                  {[...categorias].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')).map(c => <option key={c.id} value={c.id}>{c.nome}</option>)}
                </select>
              </div>
              <div>
                <label className="label">Data da 1ª parcela</label>
                <input className="input" type="date" required
                  value={form.dataInicio} onChange={e => setForm(f => ({ ...f, dataInicio: e.target.value }))} />
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
