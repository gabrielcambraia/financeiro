import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, Pencil, Trash2 } from 'lucide-react'
import { buscarCategorias, criarCategoria, atualizarCategoria, excluirCategoria } from '../api/categorias'
import SobreposicaoModal from '../components/SobreposicaoModal'
import SeletorCor from '../components/SeletorCor'
import type { Categoria, TipoTransacao } from '../types'

const CORES = ['#ef4444','#f97316','#eab308','#22c55e','#10b981','#06b6d4','#3b82f6','#8b5cf6','#ec4899','#6b7280','#6366f1','#84cc16']
const ICONES = ['utensils','shopping-cart','car','heart-pulse','home','gamepad-2','shirt','book-open','tv','briefcase','laptop','trending-up','tag','plus-circle','ellipsis']

const formPadrao = { nome: '', tipo: 'DESPESA' as TipoTransacao, cor: CORES[0], icone: ICONES[0] }

export default function Categorias() {
  const qc = useQueryClient()
  const [aba, setAba] = useState<TipoTransacao>('DESPESA')
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<Categoria | null>(null)
  const [form, setForm] = useState(formPadrao)

  const { data: categorias = [] } = useQuery({
    queryKey: ['categorias', aba],
    queryFn: () => buscarCategorias(aba),
  })

  const saveMutation = useMutation({
    mutationFn: (data: Omit<Categoria, 'id'>) =>
      editing ? atualizarCategoria(editing.id, data) : criarCategoria(data),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['categorias'] }); closeForm() },
  })

  const deleteMutation = useMutation({
    mutationFn: excluirCategoria,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['categorias'] }),
  })

  const openCreate = () => { setEditing(null); setForm({ ...formPadrao, tipo: aba }); setShowForm(true) }
  const openEdit = (c: Categoria) => { setEditing(c); setForm({ nome: c.nome, tipo: c.tipo, cor: c.cor, icone: c.icone }); setShowForm(true) }
  const closeForm = () => { setShowForm(false); setEditing(null) }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    saveMutation.mutate(form)
  }

  return (
    <div className="p-6 space-y-5">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-conteudo">Categorias</h1>
        <button onClick={openCreate} className="btn-primary flex items-center gap-2">
          <Plus size={16} /> Nova Categoria
        </button>
      </div>

      {/* Abas */}
      <div className="flex rounded-xl overflow-hidden border border-borda w-fit">
        {(['DESPESA', 'RECEITA'] as TipoTransacao[]).map(t => (
          <button key={t} onClick={() => setAba(t)}
            className={`px-6 py-2.5 text-sm font-medium transition-colors
              ${aba === t
                ? t === 'DESPESA' ? 'bg-red-600 text-white' : 'bg-emerald-600 text-white'
                : 'text-conteudo-suave hover:text-conteudo'}`}>
            {t === 'DESPESA' ? 'Despesas' : 'Receitas'}
          </button>
        ))}
      </div>

      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3">
        {categorias.map(cat => (
          <div key={cat.id} className="card flex items-center gap-3 group hover:border-conteudo-suave transition-colors">
            <div className="w-9 h-9 rounded-xl flex items-center justify-center text-lg shrink-0"
              style={{ background: `${cat.cor}20`, color: cat.cor }}>
              #
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-sm font-medium text-conteudo truncate">{cat.nome}</p>
            </div>
            <div className="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
              <button onClick={() => openEdit(cat)} className="p-1 rounded hover:bg-superficie-2 text-conteudo-suave hover:text-conteudo transition-colors">
                <Pencil size={13} />
              </button>
              <button onClick={() => { if (confirm('Excluir esta categoria?')) deleteMutation.mutate(cat.id) }}
                className="p-1 rounded hover:bg-red-900/40 text-conteudo-suave hover:text-red-400 transition-colors">
                <Trash2 size={13} />
              </button>
            </div>
          </div>
        ))}
        {categorias.length === 0 && (
          <div className="card col-span-4 text-center py-10 text-conteudo-suave text-sm">
            Nenhuma categoria cadastrada.
          </div>
        )}
      </div>

      {showForm && (
        <SobreposicaoModal>
          <div className="card w-full max-w-md">
            <div className="flex items-center justify-between mb-5">
              <h2 className="text-lg font-semibold text-conteudo">{editing ? 'Editar Categoria' : 'Nova Categoria'}</h2>
              <button onClick={closeForm} className="btn-ghost p-1.5 text-sm">✕</button>
            </div>
            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label className="label">Nome</label>
                <input className="input" placeholder="Ex: Alimentação, Salário..." required
                  value={form.nome} onChange={e => setForm(f => ({ ...f, nome: e.target.value }))} />
              </div>
              <div>
                <label className="label">Tipo</label>
                <div className="flex rounded-xl overflow-hidden border border-borda">
                  {(['DESPESA', 'RECEITA'] as TipoTransacao[]).map(t => (
                    <button key={t} type="button" onClick={() => setForm(f => ({ ...f, tipo: t }))}
                      className={`flex-1 py-2.5 text-sm font-medium transition-colors
                        ${form.tipo === t
                          ? t === 'DESPESA' ? 'bg-red-600 text-white' : 'bg-emerald-600 text-white'
                          : 'text-conteudo-suave hover:text-conteudo'}`}>
                      {t === 'DESPESA' ? 'Despesa' : 'Receita'}
                    </button>
                  ))}
                </div>
              </div>
              <div>
                <label className="label">Cor</label>
                <SeletorCor cores={CORES} corSelecionada={form.cor} tamanho="sm"
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
    </div>
  )
}
