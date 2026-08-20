import { useMemo, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Plus } from 'lucide-react'
import { buscarItensFatura, excluirItemFatura, cancelarItemFatura } from '../../api/itensFatura'
import { buscarCartoes } from '../../api/cartoes'
import { buscarContas } from '../../api/contas'
import { buscarCategorias } from '../../api/categorias'
import { buscarCentrosCusto } from '../../api/centrosCusto'
import { useLojaFiltro } from '../../store/lojaFiltro'
import SeletorMes from '../../components/SeletorMes'
import LinhaCompraCartao from '../../components/LinhaCompraCartao'
import FormularioCompraCartao from '../../components/forms/FormularioCompraCartao'
import ModalConfirmacao from '../../components/ModalConfirmacao'
import Spinner from '../../components/Spinner'
import type { ItemFatura } from '../../types'

const fmt = (v: number) =>
  new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(v)

const chipBase = 'text-xs px-3 py-1.5 rounded-full font-medium transition-colors'
const chipAtivo = 'bg-acento text-white border border-acento'
const chipInativo = 'border border-borda text-conteudo-suave hover:border-acento hover:text-acento bg-superficie'

type FiltroFaturamento = 'TODAS' | 'ABERTAS' | 'FATURADAS'

export default function ComprasCartao() {
  const qc = useQueryClient()
  const { mes, contaId, definirContaId } = useLojaFiltro()
  const [filtroCartao, setFiltroCartao] = useState<number | ''>('')
  const [filtroCategoria, setFiltroCategoria] = useState<number | ''>('')
  const [filtroCentroCusto, setFiltroCentroCusto] = useState<number | ''>('')
  const [filtroFaturamento, setFiltroFaturamento] = useState<FiltroFaturamento>('TODAS')
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<ItemFatura | undefined>()
  const [confirmarExcluirItem, setConfirmarExcluirItem] = useState<ItemFatura | null>(null)

  const { data: todos = [], isLoading } = useQuery({
    queryKey: ['itensFatura', 'compras', mes, contaId, filtroCartao, filtroCentroCusto],
    queryFn: () => buscarItensFatura({
      month: mes, contaId, cartaoId: filtroCartao || undefined,
      centroCustoId: filtroCentroCusto || undefined, incluirFaturados: true,
    }),
  })

  const itens = todos
    .filter(i => !filtroCategoria || i.categoriaId === filtroCategoria)
    .filter(i => filtroFaturamento === 'TODAS' || (filtroFaturamento === 'ABERTAS' ? !i.faturado : i.faturado))

  const { data: cartoes = [] } = useQuery({ queryKey: ['cartoes'], queryFn: buscarCartoes })
  const { data: contas = [] } = useQuery({ queryKey: ['contas'], queryFn: buscarContas })
  const { data: categorias = [] } = useQuery({ queryKey: ['categorias', 'DESPESA'], queryFn: () => buscarCategorias('DESPESA') })
  const { data: centrosCusto = [] } = useQuery({ queryKey: ['centros-custo'], queryFn: buscarCentrosCusto })

  const grupos = useMemo(() => {
    const mapa = new Map<number, ItemFatura[]>()
    for (const item of itens) {
      if (!mapa.has(item.cartaoId)) mapa.set(item.cartaoId, [])
      mapa.get(item.cartaoId)!.push(item)
    }
    return Array.from(mapa.entries())
      .map(([cartaoId, itensDoCartao]) => ({
        cartaoId,
        cartaoNome: itensDoCartao[0].cartaoNome,
        cartaoCor: itensDoCartao[0].cartaoCor,
        contaPagamentoNome: itensDoCartao[0].contaPagamentoNome,
        itens: [...itensDoCartao].sort((a, b) => b.data.localeCompare(a.data)),
        subtotal: itensDoCartao.filter(i => !i.cancelado).reduce((s, i) => s + i.valor, 0),
      }))
      .sort((a, b) => a.cartaoNome.localeCompare(b.cartaoNome, 'pt-BR'))
  }, [itens])

  const totalMes = itens.filter(i => !i.cancelado).reduce((s, i) => s + i.valor, 0)
  const totalAberto = itens.filter(i => !i.cancelado && !i.faturado).reduce((s, i) => s + i.valor, 0)

  const invalidarTudo = async () => {
    await Promise.all([
      qc.invalidateQueries({ queryKey: ['itensFatura'] }),
      qc.invalidateQueries({ queryKey: ['faturas'] }),
      qc.invalidateQueries({ queryKey: ['cartoes'] }),
      qc.invalidateQueries({ queryKey: ['painel'] }),
      qc.invalidateQueries({ queryKey: ['contas'] }),
    ])
  }

  const excluirMutation = useMutation({
    mutationFn: excluirItemFatura,
    onSuccess: async () => { await invalidarTudo(); setConfirmarExcluirItem(null); toast.success('Compra excluída') },
  })
  const cancelarMutation = useMutation({
    mutationFn: cancelarItemFatura,
    onSuccess: async () => { await invalidarTudo(); toast.success('Compra cancelada') },
  })

  return (
    <div className="p-6 space-y-5">
      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-conteudo">Compras no cartão</h1>
          <p className="text-sm text-conteudo-suave mt-0.5">Despesas de crédito de todos os cartões</p>
        </div>
        <div className="flex items-center gap-3">
          <SeletorMes />
          <button
            className="btn-primary text-xs py-1.5 px-3 flex items-center gap-1"
            onClick={() => { setEditing(undefined); setShowForm(true) }}>
            <Plus size={14} />
            Nova
          </button>
        </div>
      </div>

      {/* Resumo */}
      <div className="grid grid-cols-2 gap-3">
        <div className="card p-4">
          <p className="text-xs text-conteudo-suave">Total no mês</p>
          <p className="text-xl font-bold text-conteudo">{fmt(totalMes)}</p>
        </div>
        <div className="card p-4">
          <p className="text-xs text-conteudo-suave">Em aberto</p>
          <p className="text-xl font-bold text-perigo">{fmt(totalAberto)}</p>
        </div>
      </div>

      {/* Filtros */}
      <div className="card p-3 flex flex-wrap gap-2 items-center">
        <button className={`${chipBase} ${filtroFaturamento === 'TODAS' ? chipAtivo : chipInativo}`} onClick={() => setFiltroFaturamento('TODAS')}>Todas</button>
        <button className={`${chipBase} ${filtroFaturamento === 'ABERTAS' ? chipAtivo : chipInativo}`} onClick={() => setFiltroFaturamento('ABERTAS')}>Em aberto</button>
        <button className={`${chipBase} ${filtroFaturamento === 'FATURADAS' ? chipAtivo : chipInativo}`} onClick={() => setFiltroFaturamento('FATURADAS')}>Faturadas</button>
        <div className="w-px h-5 bg-borda shrink-0" />
        <select
          className="text-xs px-3 py-1.5 rounded-full font-medium border border-borda bg-superficie text-conteudo-suave focus:border-acento focus:outline-none cursor-pointer"
          value={filtroCartao}
          onChange={e => setFiltroCartao(e.target.value ? Number(e.target.value) : '')}>
          <option value="">Todos os cartões</option>
          {[...cartoes].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')).map(c => (
            <option key={c.id} value={c.id}>{c.nome}</option>
          ))}
        </select>
        <select
          className="text-xs px-3 py-1.5 rounded-full font-medium border border-borda bg-superficie text-conteudo-suave focus:border-acento focus:outline-none cursor-pointer"
          value={contaId ?? ''}
          onChange={e => definirContaId(e.target.value ? Number(e.target.value) : undefined)}>
          <option value="">Conta de pagamento</option>
          {[...contas].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')).map(c => (
            <option key={c.id} value={c.id}>{c.nome}</option>
          ))}
        </select>
        <select
          className="text-xs px-3 py-1.5 rounded-full font-medium border border-borda bg-superficie text-conteudo-suave focus:border-acento focus:outline-none cursor-pointer"
          value={filtroCategoria}
          onChange={e => setFiltroCategoria(e.target.value ? Number(e.target.value) : '')}>
          <option value="">Categoria</option>
          {[...categorias].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')).map(c => (
            <option key={c.id} value={c.id}>{c.nome}</option>
          ))}
        </select>
        {centrosCusto.length > 0 && (
          <select
            className="text-xs px-3 py-1.5 rounded-full font-medium border border-borda bg-superficie text-conteudo-suave focus:border-acento focus:outline-none cursor-pointer"
            value={filtroCentroCusto}
            onChange={e => setFiltroCentroCusto(e.target.value ? Number(e.target.value) : '')}>
            <option value="">Centro de Custo</option>
            {[...centrosCusto].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')).map(cc => (
              <option key={cc.id} value={cc.id}>{cc.nome}</option>
            ))}
          </select>
        )}
      </div>

      {/* Lista agrupada por cartão */}
      {isLoading ? (
        <div className="flex justify-center py-12"><Spinner /></div>
      ) : grupos.length === 0 ? (
        <div className="card text-center py-12 text-conteudo-suave">
          <p>Nenhuma compra encontrada para este período.</p>
          <button onClick={() => { setEditing(undefined); setShowForm(true) }} className="mt-3 text-acento hover:opacity-80 text-sm">
            + Adicionar compra
          </button>
        </div>
      ) : (
        <div className="space-y-4">
          {grupos.map(grupo => (
            <div key={grupo.cartaoId} className="card">
              <div className="flex items-center justify-between pb-3 border-b border-borda mb-1">
                <div className="flex items-center gap-2">
                  <div className="w-3 h-3 rounded-full shrink-0" style={{ background: grupo.cartaoCor }} />
                  <div>
                    <div className="text-sm font-semibold text-conteudo">{grupo.cartaoNome}</div>
                    <div className="text-xs text-conteudo-suave">{grupo.contaPagamentoNome}</div>
                  </div>
                </div>
                <div className="text-sm font-bold text-conteudo">{fmt(grupo.subtotal)}</div>
              </div>
              {grupo.itens.map(item => (
                <LinhaCompraCartao
                  key={item.id}
                  item={item}
                  editavel={!item.faturado}
                  mostrarData
                  mostrarFatura
                  onEdit={!item.faturado ? () => { setEditing(item); setShowForm(true) } : undefined}
                  onExcluir={!item.faturado ? () => setConfirmarExcluirItem(item) : undefined}
                  onCancelar={!item.faturado ? () => cancelarMutation.mutate(item.id) : undefined}
                />
              ))}
            </div>
          ))}
        </div>
      )}

      <ModalConfirmacao
        aberto={confirmarExcluirItem !== null}
        titulo="Excluir compra"
        aoFechar={() => !excluirMutation.isPending && setConfirmarExcluirItem(null)}
        botoes={[{ rotulo: 'Excluir', variante: 'perigo', aoClicar: () => excluirMutation.mutate(confirmarExcluirItem!.id), carregando: excluirMutation.isPending }]}
      >
        {`Deseja excluir a compra "${confirmarExcluirItem?.descricao || confirmarExcluirItem?.categoria?.nome}"?`}
      </ModalConfirmacao>

      {showForm && (
        <FormularioCompraCartao
          editing={editing}
          onClose={() => { setShowForm(false); setEditing(undefined) }}
        />
      )}
    </div>
  )
}
