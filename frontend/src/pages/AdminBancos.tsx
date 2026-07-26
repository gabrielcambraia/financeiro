import { useRef, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Pencil, Trash2, Upload, ImageOff, Search } from 'lucide-react'
import {
  buscarBancos, criarBanco, atualizarBanco, excluirBanco, enviarLogoBanco, removerLogoBanco,
} from '../api/bancos'
import SobreposicaoModal from '../components/SobreposicaoModal'
import LogoBanco from '../components/LogoBanco'
import AcaoNova from '../components/AcaoNova'
import type { Banco } from '../types'

const formPadrao = { nome: '', corPrimaria: '#6366f1', sigla: '' }

export default function AdminBancos() {
  const qc = useQueryClient()
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<Banco | null>(null)
  const [form, setForm] = useState(formPadrao)
  const [busca, setBusca] = useState('')
  const [uploadAlvoId, setUploadAlvoId] = useState<number | null>(null)
  const inputArquivoRef = useRef<HTMLInputElement>(null)

  const { data: bancos = [] } = useQuery({ queryKey: ['bancos'], queryFn: buscarBancos })

  const invalidar = () => qc.invalidateQueries({ queryKey: ['bancos'] })

  const saveMutation = useMutation({
    mutationFn: (data: typeof formPadrao) =>
      editing ? atualizarBanco(editing.id, data) : criarBanco(data),
    onSuccess: async () => { await invalidar(); closeForm() },
  })

  const deleteMutation = useMutation({ mutationFn: excluirBanco, onSuccess: invalidar })
  const uploadMutation = useMutation({
    mutationFn: ({ id, arquivo }: { id: number; arquivo: File }) => enviarLogoBanco(id, arquivo),
    onSuccess: invalidar,
    onError: () => alert('Falha ao enviar imagem. Use PNG, JPEG ou WEBP de até 1MB.'),
  })
  const removerLogoMutation = useMutation({ mutationFn: removerLogoBanco, onSuccess: invalidar })

  const openCreate = () => { setEditing(null); setForm(formPadrao); setShowForm(true) }
  const openEdit = (b: Banco) => {
    setEditing(b)
    setForm({ nome: b.nome, corPrimaria: b.corPrimaria, sigla: b.sigla })
    setShowForm(true)
  }
  const closeForm = () => { setShowForm(false); setEditing(null) }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    saveMutation.mutate(form)
  }

  const acionarUpload = (id: number) => {
    setUploadAlvoId(id)
    inputArquivoRef.current?.click()
  }

  const arquivoSelecionado = (e: React.ChangeEvent<HTMLInputElement>) => {
    const arquivo = e.target.files?.[0]
    e.target.value = ''
    if (arquivo && uploadAlvoId != null) {
      uploadMutation.mutate({ id: uploadAlvoId, arquivo })
    }
  }

  const bancosFiltrados = bancos.filter(b => b.nome.toLowerCase().includes(busca.toLowerCase()))

  return (
    <div className="p-6 space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-conteudo">Bancos</h1>
          <p className="text-sm text-conteudo-suave mt-0.5">Catálogo global — visível para todos os usuários.</p>
        </div>
        <AcaoNova aoClicar={openCreate} rotulo="Novo banco" />
      </div>

      <input ref={inputArquivoRef} type="file" accept="image/png,image/jpeg,image/webp" className="hidden" onChange={arquivoSelecionado} />

      <div className="relative max-w-sm">
        <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-conteudo-suave" />
        <input
          className="input pl-9" placeholder="Buscar bancos..."
          value={busca} onChange={e => setBusca(e.target.value)}
        />
      </div>

      <div className="card p-0 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-superficie-2 text-xs text-conteudo-suave uppercase tracking-wider">
                <th className="text-left font-semibold px-5 py-3">Banco</th>
                <th className="text-left font-semibold px-5 py-3">Sigla</th>
                <th className="text-left font-semibold px-5 py-3">Cor</th>
                <th className="text-right font-semibold px-5 py-3">Ações</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-borda">
              {bancosFiltrados.map(banco => (
                <tr key={banco.id} className="hover:bg-superficie-2 transition-colors group">
                  <td className="px-5 py-3">
                    <div className="flex items-center gap-3">
                      <LogoBanco banco={banco} tamanho={36} />
                      <span className="font-medium text-conteudo">{banco.nome}</span>
                    </div>
                  </td>
                  <td className="px-5 py-3 text-conteudo-suave">{banco.sigla}</td>
                  <td className="px-5 py-3">
                    <div className="flex items-center gap-2">
                      <span className="w-4 h-4 rounded-full border border-borda" style={{ background: banco.corPrimaria }} />
                      <span className="text-conteudo-suave text-xs">{banco.corPrimaria}</span>
                    </div>
                  </td>
                  <td className="px-5 py-3">
                    <div className="flex gap-1 justify-end md:opacity-0 md:group-hover:opacity-100 transition-opacity">
                      <button onClick={() => acionarUpload(banco.id)} title="Enviar logo"
                        className="p-1.5 rounded-lg hover:bg-superficie-2 text-conteudo-suave hover:text-conteudo transition-colors">
                        <Upload size={14} />
                      </button>
                      {banco.temLogo && (
                        <button onClick={() => removerLogoMutation.mutate(banco.id)} title="Remover logo"
                          className="p-1.5 rounded-lg hover:bg-orange-900/40 text-conteudo-suave hover:text-orange-500 transition-colors">
                          <ImageOff size={14} />
                        </button>
                      )}
                      <button onClick={() => openEdit(banco)} title="Editar"
                        className="p-1.5 rounded-lg hover:bg-superficie-2 text-conteudo-suave hover:text-conteudo transition-colors">
                        <Pencil size={14} />
                      </button>
                      <button onClick={() => { if (confirm('Excluir este banco?')) deleteMutation.mutate(banco.id) }} title="Excluir"
                        className="p-1.5 rounded-lg hover:bg-red-900/40 text-conteudo-suave hover:text-red-400 transition-colors">
                        <Trash2 size={14} />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {bancosFiltrados.length === 0 && (
            <p className="text-center py-12 text-conteudo-suave text-sm">
              {bancos.length === 0 ? 'Nenhum banco cadastrado.' : 'Nenhum banco encontrado.'}
            </p>
          )}
        </div>
      </div>
      {bancosFiltrados.length > 0 && (
        <p className="text-xs text-conteudo-suave">{bancosFiltrados.length} registro{bancosFiltrados.length !== 1 ? 's' : ''}</p>
      )}

      {showForm && (
        <SobreposicaoModal aoFechar={closeForm}>
          <div className="cartao-modal max-w-md">
            <div className="cartao-modal-cabecalho">
              <h2 className="text-lg font-semibold text-conteudo">{editing ? 'Editar Banco' : 'Novo Banco'}</h2>
              <button onClick={closeForm} className="btn-ghost p-1.5 text-sm">✕</button>
            </div>
            <form onSubmit={handleSubmit} className="cartao-modal-corpo">
              <div>
                <label className="label">Nome</label>
                <input className="input" placeholder="Ex: Nubank" required
                  value={form.nome} onChange={e => setForm(f => ({ ...f, nome: e.target.value }))} />
              </div>
              <div>
                <label className="label">Sigla (badge quando não há logo)</label>
                <input className="input" placeholder="Ex: nu" maxLength={4} required
                  value={form.sigla} onChange={e => setForm(f => ({ ...f, sigla: e.target.value }))} />
              </div>
              <div>
                <label className="label">Cor da marca</label>
                <div className="flex items-center gap-3">
                  <input type="color" className="w-10 h-10 rounded-lg border border-borda cursor-pointer"
                    value={form.corPrimaria} onChange={e => setForm(f => ({ ...f, corPrimaria: e.target.value }))} />
                  <input className="input flex-1" value={form.corPrimaria}
                    onChange={e => setForm(f => ({ ...f, corPrimaria: e.target.value }))} />
                </div>
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
