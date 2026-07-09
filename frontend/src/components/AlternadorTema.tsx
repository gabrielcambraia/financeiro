import { Sun, Moon } from 'lucide-react'
import { useLojaTema } from '../store/lojaTema'

export default function AlternadorTema() {
  const tema = useLojaTema(s => s.tema)
  const alternarTema = useLojaTema(s => s.alternarTema)

  return (
    <button
      onClick={alternarTema}
      aria-label="Alternar tema"
      className="p-2 rounded-xl bg-superficie-2 text-conteudo-suave hover:text-conteudo transition-colors"
    >
      {tema === 'claro' ? <Moon size={18} /> : <Sun size={18} />}
    </button>
  )
}
