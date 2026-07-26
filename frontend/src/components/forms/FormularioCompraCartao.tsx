import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { X } from 'lucide-react'
import { format } from 'date-fns'
import { buscarCategorias } from '../../api/categorias'
import { criarItemFatura, atualizarItemFatura } from '../../api/itensFatura'
import SobreposicaoModal from '../SobreposicaoModal'
import type { ItemFatura } from '../../types'

interface Props {
  cartaoId: number
  onClose: () => void
  editing?: ItemFatura
}

export default function FormularioCompraCartao({ cartaoId, onClose, editing }: Props) {
  const qc = useQueryClient()
  const [form, setForm] = useState({
    categoriaId: editing?.categoriaId ?? '',
    valor: editing?.valor ?? '',
    descricao: editing?.descricao ?? '',
    data: editing?.data ?? format(new Date(), 'yyyy-MM-dd'),
    totalParcelas: editing?.totalParcelas ?? '',
  })

  const { data: categorias = [] } = useQuery({
    queryKey: ['categorias', 'DESPESA'],
    queryFn: () => buscarCategorias('DESPESA'),
  })

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
      cartaoId,
      categoriaId: form.categoriaId ? Number(form.categoriaId) : undefined,
      valor: Number(form.valor),
      descricao: form.descricao || undefined,
      data: form.data,
      totalParcelas: form.totalParcelas ? Number(form.totalParcelas) : undefined,
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
              {[...categorias].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')).map(c => <option key={c.id} value={c.id}>{c.nome}</option>)}
            </select>
          </div>

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
