import { ChevronLeft, ChevronRight } from 'lucide-react'

interface Props {
  pagina: number
  totalPaginas: number
  aoMudar: (pagina: number) => void
}

export default function Paginacao({ pagina, totalPaginas, aoMudar }: Props) {
  if (totalPaginas <= 1) return null

  return (
    <div className="flex items-center gap-2 bg-superficie-2 rounded-xl px-3 py-1.5">
      <button
        onClick={() => aoMudar(pagina - 1)}
        disabled={pagina <= 0}
        className="btn-ghost p-1 disabled:opacity-40 disabled:cursor-not-allowed"
      >
        <ChevronLeft size={16} />
      </button>
      <span className="text-sm font-medium text-conteudo min-w-[120px] text-center">
        Página {pagina + 1} de {totalPaginas}
      </span>
      <button
        onClick={() => aoMudar(pagina + 1)}
        disabled={pagina >= totalPaginas - 1}
        className="btn-ghost p-1 disabled:opacity-40 disabled:cursor-not-allowed"
      >
        <ChevronRight size={16} />
      </button>
    </div>
  )
}
