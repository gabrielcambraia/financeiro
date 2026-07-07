import { ChevronLeft, ChevronRight } from 'lucide-react'
import { format, addMonths, subMonths, parseISO } from 'date-fns'
import { ptBR } from 'date-fns/locale'
import { useLojaFiltro } from '../store/lojaFiltro'

export default function SeletorMes() {
  const { mes, definirMes } = useLojaFiltro()
  const data = parseISO(`${mes}-01`)

  return (
    <div className="flex items-center gap-2 bg-gray-800 rounded-xl px-3 py-1.5">
      <button onClick={() => definirMes(format(subMonths(data, 1), 'yyyy-MM'))} className="btn-ghost p-1">
        <ChevronLeft size={16} />
      </button>
      <span className="text-sm font-medium text-white min-w-[120px] text-center capitalize">
        {format(data, 'MMMM yyyy', { locale: ptBR })}
      </span>
      <button onClick={() => definirMes(format(addMonths(data, 1), 'yyyy-MM'))} className="btn-ghost p-1">
        <ChevronRight size={16} />
      </button>
    </div>
  )
}
