import type { ButtonHTMLAttributes } from 'react'
import Spinner from './Spinner'

type Variante = 'primario' | 'secundario' | 'perigo' | 'atencao' | 'neutro'

interface Props extends ButtonHTMLAttributes<HTMLButtonElement> {
  carregando?: boolean
  variante?: Variante
}

const classes: Record<Variante, string> = {
  primario: 'btn-primary',
  secundario: 'py-2 px-4 rounded-lg border border-borda text-conteudo-suave hover:text-conteudo transition-colors text-sm font-medium',
  perigo: 'py-2 px-4 rounded-lg border border-perigo text-perigo hover:bg-perigo/10 transition-colors text-sm font-medium',
  atencao: 'py-2 px-4 rounded-lg border border-aviso text-aviso hover:bg-aviso/10 transition-colors text-sm font-medium',
  neutro: 'w-full py-2 px-4 rounded-lg border border-borda text-conteudo-suave hover:bg-superficie-2 transition-colors text-sm',
}

export default function Botao({
  carregando = false,
  variante = 'primario',
  disabled,
  children,
  className = '',
  ...rest
}: Props) {
  const desabilitado = carregando || disabled

  return (
    <button
      disabled={desabilitado}
      className={`${classes[variante]} ${desabilitado ? 'opacity-60 cursor-not-allowed' : ''} inline-flex items-center justify-center gap-2 ${className}`}
      {...rest}
    >
      {carregando && <Spinner tamanho="sm" />}
      {children}
    </button>
  )
}
