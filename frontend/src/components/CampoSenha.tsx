import { useState, type InputHTMLAttributes } from 'react'
import { Eye, EyeOff } from 'lucide-react'

type PropsCampoSenha = Omit<InputHTMLAttributes<HTMLInputElement>, 'type'>

export default function CampoSenha(props: PropsCampoSenha) {
  const [visivel, setVisivel] = useState(false)

  return (
    <div className="relative">
      <input
        {...props}
        type={visivel ? 'text' : 'password'}
        className={`${props.className ?? 'input'} pr-10`}
      />
      <button
        type="button"
        onClick={() => setVisivel(v => !v)}
        tabIndex={-1}
        className="absolute right-3 top-1/2 -translate-y-1/2 text-conteudo-suave hover:text-conteudo transition-colors"
        aria-label={visivel ? 'Ocultar senha' : 'Mostrar senha'}
      >
        {visivel ? <EyeOff size={16} /> : <Eye size={16} />}
      </button>
    </div>
  )
}
