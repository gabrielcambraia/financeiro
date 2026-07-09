import { createPortal } from 'react-dom'
import type { ReactNode } from 'react'

interface Props {
  children: ReactNode
  aoFechar?: () => void
}

export default function SobreposicaoModal({ children, aoFechar }: Props) {
  return createPortal(
    <div
      className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-end md:items-center justify-center z-50 p-0 md:p-4"
      onClick={e => { if (e.target === e.currentTarget) aoFechar?.() }}
    >
      {children}
    </div>,
    document.body,
  )
}
