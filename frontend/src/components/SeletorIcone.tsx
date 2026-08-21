import type { LucideIcon } from 'lucide-react'

interface Props {
  icones: { nome: string; Icone: LucideIcon }[]
  iconeSelecionado: string
  aoSelecionar: (nome: string) => void
}

export default function SeletorIcone({ icones, iconeSelecionado, aoSelecionar }: Props) {
  return (
    <div className="flex gap-2 flex-wrap">
      {icones.map(({ nome, Icone }) => (
        <button key={nome} type="button" onClick={() => aoSelecionar(nome)}
          className={`w-9 h-9 rounded-lg border-2 flex items-center justify-center transition-all
            ${iconeSelecionado === nome ? 'border-conteudo scale-110 text-conteudo' : 'border-borda text-conteudo-suave hover:border-conteudo/50'}`}>
          <Icone size={18} />
        </button>
      ))}
    </div>
  )
}
