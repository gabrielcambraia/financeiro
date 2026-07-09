import { useState, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { X } from 'lucide-react'
import { format } from 'date-fns'
import { buscarContas } from '../../api/contas'
import { buscarCategorias } from '../../api/categorias'
import { criarTransacao, atualizarTransacao } from '../../api/transacoes'
import SobreposicaoModal from '../SobreposicaoModal'
import type { Transacao, TipoTransacao, TipoPagamento } from '../../types'

interface Props {
  onClose: () => void
  editing?: Transacao
}

export default function FormularioTransacao({ onClose, editing }: Props) {
  const qc = useQueryClient()
  const [tipo, setTipo] = useState<TipoTransacao>(editing?.tipo ?? 'DESPESA')
  const [form, setForm] = useState({
    contaId: editing?.contaId ?? '',
    categoriaId: editing?.categoriaId ?? '',
    tipoPagamento: (editing?.tipoPagamento ?? 'DEBITO') as TipoPagamento,
    valor: editing?.valor ?? '',
    descricao: editing?.descricao ?? '',
    data: editing?.data ?? format(new Date(), 'yyyy-MM-dd'),
    fixa: editing?.fixa ?? false,
    totalParcelas: editing?.totalParcelas ?? '',
  })

  const { data: contas = [] } = useQuery({ queryKey: ['contas'], queryFn: buscarContas })

  useEffect(() => {
    if (!editing && contas.length === 1 && !form.contaId) {
      set('contaId', contas[0].id)
    }
  }, [contas])
  const { data: categorias = [] } = useQuery({
    queryKey: ['categorias', tipo],
    queryFn: () => buscarCategorias(tipo),
  })

  const mutation = useMutation({
    mutationFn: async (payload: Parameters<typeof criarTransacao>[0]) => {
      if (editing) { await atualizarTransacao(editing.id, payload); return [] }
      return criarTransacao(payload)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['transacoes'] })
      qc.invalidateQueries({ queryKey: ['painel'] })
      qc.invalidateQueries({ queryKey: ['contas'] })
      onClose()
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    mutation.mutate({
      contaId: Number(form.contaId),
      categoriaId: form.categoriaId ? Number(form.categoriaId) : undefined,
      tipo,
      tipoPagamento: form.tipoPagamento,
      valor: Number(form.valor),
      descricao: form.descricao || undefined,
      data: form.data,
      fixa: form.fixa,
      totalParcelas: form.totalParcelas ? Number(form.totalParcelas) : undefined,
    })
  }

  const set = (k: string, v: unknown) => setForm(f => ({ ...f, [k]: v }))

  return (
    <SobreposicaoModal aoFechar={onClose}>
      <div className="cartao-modal max-w-lg">
        <div className="cartao-modal-cabecalho">
          <h2 className="text-lg font-semibold text-conteudo">
            {editing ? 'Editar Lançamento' : 'Novo Lançamento'}
          </h2>
          <button onClick={onClose} className="btn-ghost p-1.5"><X size={18} /></button>
        </div>

        <form onSubmit={handleSubmit} className="cartao-modal-corpo">
          {/* Tipo */}
          <div className="flex rounded-xl overflow-hidden border border-borda">
            {(['DESPESA', 'RECEITA'] as TipoTransacao[]).map(t => (
              <button
                key={t}
                type="button"
                onClick={() => { setTipo(t); set('categoriaId', '') }}
                className={`flex-1 py-2.5 text-sm font-medium transition-colors
                  ${tipo === t
                    ? t === 'DESPESA' ? 'bg-red-600 text-white' : 'bg-emerald-600 text-white'
                    : 'text-conteudo-suave hover:text-conteudo'}`}
              >
                {t === 'DESPESA' ? 'Despesa' : 'Receita'}
              </button>
            ))}
          </div>

          {/* Conta e Categoria */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="label">Conta</label>
              <select className="select" value={form.contaId} onChange={e => set('contaId', e.target.value)} required>
                <option value="">Selecione...</option>
                {[...contas].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')).map(c => <option key={c.id} value={c.id}>{c.nome}</option>)}
              </select>
            </div>
            <div>
              <label className="label">Categoria</label>
              <select className="select" value={form.categoriaId} onChange={e => set('categoriaId', e.target.value)}>
                <option value="">Sem categoria</option>
                {[...categorias].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')).map(c => <option key={c.id} value={c.id}>{c.nome}</option>)}
              </select>
            </div>
          </div>

          {/* Valor e Data */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="label">Valor (R$)</label>
              <input
                className="input" type="number" step="0.01" min="0.01" placeholder="0,00"
                value={form.valor} onChange={e => set('valor', e.target.value)} required
              />
            </div>
            <div>
              <label className="label">Data</label>
              <input
                className="input" type="date"
                value={form.data} onChange={e => set('data', e.target.value)} required
              />
            </div>
          </div>

          {/* Descrição */}
          <div>
            <label className="label">Descrição</label>
            <input
              className="input" placeholder="Ex: Mercado, Aluguel..."
              value={form.descricao} onChange={e => set('descricao', e.target.value)}
            />
          </div>

          {/* Débito / Crédito */}
          <div>
            <label className="label">Forma de pagamento</label>
            <div className="flex gap-2">
              {(['DEBITO', 'CREDITO'] as TipoPagamento[]).map(p => (
                <button
                  key={p} type="button"
                  onClick={() => set('tipoPagamento', p)}
                  className={`flex-1 py-2 text-sm rounded-lg border transition-colors
                    ${form.tipoPagamento === p
                      ? 'border-acento bg-acento/20 text-acento'
                      : 'border-borda text-conteudo-suave hover:border-conteudo-suave'}`}
                >
                  {p === 'DEBITO' ? 'Débito' : 'Crédito'}
                </button>
              ))}
            </div>
          </div>

          {/* Fixa / Parcelada — só para despesas ou sem parcelamento para receitas */}
          {!editing && (
            <div className="space-y-3">
              <div className="flex items-center gap-3 p-3 rounded-xl bg-superficie-2">
                <input
                  id="fixa" type="checkbox"
                  checked={form.fixa}
                  onChange={e => { set('fixa', e.target.checked); if (e.target.checked) set('totalParcelas', '') }}
                  className="w-4 h-4 accent-acento"
                />
                <label htmlFor="fixa" className="text-sm text-conteudo">
                  Repetir todo mês (fixa)
                </label>
              </div>

              {!form.fixa && (
                <div>
                  <label className="label">Parcelar em quantas vezes? (deixe em branco = à vista)</label>
                  <input
                    className="input" type="number" min="2" max="60" placeholder="Ex: 3"
                    value={form.totalParcelas}
                    onChange={e => set('totalParcelas', e.target.value)}
                  />
                </div>
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

