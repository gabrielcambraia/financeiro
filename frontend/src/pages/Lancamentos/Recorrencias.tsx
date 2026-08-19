import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Plus, Pencil, Trash2, RefreshCw } from 'lucide-react'
import {
  buscarRecorrencias, excluirRecorrencia, toggleAtivaRecorrencia, gerarMesAtualRecorrencia
} from '../../api/recorrencias'
import FormularioRecorrencia from '../../components/forms/FormularioRecorrencia'
import SobreposicaoModal from '../../components/SobreposicaoModal'
import Spinner from '../../components/Spinner'
import type { Recorrencia, TipoPagamento, TipoTransacao } from '../../types'

const fmt = (v: number) =>
  new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(v)

const rotuloTipo: Record<TipoTransacao, string> = {
  DESPESA: 'Despesa', RECEITA: 'Receita', TRANSFERENCIA: 'Transferência',
}
const rotuloTipoPagamento: Record<TipoPagamento, string> = {
  DEBITO: 'Débito', CREDITO: 'Crédito',
}

export default function Recorrencias() {
  const qc = useQueryClient()
  const [filtroAtiva, setFiltroAtiva] = useState<'todas' | 'ativas' | 'inativas'>('ativas')
  const [filtroTipo, setFiltroTipo] = useState<TipoTransacao | ''>('')
  const [showForm, setShowForm] = useState(false)
  const [editando, setEditando] = useState<Recorrencia | undefined>()
  const [excluindo, setExcluindo] = useState<Recorrencia | null>(null)

  const { data: todas = [], isLoading } = useQuery({
    queryKey: ['recorrencias'],
    queryFn: () => buscarRecorrencias(),
  })

  const recorrencias = todas.filter(r => {
    if (filtroAtiva === 'ativas' && !r.ativa) return false
    if (filtroAtiva === 'inativas' && r.ativa) return false
    if (filtroTipo && r.tipo !== filtroTipo) return false
    return true
  })

  const invalidar = () => qc.invalidateQueries({ queryKey: ['recorrencias'] })

  const toggleMutation = useMutation({
    mutationFn: ({ id, ativa }: { id: number; ativa: boolean }) => toggleAtivaRecorrencia(id, ativa),
    onSuccess: async () => { await invalidar(); toast.success('Status atualizado') },
    onError: () => toast.error('Erro ao atualizar status'),
  })

  const excluirMutation = useMutation({
    mutationFn: (id: number) => excluirRecorrencia(id),
    onSuccess: async () => { await invalidar(); setExcluindo(null); toast.success('Recorrência excluída') },
    onError: () => toast.error('Erro ao excluir recorrência'),
  })

  const gerarMutation = useMutation({
    mutationFn: (id: number) => gerarMesAtualRecorrencia(id),
    onSuccess: async () => {
      await Promise.all([
        invalidar(),
        qc.invalidateQueries({ queryKey: ['transacoes'] }),
        qc.invalidateQueries({ queryKey: ['itensFatura'] }),
      ])
      toast.success('Geração do mês atual concluída')
    },
    onError: (e: unknown) => {
      const msg = (e as { response?: { data?: { mensagem?: string } } })?.response?.data?.mensagem
      toast.error(msg ?? 'Erro ao gerar lançamento')
    },
  })

  const chipBase = 'text-xs px-3 py-1.5 rounded-full font-medium transition-colors cursor-pointer'
  const chipAtivo = 'bg-acento text-white border border-acento'
  const chipInativo = 'border border-borda text-conteudo-suave hover:border-acento hover:text-acento bg-superficie'

  return (
    <div className="p-6 space-y-5">
      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-conteudo">Recorrências</h1>
          <p className="text-sm text-conteudo-suave mt-0.5">Lançamentos gerados automaticamente todo mês</p>
        </div>
        <button
          className="btn-primary text-sm py-2 px-4 flex items-center gap-2 self-start md:self-auto"
          onClick={() => { setEditando(undefined); setShowForm(true) }}>
          <Plus size={16} />
          Nova recorrência
        </button>
      </div>

      {/* Filtros */}
      <div className="card p-3 flex flex-wrap gap-2 items-center">
        <button className={`${chipBase} ${filtroAtiva === 'ativas' ? chipAtivo : chipInativo}`}
          onClick={() => setFiltroAtiva('ativas')}>Ativas</button>
        <button className={`${chipBase} ${filtroAtiva === 'todas' ? chipAtivo : chipInativo}`}
          onClick={() => setFiltroAtiva('todas')}>Todas</button>
        <button className={`${chipBase} ${filtroAtiva === 'inativas' ? chipAtivo : chipInativo}`}
          onClick={() => setFiltroAtiva('inativas')}>Inativas</button>
        <div className="w-px h-5 bg-borda shrink-0" />
        <button className={`${chipBase} ${filtroTipo === '' ? chipAtivo : chipInativo}`}
          onClick={() => setFiltroTipo('')}>Todos</button>
        <button className={`${chipBase} ${filtroTipo === 'DESPESA' ? chipAtivo : chipInativo}`}
          onClick={() => setFiltroTipo('DESPESA')}>Despesas</button>
        <button className={`${chipBase} ${filtroTipo === 'RECEITA' ? chipAtivo : chipInativo}`}
          onClick={() => setFiltroTipo('RECEITA')}>Receitas</button>
      </div>

      {isLoading ? (
        <div className="flex justify-center py-12"><Spinner /></div>
      ) : recorrencias.length === 0 ? (
        <div className="card text-center py-12 text-conteudo-suave">
          <p>
            {todas.length === 0
              ? 'Nenhuma recorrência cadastrada.'
              : 'Nenhuma recorrência encontrada com os filtros selecionados.'}
          </p>
          {todas.length === 0 && (
            <button onClick={() => setShowForm(true)} className="mt-3 text-acento hover:opacity-80 text-sm">
              + Criar primeira recorrência
            </button>
          )}
        </div>
      ) : (
        <>
          {/* Desktop */}
          <div className="hidden md:block card p-0 overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-sm border-collapse">
                <thead>
                  <tr className="border-b border-borda">
                    <th className="px-4 py-3 text-left text-xs font-semibold text-conteudo-suave uppercase tracking-wide">Descrição</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-conteudo-suave uppercase tracking-wide">Tipo</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-conteudo-suave uppercase tracking-wide">Conta / Cartão</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-conteudo-suave uppercase tracking-wide">Dia</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-conteudo-suave uppercase tracking-wide">Próxima geração</th>
                    <th className="px-4 py-3 text-right text-xs font-semibold text-conteudo-suave uppercase tracking-wide">Valor</th>
                    <th className="px-4 py-3 text-center text-xs font-semibold text-conteudo-suave uppercase tracking-wide">Ativa</th>
                    <th className="px-4 py-3 w-[80px]" />
                  </tr>
                </thead>
                <tbody>
                  {recorrencias.map(r => (
                    <tr key={r.id} className={`group border-b border-borda last:border-b-0 hover:bg-superficie-2 transition-colors ${!r.ativa ? 'opacity-50' : ''}`}>
                      <td className="px-4 py-3">
                        <div className="font-medium text-conteudo">{r.descricao || '—'}</div>
                        {r.categoriaNome && (
                          <div className="text-xs text-conteudo-suave mt-0.5 flex items-center gap-1.5">
                            {r.categoriaCor && <div className="w-2 h-2 rounded-full shrink-0" style={{ background: r.categoriaCor }} />}
                            {r.categoriaNome}
                          </div>
                        )}
                      </td>
                      <td className="px-4 py-3">
                        <span className={`text-xs px-1.5 py-0.5 rounded-full font-medium ${r.tipo === 'DESPESA' ? 'bg-red-500/15 text-red-400' : 'bg-emerald-500/15 text-emerald-400'}`}>
                          {rotuloTipo[r.tipo]}
                        </span>
                        <div className="text-xs text-conteudo-suave mt-0.5">{rotuloTipoPagamento[r.tipoPagamento]}</div>
                      </td>
                      <td className="px-4 py-3 text-sm text-conteudo-suave">
                        {r.contaNome ?? r.cartaoNome ?? '—'}
                      </td>
                      <td className="px-4 py-3 text-sm text-conteudo-suave">
                        Dia {r.diaCompetencia}
                        {r.diaVencimento && r.diaVencimento !== r.diaCompetencia && (
                          <div className="text-xs text-conteudo-suave">Vence dia {r.diaVencimento}</div>
                        )}
                      </td>
                      <td className="px-4 py-3 text-sm text-conteudo-suave">
                        {r.proximaGeracaoMes ?? '—'}
                      </td>
                      <td className="px-4 py-3 text-right">
                        <span className={`font-bold whitespace-nowrap ${r.tipo === 'DESPESA' ? 'text-red-500' : 'text-emerald-500'}`}>
                          {r.tipo === 'DESPESA' ? '-' : '+'}{fmt(r.valor)}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-center">
                        <button
                          onClick={() => toggleMutation.mutate({ id: r.id, ativa: !r.ativa })}
                          disabled={toggleMutation.isPending}
                          className={`relative inline-flex h-5 w-9 items-center rounded-full transition-colors ${r.ativa ? 'bg-acento' : 'bg-borda'}`}
                          aria-label={r.ativa ? 'Desativar' : 'Ativar'}
                        >
                          <span className={`inline-block h-3 w-3 rounded-full bg-white transition-transform ${r.ativa ? 'translate-x-5' : 'translate-x-1'}`} />
                        </button>
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex gap-0.5 justify-end opacity-0 group-hover:opacity-100 transition-opacity">
                          <button onClick={() => gerarMutation.mutate(r.id)} title="Gerar mês atual"
                            disabled={gerarMutation.isPending}
                            className="p-1.5 rounded-lg hover:bg-superficie-2 text-conteudo-suave hover:text-acento transition-colors">
                            <RefreshCw size={13} />
                          </button>
                          <button onClick={() => { setEditando(r); setShowForm(true) }} title="Editar"
                            className="p-1.5 rounded-lg hover:bg-superficie-2 text-conteudo-suave hover:text-conteudo transition-colors">
                            <Pencil size={13} />
                          </button>
                          <button onClick={() => setExcluindo(r)} title="Excluir"
                            className="p-1.5 rounded-lg hover:bg-red-900/40 text-conteudo-suave hover:text-red-500 transition-colors">
                            <Trash2 size={13} />
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          {/* Mobile */}
          <div className="md:hidden space-y-2">
            {recorrencias.map(r => (
              <div key={r.id} className={`card px-4 py-3 flex items-start gap-3 ${!r.ativa ? 'opacity-50' : ''}`}>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="font-medium text-conteudo text-sm">{r.descricao || '—'}</span>
                    <span className={`text-xs px-1.5 py-0.5 rounded-full font-medium ${r.tipo === 'DESPESA' ? 'bg-red-500/15 text-red-400' : 'bg-emerald-500/15 text-emerald-400'}`}>
                      {rotuloTipo[r.tipo]}
                    </span>
                  </div>
                  <div className="text-xs text-conteudo-suave mt-0.5">
                    Dia {r.diaCompetencia} · {r.contaNome ?? r.cartaoNome ?? '—'}
                    {r.categoriaNome ? ` · ${r.categoriaNome}` : ''}
                  </div>
                </div>
                <div className="text-right shrink-0">
                  <div className={`font-bold text-sm ${r.tipo === 'DESPESA' ? 'text-red-500' : 'text-emerald-500'}`}>
                    {r.tipo === 'DESPESA' ? '-' : '+'}{fmt(r.valor)}
                  </div>
                  <div className="flex items-center justify-end gap-2 mt-1">
                    <button onClick={() => toggleMutation.mutate({ id: r.id, ativa: !r.ativa })}
                      className={`relative inline-flex h-5 w-9 items-center rounded-full transition-colors ${r.ativa ? 'bg-acento' : 'bg-borda'}`}>
                      <span className={`inline-block h-3 w-3 rounded-full bg-white transition-transform ${r.ativa ? 'translate-x-5' : 'translate-x-1'}`} />
                    </button>
                    <button onClick={() => { setEditando(r); setShowForm(true) }}
                      className="p-1 rounded text-conteudo-suave hover:text-conteudo">
                      <Pencil size={14} />
                    </button>
                    <button onClick={() => setExcluindo(r)}
                      className="p-1 rounded text-conteudo-suave hover:text-red-500">
                      <Trash2 size={14} />
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </>
      )}

      {showForm && (
        <FormularioRecorrencia
          onClose={() => { setShowForm(false); setEditando(undefined) }}
          editing={editando}
        />
      )}

      {excluindo && (
        <SobreposicaoModal aoFechar={() => setExcluindo(null)}>
          <div className="cartao-modal max-w-sm">
            <div className="cartao-modal-cabecalho">
              <h3 className="text-base font-semibold text-conteudo">Excluir recorrência</h3>
            </div>
            <div className="cartao-modal-corpo space-y-4">
              <p className="text-sm text-conteudo-suave">
                Excluir <strong className="text-conteudo">{excluindo.descricao || 'esta recorrência'}</strong>?
                Os lançamentos já gerados continuarão no histórico (a FK é mantida, mas a recorrência-pai desaparece).
              </p>
              <div className="space-y-2">
                <button
                  onClick={() => excluirMutation.mutate(excluindo.id)}
                  disabled={excluirMutation.isPending}
                  className="w-full btn-danger">
                  {excluirMutation.isPending ? 'Excluindo...' : 'Excluir'}
                </button>
                <button onClick={() => setExcluindo(null)}
                  className="w-full py-2.5 rounded-lg border border-borda text-conteudo-suave hover:text-conteudo transition-colors text-sm font-medium">
                  Cancelar
                </button>
              </div>
            </div>
          </div>
        </SobreposicaoModal>
      )}
    </div>
  )
}
