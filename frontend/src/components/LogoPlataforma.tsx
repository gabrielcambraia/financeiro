import { useConfiguracaoPlataforma } from '../hooks/useConfiguracaoPlataforma'
import { logoPlataformaUrl } from '../api/configuracaoPlataforma'
import LogoNexus360 from './LogoNexus360'

interface Props {
  tamanho?: number
  mostrarNome?: boolean
  classeNome?: string
}

// Ícone da barra lateral — a mesma imagem enviada pelo admin em Perfil >
// Logo da plataforma (mesma configuração usada pelo favicon, ver
// FaviconPlataforma.tsx). Sem upload, cai no fallback do ícone Nexus360.
// O nome "Nexus360" é sempre escrito ao lado quando mostrarNome=true,
// independente de haver ícone customizado. A tela de login usa seu próprio
// slot de logo (ver LogoLogin.tsx), não este componente.
export default function LogoPlataforma({ tamanho = 32, mostrarNome = true, classeNome = '' }: Props) {
  const { data } = useConfiguracaoPlataforma()

  return (
    <>
      {data?.temLogo ? (
        <img
          src={logoPlataformaUrl()}
          alt="Logo"
          style={{ width: tamanho, height: tamanho }}
          className="object-contain shrink-0"
        />
      ) : (
        <LogoNexus360 tamanho={tamanho} />
      )}
      {mostrarNome && <span className={classeNome}>Nexus360</span>}
    </>
  )
}
