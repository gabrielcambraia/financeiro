import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Pencil, Trash2 } from 'lucide-react'
import { buscarCentrosCusto, criarCentroCusto, atualizarCentroCusto, excluirCentroCusto } from '../api/centrosCusto'
import SobreposicaoModal from '../components/SobreposicaoModal'
import ModalConfirmacao from '../components/ModalConfirmacao'
import SeletorCor from '../components/SeletorCor'
import AcaoNova from '../components/AcaoNova'
import CampoEntidade from '../components/forms/CampoEntidade'
import Spinner from '../components/Spinner'
import { useLojaAutenticacao } from '../store/lojaAutenticacao'
import type { CentroCusto } from '../types'

const CORES = ['#ef4444','#f97316','#eab308','#22c55e','#10b981','#06b6d4','#3b82f6','#8b5cf6','#ec4899','#6b7280','#6366f1','#84cc16']

const formPadrao = { nome: '', cor: CORES[0], entidadeId: undefined as number | null | undefined }

export default function CentrosCusto() {
  const qc = useQueryClient()
  const papel = useLojaAutenticacao(s => s.sessao?.papel)
  const isDono = papel === 'DONO'

  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<CentroCusto | null>(null)
  const [form, setForm] = useState(formPadrao)
  const [confirmarExcluir, setConfirmarExcluir] = useState<CentroCusto | null>(null)

  const { data: centros = [], isLoading } = useQuery({
    queryKey: ['centros-custo'],
    queryFn: buscarCentrosCusto,
  })

  const saveMutation = useMutation({
    mutationFn: (data: Omit<CentroCusto, 'id'>) =>
      editing ? atualizarCentroCusto(editing.id, data) : criarCentroCusto(data),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ['centros-custo'] })
      toast.success('Centro de custo salvo')
      closeForm()
    },
  })

  const deleteMutation = useMutation({
    mutationFn: excluirCentroCusto,
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ['centros-custo'] })
      setConfirmarExcluir(null)
      toast.success('Centro de custo excluído')
    },
  })

  const openCreate = () => { setEditing(null); setForm(formPadrao); setShowForm(true) }
  const openEdit = (cc: CentroCusto) => {
    setEditing(cc)
    setForm({ nome: cc.nome, cor: cc.cor, entidadeId: cc.entidadeId })
    setShowForm(true)
  }
  const closeForm = () => { setShowForm(false); setEditing(null) }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    saveMutation.mutate({ ...form, entidadeId: form.entidadeId ?? null })
  }

  return (
    <div className="p-6 space-y-5">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-conteudo">Centros de Custo</h1>
        {isDono && <AcaoNova aoClicar={openCreate} rotulo="Novo centro de custo" />}
      </div>

      {!isDono && (
        <p className="text-sm text-conteudo-suave">
          Somente o dono do espaço pode criar ou alterar centros de custo.
        </p>
      )}

      {isLoading ? (
        <div className="flex justify-center py-12"><Spinner /></div>
      ) : null}

      <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3">
        {!isLoading && centros.map(cc => (
          <div key={cc.id} className="card flex items-center gap-3 group hover:border-conteudo-suave transition-colors">
            <div className="w-9 h-9 rounded-xl flex items-center justify-center shrink-0"
              style={{ background: `${cc.cor}20`, color: cc.cor }}>
              <span className="w-3 h-3 rounded-full" style={{ background: cc.cor }} />
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-sm font-medium text-conteudo truncate">{cc.nome}</p>
            </div>
            {isDono && (
              <div className="flex gap-1 md:opacity-0 md:group-hover:opacity-100 transition-opacity">
                <button onClick={() => openEdit(cc)} className="p-1 rounded hover:bg-superficie-2 text-conteudo-suave hover:text-conteudo transition-colors">
                  <Pencil size={13} />
                </button>
                <button onClick={() => setConfirmarExcluir(cc)}
                  className="p-1 rounded hover:bg-red-900/40 text-conteudo-suave hover:text-red-400 transition-colors">
                  <Trash2 size={13} />
                </button>
              </div>
            )}
          </div>
        ))}
        {!isLoading && centros.length === 0 && (
          <div className="card col-span-full text-center py-10 text-conteudo-suave text-sm">
            Nenhum centro de custo cadastrado.
          </div>
        )}
      </div>

      <ModalConfirmacao
        aberto={confirmarExcluir !== null}
        titulo="Excluir centro de custo"
        aoFechar={() => !deleteMutation.isPending && setConfirmarExcluir(null)}
        botoes={[{ rotulo: 'Excluir', variante: 'perigo', aoClicar: () => deleteMutation.mutate(confirmarExcluir!.id), carregando: deleteMutation.isPending }]}
      >
        {`Deseja excluir "${confirmarExcluir?.nome}"? Lançamentos associados não serão excluídos — apenas perderão o vínculo com este centro de custo.`}
      </ModalConfirmacao>

      {showForm && (
        <SobreposicaoModal aoFechar={closeForm}>
          <div className="cartao-modal max-w-md">
            <div className="cartao-modal-cabecalho">
              <h2 className="text-lg font-semibold text-conteudo">{editing ? 'Editar Centro de Custo' : 'Novo Centro de Custo'}</h2>
              <button onClick={closeForm} className="btn-ghost p-1.5 text-sm">✕</button>
            </div>
            <form onSubmit={handleSubmit} className="cartao-modal-corpo">
              <CampoEntidade value={form.entidadeId} onChange={v => setForm(f => ({ ...f, entidadeId: v }))} />
              <div>
                <label className="label">Nome</label>
                <input className="input" placeholder="Ex: Consultório, Casa, Loja..." required
                  value={form.nome} onChange={e => setForm(f => ({ ...f, nome: e.target.value }))} />
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
