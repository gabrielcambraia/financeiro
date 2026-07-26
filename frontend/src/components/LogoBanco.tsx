import { useState, useEffect } from 'react'
import { logoBancoUrl } from '../api/bancos'
import type { Banco } from '../types'

interface Props {
  banco?: Banco
  tamanho?: number
}

// Se o banco tem logo cadastrado (upload feito pelo admin), mostra a imagem
// real; senão cai num badge com a cor da marca + sigla.
export default function LogoBanco({ banco, tamanho = 40 }: Props) {
  const [erro, setErro] = useState(false)
  useEffect(() => setErro(false), [banco?.id])

  if (!banco || !banco.temLogo || erro) {
    return (
      <div
        className="rounded-xl flex items-center justify-center text-white font-bold shrink-0 uppercase"
        style={{ width: tamanho, height: tamanho, background: banco?.corPrimaria ?? '#6b7280', fontSize: tamanho * 0.32 }}
      >
        {(banco?.sigla ?? '?').slice(0, 3)}
      </div>
    )
  }

  return (
    <img
      src={logoBancoUrl(banco.id)}
      alt={banco.nome}
      onError={() => setErro(true)}
      className="rounded-xl object-cover shrink-0"
      style={{ width: tamanho, height: tamanho }}
    />
  )
}
