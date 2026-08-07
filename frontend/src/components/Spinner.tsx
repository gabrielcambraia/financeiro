interface Props {
  tamanho?: 'sm' | 'md' | 'lg'
  className?: string
}

const tamanhos = { sm: 'h-4 w-4', md: 'h-8 w-8', lg: 'h-10 w-10' }

export default function Spinner({ tamanho = 'md', className = '' }: Props) {
  return (
    <div
      className={`animate-spin rounded-full border-b-2 border-acento ${tamanhos[tamanho]} ${className}`}
    />
  )
}
