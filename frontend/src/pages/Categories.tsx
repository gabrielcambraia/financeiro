import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, Pencil, Trash2 } from 'lucide-react'
import { getCategories, createCategory, updateCategory, deleteCategory } from '../api/categories'
import type { Category, TransactionType } from '../types'

const COLORS = ['#ef4444','#f97316','#eab308','#22c55e','#10b981','#06b6d4','#3b82f6','#8b5cf6','#ec4899','#6b7280','#6366f1','#84cc16']
const ICONS = ['utensils','shopping-cart','car','heart-pulse','home','gamepad-2','shirt','book-open','tv','briefcase','laptop','trending-up','tag','plus-circle','ellipsis']

const defaultForm = { name: '', type: 'EXPENSE' as TransactionType, color: COLORS[0], icon: ICONS[0] }

export default function Categories() {
  const qc = useQueryClient()
  const [tab, setTab] = useState<TransactionType>('EXPENSE')
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<Category | null>(null)
  const [form, setForm] = useState(defaultForm)

  const { data: categories = [] } = useQuery({
    queryKey: ['categories', tab],
    queryFn: () => getCategories(tab),
  })

  const saveMutation = useMutation({
    mutationFn: (data: Omit<Category, 'id'>) =>
      editing ? updateCategory(editing.id, data) : createCategory(data),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['categories'] }); closeForm() },
  })

  const deleteMutation = useMutation({
    mutationFn: deleteCategory,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['categories'] }),
  })

  const openCreate = () => { setEditing(null); setForm({ ...defaultForm, type: tab }); setShowForm(true) }
  const openEdit = (c: Category) => { setEditing(c); setForm({ name: c.name, type: c.type, color: c.color, icon: c.icon }); setShowForm(true) }
  const closeForm = () => { setShowForm(false); setEditing(null) }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    saveMutation.mutate(form)
  }

  return (
    <div className="p-6 space-y-5">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-white">Categorias</h1>
        <button onClick={openCreate} className="btn-primary flex items-center gap-2">
          <Plus size={16} /> Nova Categoria
        </button>
      </div>

      {/* Tabs */}
      <div className="flex rounded-xl overflow-hidden border border-gray-800 w-fit">
        {(['EXPENSE', 'INCOME'] as TransactionType[]).map(t => (
          <button key={t} onClick={() => setTab(t)}
            className={`px-6 py-2.5 text-sm font-medium transition-colors
              ${tab === t
                ? t === 'EXPENSE' ? 'bg-red-600 text-white' : 'bg-emerald-600 text-white'
                : 'text-gray-400 hover:text-white'}`}>
            {t === 'EXPENSE' ? 'Despesas' : 'Receitas'}
          </button>
        ))}
      </div>

      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3">
        {categories.map(cat => (
          <div key={cat.id} className="card flex items-center gap-3 group hover:border-gray-700 transition-colors">
            <div className="w-9 h-9 rounded-xl flex items-center justify-center text-lg shrink-0"
              style={{ background: `${cat.color}20`, color: cat.color }}>
              #
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-sm font-medium text-white truncate">{cat.name}</p>
            </div>
            <div className="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
              <button onClick={() => openEdit(cat)} className="p-1 rounded hover:bg-gray-700 text-gray-500 hover:text-white transition-colors">
                <Pencil size={13} />
              </button>
              <button onClick={() => { if (confirm('Excluir esta categoria?')) deleteMutation.mutate(cat.id) }}
                className="p-1 rounded hover:bg-red-900/40 text-gray-500 hover:text-red-400 transition-colors">
                <Trash2 size={13} />
              </button>
            </div>
          </div>
        ))}
        {categories.length === 0 && (
          <div className="card col-span-4 text-center py-10 text-gray-600 text-sm">
            Nenhuma categoria cadastrada.
          </div>
        )}
      </div>

      {showForm && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 p-4">
          <div className="card w-full max-w-md">
            <div className="flex items-center justify-between mb-5">
              <h2 className="text-lg font-semibold text-white">{editing ? 'Editar Categoria' : 'Nova Categoria'}</h2>
              <button onClick={closeForm} className="btn-ghost p-1.5 text-sm">✕</button>
            </div>
            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label className="label">Nome</label>
                <input className="input" placeholder="Ex: Alimentação, Salário..." required
                  value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))} />
              </div>
              <div>
                <label className="label">Tipo</label>
                <div className="flex rounded-xl overflow-hidden border border-gray-700">
                  {(['EXPENSE', 'INCOME'] as TransactionType[]).map(t => (
                    <button key={t} type="button" onClick={() => setForm(f => ({ ...f, type: t }))}
                      className={`flex-1 py-2.5 text-sm font-medium transition-colors
                        ${form.type === t
                          ? t === 'EXPENSE' ? 'bg-red-600 text-white' : 'bg-emerald-600 text-white'
                          : 'text-gray-400 hover:text-white'}`}>
                      {t === 'EXPENSE' ? 'Despesa' : 'Receita'}
                    </button>
                  ))}
                </div>
              </div>
              <div>
                <label className="label">Cor</label>
                <div className="flex gap-2 flex-wrap">
                  {COLORS.map(c => (
                    <button key={c} type="button" onClick={() => setForm(f => ({ ...f, color: c }))}
                      className={`w-7 h-7 rounded-full border-2 transition-all ${form.color === c ? 'border-white scale-110' : 'border-transparent'}`}
                      style={{ background: c }} />
                  ))}
                </div>
              </div>
              <div className="flex gap-3 pt-2">
                <button type="button" onClick={closeForm}
                  className="flex-1 py-2.5 rounded-lg border border-gray-700 text-gray-400 hover:text-white transition-colors text-sm font-medium">
                  Cancelar
                </button>
                <button type="submit" disabled={saveMutation.isPending} className="flex-1 btn-primary">
                  {saveMutation.isPending ? 'Salvando...' : 'Salvar'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
