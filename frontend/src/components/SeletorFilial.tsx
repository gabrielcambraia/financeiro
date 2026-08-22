import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Building2 } from 'lucide-react'
import { listarFiliais } from '../api/filiais'
import { useLojaFilialAtual } from '../store/lojaFilialAtual'

interface Props {
  recolhido?: boolean
  nasSidebar?: boolean
}

export default function SeletorFilial({ recolhido = false, nasSidebar = false }: Props) {
  const queryClient = useQueryClient()
  const { filialAtualId, definirFilialAtual } = useLojaFilialAtual()
  const { data: filiais } = useQuery({ queryKey: ['filiais'], queryFn: listarFiliais })

  if (!filiais || filiais.length < 2) return null

  const trocar = (id: number | null) => {
    definirFilialAtual(id)
    queryClient.invalidateQueries()
  }

  if (recolhido) {
    return (
      <div className="flex justify-center px-2 py-2">
        <button
          title={filialAtualId ? (filiais.find(e => e.id === filialAtualId)?.nome ?? 'Filial') : 'Todas'}
          className="p-1.5 rounded-lg text-sb-suave hover:text-sb-texto hover:bg-sb-hover transition-colors"
        >
          <Building2 size={18} />
        </button>
      </div>
    )
  }

  return (
    <div className="px-2 pb-2">
      <select
        value={filialAtualId ?? ''}
        onChange={e => trocar(e.target.value === '' ? null : Number(e.target.value))}
        className={
          nasSidebar
            ? 'w-full text-xs rounded-lg px-2 py-1.5 border border-sb-borda bg-sb-hover text-sb-texto focus:outline-none focus:ring-1 focus:ring-sb-ativo-barra'
            : 'select text-xs py-1.5'
        }
      >
        <option value="">Todas as filiais</option>
        {filiais.map(e => (
          <option key={e.id} value={e.id}>{e.nome}</option>
        ))}
      </select>
    </div>
  )
}
