import { Plus } from 'lucide-react'
import BotaoFlutuante from './BotaoFlutuante'

interface Props {
  aoClicar: () => void
  rotulo: string
}

export default function AcaoNova({ aoClicar, rotulo }: Props) {
  return (
    <>
      <button onClick={aoClicar} className="hidden md:flex btn-primary items-center gap-2">
        <Plus size={16} /> {rotulo}
      </button>
      <BotaoFlutuante aoClicar={aoClicar} rotulo={rotulo} />
    </>
  )
}
