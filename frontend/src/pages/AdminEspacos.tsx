import { useState } from 'react'
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Pencil } from 'lucide-react'
import { format, parseISO } from 'date-fns'
import { listarEspacosAdmin, alterarTipoEspaco, alterarModulosEspaco, type EspacoAdmin } from '../api/espacosAdmin'
import { alterarPlanoEspaco } from '../api/planosAdmin'
import SobreposicaoModal from '../components/SobreposicaoModal'
import Paginacao from '../components/Paginacao'
import Spinner from '../components/Spinner'
import type { TipoEspaco, ModuloEspaco, CodigoPlano } from '../types'

const rotuloTipo: Record<TipoEspaco, string> = {
  PESSOAL: 'Pessoal',
  FAMILIA: 'Família',
  EMPRESA: 'Empresa',
}

// Catálogo de módulos disponíveis para habilitar por espaço. Hoje só existe
// um, de exemplo — novos módulos entram aqui conforme forem criados.
const TODOS_MODULOS: ModuloEspaco[] = ['WHATSAPP_IA']

const rotuloModulo: Record<ModuloEspaco, string> = {
  WHATSAPP_IA: 'WhatsApp IA',
}

export default function AdminEspacos() {
  const qc = useQueryClient()
  const [pagina, setPagina] = useState(0)
  const [expandidos, setExpandidos] = useState<Set<number>>(new Set())

  const [editandoTipo, setEditandoTipo] = useState<EspacoAdmin | null>(null)
  const [tipoSelecionado, setTipoSelecionado] = useState<TipoEspaco>('PESSOAL')

  const [editandoModulos, setEditandoModulos] = useState<EspacoAdmin | null>(null)
  const [modulosSelecionados, setModulosSelecionados] = useState<Set<ModuloEspaco>>(new Set())

  const [editandoPlano, setEditandoPlano] = useState<EspacoAdmin | null>(null)
  const [planoSelecionado, setPlanoSelecionado] = useState<CodigoPlano>('INDIVIDUAL')

  const { data, isLoading } = useQuery({
    queryKey: ['admin-espacos', pagina],
    queryFn: () => listarEspacosAdmin(pagina),
    placeholderData: keepPreviousData,
  })

  const mutacaoTipo = useMutation({
    mutationFn: ({ id, tipo }: { id: number; tipo: TipoEspaco }) => alterarTipoEspaco(id, tipo),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ['admin-espacos'] })
      setEditandoTipo(null)
      toast.success('Tipo atualizado')
    },
  })

  const mutacaoModulos = useMutation({
    mutationFn: ({ id, modulos }: { id: number; modulos: ModuloEspaco[] }) => alterarModulosEspaco(id, modulos),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ['admin-espacos'] })
      setEditandoModulos(null)
      toast.success('Módulos atualizados')
    },
  })

  const mutacaoPlano = useMutation({
    mutationFn: ({ id, plano }: { id: number; plano: CodigoPlano }) => alterarPlanoEspaco(id, plano),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ['admin-espacos'] })
      setEditandoPlano(null)
      toast.success('Plano atualizado')
    },
  })

  const abrirModalTipo = (espaco: EspacoAdmin) => {
    setEditandoTipo(espaco)
    setTipoSelecionado(espaco.tipo)
  }

  const abrirModalModulos = (espaco: EspacoAdmin) => {
    setEditandoModulos(espaco)
    setModulosSelecionados(new Set(espaco.modulosHabilitados))
  }

  const abrirModalPlano = (espaco: EspacoAdmin) => {
    setEditandoPlano(espaco)
    setPlanoSelecionado(espaco.codigoPlano ?? 'INDIVIDUAL')
  }

  const alternarModulo = (modulo: ModuloEspaco) => {
    setModulosSelecionados(prev => {
      const novo = new Set(prev)
      if (novo.has(modulo)) novo.delete(modulo)
      else novo.add(modulo)
      return novo
    })
  }

  const handleSubmitTipo = (e: React.FormEvent) => {
    e.preventDefault()
    if (editandoTipo) mutacaoTipo.mutate({ id: editandoTipo.id, tipo: tipoSelecionado })
  }

  const handleSubmitModulos = (e: React.FormEvent) => {
    e.preventDefault()
    if (editandoModulos) mutacaoModulos.mutate({ id: editandoModulos.id, modulos: Array.from(modulosSelecionados) })
  }

  const handleSubmitPlano = (e: React.FormEvent) => {
    e.preventDefault()
    if (editandoPlano) mutacaoPlano.mutate({ id: editandoPlano.id, plano: planoSelecionado })
  }

  const alternarExpandido = (id: number) => {
    setExpandidos(prev => {
      const novo = new Set(prev)
      if (novo.has(id)) novo.delete(id)
      else novo.add(id)
      return novo
    })
  }

  const espacos = data?.itens ?? []

  return (
    <div className="p-6 space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-conteudo">Espaços</h1>
          <p className="text-sm text-conteudo-suave mt-0.5">Todos os espaços da plataforma.</p>
        </div>
      </div>

      <div className="card p-0 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-superficie-2 text-xs text-conteudo-suave uppercase tracking-wider">
                <th className="text-left font-semibold px-5 py-3">Espaço</th>
                <th className="text-left font-semibold px-5 py-3">Tipo</th>
                <th className="text-left font-semibold px-5 py-3">Plano</th>
                <th className="text-left font-semibold px-5 py-3">Criado em</th>
                <th className="text-left font-semibold px-5 py-3">Dono</th>
                <th className="text-left font-semibold px-5 py-3">Vinculados</th>
                <th className="text-left font-semibold px-5 py-3">Módulos</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-borda">
              {isLoading ? (
                <tr><td colSpan={7} className="text-center py-12"><Spinner className="mx-auto" /></td></tr>
              ) : espacos.map(espaco => {
                const expandido = expandidos.has(espaco.id)
                const vinculosVisiveis = expandido ? espaco.vinculos : espaco.vinculos.slice(0, 3)
                const restantes = espaco.vinculos.length - 3

                return (
                  <tr key={espaco.id} className="hover:bg-superficie-2 transition-colors group">
                    <td className="px-5 py-3 whitespace-nowrap">
                      <span className="font-medium text-conteudo">{espaco.nome}</span>
                      <span className="text-xs text-conteudo-suave ml-1.5">#{espaco.id}</span>
                    </td>
                    <td className="px-5 py-3 whitespace-nowrap">
                      <div className="flex items-center gap-1.5">
                        <span className="text-conteudo-suave">{rotuloTipo[espaco.tipo]}</span>
                        <button
                          onClick={() => abrirModalTipo(espaco)}
                          title="Editar tipo"
                          className="p-1 rounded-lg hover:bg-superficie text-conteudo-suave hover:text-conteudo transition-colors md:opacity-0 md:group-hover:opacity-100"
                        >
                          <Pencil size={12} />
                        </button>
                      </div>
                    </td>
                    <td className="px-5 py-3 whitespace-nowrap">
                      <div className="flex items-center gap-1.5">
                        <span className="text-conteudo-suave">{espaco.codigoPlano ?? '—'}</span>
                        <button
                          onClick={() => abrirModalPlano(espaco)}
                          title="Editar plano"
                          className="p-1 rounded-lg hover:bg-superficie text-conteudo-suave hover:text-conteudo transition-colors md:opacity-0 md:group-hover:opacity-100"
                        >
                          <Pencil size={12} />
                        </button>
                      </div>
                    </td>
                    <td className="px-5 py-3 whitespace-nowrap text-conteudo-suave">
                      {format(parseISO(espaco.criadoEm), 'dd/MM/yyyy')}
                    </td>
                    <td className="px-5 py-3 text-conteudo-suave truncate max-w-[220px]">
                      {espaco.emailDono ?? '—'}
                    </td>
                    <td className="px-5 py-3">
                      <div className={`flex gap-1.5 ${expandido ? 'flex-col items-start' : 'flex-wrap items-center'}`}>
                        {vinculosVisiveis.map(v => (
                          <span
                            key={v.usuarioId}
                            title={v.email}
                            className={`text-xs px-2 py-0.5 rounded-full truncate max-w-[180px] ${
                              v.papel === 'DONO' ? 'text-pastel-lilas-texto bg-pastel-lilas' : 'text-pastel-azul-texto bg-pastel-azul'
                            }`}
                          >
                            {v.email}
                          </span>
                        ))}
                        {restantes > 0 && (
                          <button
                            onClick={() => alternarExpandido(espaco.id)}
                            className="text-xs text-acento hover:underline shrink-0"
                          >
                            {expandido ? 'menos' : `+${restantes}`}
                          </button>
                        )}
                      </div>
                    </td>
                    <td className="px-5 py-3 whitespace-nowrap">
                      <div className="flex items-center gap-1.5">
                        {espaco.modulosHabilitados.length > 0 ? (
                          <div className="flex flex-wrap gap-1">
                            {espaco.modulosHabilitados.map(m => (
                              <span key={m} className="text-xs px-2 py-0.5 rounded-full text-pastel-azul-texto bg-pastel-azul">
                                {rotuloModulo[m]}
                              </span>
                            ))}
                          </div>
                        ) : (
                          <span className="text-xs text-conteudo-suave">Nenhum</span>
                        )}
                        <button
                          onClick={() => abrirModalModulos(espaco)}
                          title="Editar módulos"
                          className="p-1 rounded-lg hover:bg-superficie text-conteudo-suave hover:text-conteudo transition-colors md:opacity-0 md:group-hover:opacity-100"
                        >
                          <Pencil size={12} />
                        </button>
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
          {!isLoading && espacos.length === 0 && (
            <p className="text-center py-12 text-conteudo-suave text-sm">Nenhum espaço encontrado.</p>
          )}
        </div>
      </div>

      {data && data.totalItens > 0 && (
        <div className="flex items-center justify-between">
          <p className="text-xs text-conteudo-suave">{data.totalItens} registro{data.totalItens !== 1 ? 's' : ''}</p>
          <Paginacao pagina={data.pagina} totalPaginas={data.totalPaginas} aoMudar={setPagina} />
        </div>
      )}

      {editandoTipo && (
        <SobreposicaoModal aoFechar={() => setEditandoTipo(null)}>
          <div className="cartao-modal max-w-sm">
            <div className="cartao-modal-cabecalho">
              <h2 className="text-lg font-semibold text-conteudo">Editar tipo — {editandoTipo.nome}</h2>
              <button onClick={() => setEditandoTipo(null)} className="btn-ghost p-1.5 text-sm">✕</button>
            </div>
            <form onSubmit={handleSubmitTipo} className="cartao-modal-corpo">
              <div>
                <label className="label">Tipo do espaço</label>
                <select
                  className="input"
                  value={tipoSelecionado}
                  onChange={e => setTipoSelecionado(e.target.value as TipoEspaco)}
                >
                  <option value="PESSOAL">Pessoal</option>
                  <option value="FAMILIA">Família</option>
                  <option value="EMPRESA">Empresa</option>
                </select>
              </div>
              <div className="flex gap-3 pt-2">
                <button type="button" onClick={() => setEditandoTipo(null)}
                  className="flex-1 py-2.5 rounded-lg border border-borda text-conteudo-suave hover:text-conteudo transition-colors text-sm font-medium">
                  Cancelar
                </button>
                <button type="submit" disabled={mutacaoTipo.isPending} className="flex-1 btn-primary">
                  {mutacaoTipo.isPending ? 'Salvando...' : 'Salvar'}
                </button>
              </div>
            </form>
          </div>
        </SobreposicaoModal>
      )}

      {editandoModulos && (
        <SobreposicaoModal aoFechar={() => setEditandoModulos(null)}>
          <div className="cartao-modal max-w-sm">
            <div className="cartao-modal-cabecalho">
              <h2 className="text-lg font-semibold text-conteudo">Editar módulos — {editandoModulos.nome}</h2>
              <button onClick={() => setEditandoModulos(null)} className="btn-ghost p-1.5 text-sm">✕</button>
            </div>
            <form onSubmit={handleSubmitModulos} className="cartao-modal-corpo">
              <div className="space-y-2">
                {TODOS_MODULOS.map(modulo => (
                  <label key={modulo} className="flex items-center gap-2.5 text-sm text-conteudo cursor-pointer">
                    <input
                      type="checkbox"
                      checked={modulosSelecionados.has(modulo)}
                      onChange={() => alternarModulo(modulo)}
                      className="w-4 h-4 rounded accent-acento"
                    />
                    {rotuloModulo[modulo]}
                  </label>
                ))}
              </div>
              <div className="flex gap-3 pt-2">
                <button type="button" onClick={() => setEditandoModulos(null)}
                  className="flex-1 py-2.5 rounded-lg border border-borda text-conteudo-suave hover:text-conteudo transition-colors text-sm font-medium">
                  Cancelar
                </button>
                <button type="submit" disabled={mutacaoModulos.isPending} className="flex-1 btn-primary">
                  {mutacaoModulos.isPending ? 'Salvando...' : 'Salvar'}
                </button>
              </div>
            </form>
          </div>
        </SobreposicaoModal>
      )}

      {editandoPlano && (
        <SobreposicaoModal aoFechar={() => setEditandoPlano(null)}>
          <div className="cartao-modal max-w-sm">
            <div className="cartao-modal-cabecalho">
              <h2 className="text-lg font-semibold text-conteudo">Editar plano — {editandoPlano.nome}</h2>
              <button onClick={() => setEditandoPlano(null)} className="btn-ghost p-1.5 text-sm">✕</button>
            </div>
            <form onSubmit={handleSubmitPlano} className="cartao-modal-corpo">
              <div>
                <label className="label">Plano de assinatura</label>
                <select
                  className="input"
                  value={planoSelecionado}
                  onChange={e => setPlanoSelecionado(e.target.value as CodigoPlano)}
                >
                  <option value="INDIVIDUAL">Individual (1 entidade)</option>
                  <option value="FAMILIA">Família (1 entidade)</option>
                  <option value="EMPRESA">Empresa (5 entidades)</option>
                </select>
              </div>
              <div className="flex gap-3 pt-2">
                <button type="button" onClick={() => setEditandoPlano(null)}
                  className="flex-1 py-2.5 rounded-lg border border-borda text-conteudo-suave hover:text-conteudo transition-colors text-sm font-medium">
                  Cancelar
                </button>
                <button type="submit" disabled={mutacaoPlano.isPending} className="flex-1 btn-primary">
                  {mutacaoPlano.isPending ? 'Salvando...' : 'Salvar'}
                </button>
              </div>
            </form>
          </div>
        </SobreposicaoModal>
      )}
    </div>
  )
}
