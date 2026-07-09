import { Plus, type LucideIcon } from 'lucide-react'

interface Props {
  aoClicar: () => void
  icon?: LucideIcon
  rotulo?: string
}

export default function BotaoFlutuante({ aoClicar, icon: Icon = Plus, rotulo = 'Adicionar' }: Props) {
  return (
    <button
      onClick={aoClicar}
      aria-label={rotulo}
      className="md:hidden fixed right-4 bottom-20 z-40 w-14 h-14 rounded-2xl bg-acento text-white shadow-lg flex items-center justify-center"
      style={{ marginBottom: 'env(safe-area-inset-bottom)' }}
    >
      <Icon size={24} />
    </button>
  )
}
