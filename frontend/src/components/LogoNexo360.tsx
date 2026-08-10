interface Props {
  tamanho?: number
}

export default function LogoNexo360({ tamanho = 32 }: Props) {
  return (
    <svg width={tamanho} height={tamanho} viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
      <rect width="32" height="32" rx="8" fill="rgb(17 32 60)" />
      <path d="M9 8L14 17L9 24H12.5L16 19L19.5 24H23L18 17L23 8H19.5L16 14L12.5 8H9Z" fill="white" />
      <circle cx="26" cy="6" r="4" fill="rgb(46 180 100)" />
    </svg>
  )
}
