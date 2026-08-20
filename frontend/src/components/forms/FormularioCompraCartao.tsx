import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { X, Repeat } from 'lucide-react'
import { format } from 'date-fns'
import { buscarCategorias } from '../../api/categorias'
import { buscarCentrosCusto } from '../../api/centrosCusto'
import { buscarCartoes } from '../../api/cartoes'
import { criarItemFatura, atualizarItemFatura } from '../../api/itensFatura'
import SobreposicaoModal from '../SobreposicaoModal'
import type { ItemFatura } from '../../types'

const fmtParcela = (valor: string | number, totalParcelas: string | number) => {
  const n = Number(totalParcelas)
  const v = Number(valor) / n
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(v || 0)
}

interface Props {
  // Ausente quando o formulário é aberto de uma tela sem cartão fixo (ex.:
  // "Compras no Cartão") — nesse caso o próprio formulário exibe o seletor.
  cartaoId?: number
  entidadeId?: number | null
  onClose: () => void
  editing?: ItemFatura
}

export default function FormularioCompraCartao({ cartaoId, entidadeId, onClose, editing }: Props) {
  const qc = useQueryClient()
  const navigate = useNavigate()
  const [form, setForm] = useState({
    cartaoId: editing?.cartaoId ?? cartaoId ?? '',
    categoriaId: editing?.categoriaId ?? '',
    valor: editing?.valor ?? '',
    descricao: editing?.descricao ?? '',
    data: editing?.data ?? format(new Date(), 'yyyy-MM-dd'),
    totalParcelas: editing?.totalParcelas ?? '',
    centroCustoId: (editing?.centroCustoId ?? '') as number | '',
  })

  const precisaEscolherCartao = !editing && cartaoId == null

  // Sempre carregada (não só quando precisaEscolherCartao) para resolver a
  // entidade do cartão ao editar um item aberto pela tela sem cartão fixo
  // ("Compras no Cartão"), onde `entidadeId` não vem pronto via prop.
  const { data: cartoes = [] } = useQuery({ queryKey: ['cartoes'], queryFn: buscarCartoes })

  const cartaoSelecionado = cartoes.find(c => c.id === Number(form.cartaoId))
  const entidadeAtual = entidadeId ?? cartaoSelecionado?.contaPagamento?.entidadeId

  const { data: categorias = [] } = useQuery({
    queryKey: ['categorias', 'DESPESA'],
    queryFn: () => buscarCategorias('DESPESA'),
  })

  const { data: centrosCusto = [] } = useQuery({
    queryKey: ['centros-custo'],
    queryFn: buscarCentrosCusto,
  })

  const categoriasFiltradas = categorias.filter(c =>
    entidadeAtual == null || c.entidadeId == null || c.entidadeId === entidadeAtual
  )
  const centrosCustoFiltrados = centrosCusto.filter(cc =>
    entidadeAtual == null || cc.entidadeId == null || cc.entidadeId === entidadeAtual
  )

  const mutation = useMutation({
    mutationFn: async (payload: Parameters<typeof criarItemFatura>[0]) => {
      if (editing) { await atualizarItemFatura(editing.id, payload); return }
      await criarItemFatura(payload)
    },
    onSuccess: async () => {
      await Promise.all([
        qc.invalidateQueries({ queryKey: ['itensFatura'] }),
        qc.invalidateQueries({ queryKey: ['cartoes'] }),
      ])
      onClose()
    },
  })

  const set = (k: string, v: unknown) => setForm(f => ({ ...f, [k]: v }))

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    mutation.mutate({
      cartaoId: Number(form.cartaoId),
      categoriaId: form.categoriaId ? Number(form.categoriaId) : undefined,
      valor: Number(form.valor),
      descricao: form.descricao || undefined,
      data: form.data,
      totalParcelas: form.totalParcelas ? Number(form.totalParcelas) : undefined,
      centroCustoId: form.centroCustoId ? Number(form.centroCustoId) : null,
    })
  }

  return (
    <SobreposicaoModal aoFechar={onClose}>
      <div className="cartao-modal max-w-lg">
        <div className="cartao-modal-cabecalho">
          <h2 className="text-lg font-semibold text-conteudo">
            {editing ? 'Editar Compra' : 'Nova Compra no Cartão'}
          </h2>
          <button onClick={onClose} className="btn-ghost p-1.5"><X size={18} /></button>
        </div>

        <form onSubmit={handleSubmit} className="cartao-modal-corpo">
          {editing?.origemRecorrenciaId && (
            <div className="flex items-start gap-2 p-3 rounded-xl bg-acento/10 border border-acento/30">
              <Repeat size={14} className="text-acento shrink-0 mt-0.5" />
              <span className="text-sm text-conteudo flex-1">
                Compra gerada por uma recorrência. Editar aqui altera <strong>apenas este mês</strong>.
              </span>
              <button type="button" onClick={() => { onClose(); navigate('/lancamentos/recorrencias') }}
                className="text-xs text-acento hover:opacity-80 shrink-0 font-medium whitespace-nowrap">
                Ir para recorrências →
              </button>
            </div>
          )}
          {precisaEscolherCartao && (
            <div>
              <label className="label">Cartão</label>
              <select className="select" value={form.cartaoId} onChange={e => set('cartaoId', e.target.value)} required>
                <option value="">Selecione um cartão</option>
                {[...cartoes].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')).map(c => (
                  <option key={c.id} value={c.id}>{c.nome}</option>
                ))}
              </select>
            </div>
          )}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="label">Valor (R$)</label>
              <input
                className="input" type="number" step="0.01" min="0.01" placeholder="0,00"
                value={form.valor} onChange={e => set('valor', e.target.value)} required
              />
            </div>
            <div>
              <label className="label">Data da compra</label>
              <input
                className="input" type="date"
                value={form.data} onChange={e => set('data', e.target.value)} required
              />
            </div>
          </div>

          <div>
            <label className="label">Categoria</label>
            <select className="select" value={form.categoriaId} onChange={e => set('categoriaId', e.target.value)}>
              <option value="">Sem categoria</option>
              {[...categoriasFiltradas].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')).map(c => <option key={c.id} value={c.id}>{c.nome}</option>)}
            </select>
          </div>

          {centrosCustoFiltrados.length > 0 && (
            <div>
              <label className="label">Centro de Custo</label>
              <select className="select" value={form.centroCustoId} onChange={e => set('centroCustoId', e.target.value ? Number(e.target.value) : '')}>
                <option value="">Sem centro de custo</option>
                {[...centrosCustoFiltrados].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')).map(cc => (
                  <option key={cc.id} value={cc.id}>{cc.nome}</option>
                ))}
              </select>
            </div>
          )}

          <div>
            <label className="label">Descrição</label>
            <input
              className="input" placeholder="Ex: Mercado, Farmácia..."
              value={form.descricao} onChange={e => set('descricao', e.target.value)}
            />
          </div>

          {!editing && (
            <div>
              <label className="label">Parcelar em quantas vezes? (deixe em branco = à vista)</label>
              <input
                className="input" type="number" min="2" max="60" placeholder="Ex: 3"
                value={form.totalParcelas}
                onChange={e => set('totalParcelas', e.target.value)}
              />
              {!!form.totalParcelas && Number(form.totalParcelas) > 1 && (
                <p className="text-xs text-conteudo-suave mt-1">
                  O valor acima é o total da compra — cada parcela fica em {fmtParcela(form.valor, form.totalParcelas)}.
                </p>
              )}
            </div>
          )}

          <div className="flex gap-3 pt-2">
            <button type="button" onClick={onClose} className="flex-1 py-2.5 rounded-lg border border-borda text-conteudo-suave hover:text-conteudo hover:border-conteudo-suave transition-colors text-sm font-medium">
              Cancelar
            </button>
            <button type="submit" disabled={mutation.isPending} className="flex-1 btn-primary">
              {mutation.isPending ? 'Salvando...' : 'Salvar'}
            </button>
          </div>
        </form>
      </div>
    </SobreposicaoModal>
  )
}
