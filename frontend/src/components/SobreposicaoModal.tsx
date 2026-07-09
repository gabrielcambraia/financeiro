import { createPortal } from 'react-dom'
import type { ReactNode } from 'react'

interface Props {
  children: ReactNode
}

export default function SobreposicaoModal({ children }: Props) {
  return createPortal(
    <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 p-4">
      {children}
    </div>,
    document.body,
  )
}
