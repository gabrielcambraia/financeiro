import { useConfiguracaoPlataforma } from '../hooks/useConfiguracaoPlataforma'
import { logoLoginPlataformaUrl } from '../api/configuracaoPlataforma'
import LogoNexus360 from './LogoNexus360'

interface Props {
  altura?: number
  classeNome?: string
}

// Banner exibido na tela de login — slot próprio (Perfil > Logo da tela de
// login), independente do ícone da barra lateral (ver LogoPlataforma.tsx),
// já que costuma ser uma peça de marca larga (com nome/tagline embutidos),
// não um ícone quadrado. Sem upload, cai no ícone+nome padrão.
export default function LogoLogin({ altura = 36, classeNome = '' }: Props) {
  const { data } = useConfiguracaoPlataforma()

  if (data?.temLogoLogin) {
    return (
      <img
        src={logoLoginPlataformaUrl()}
        alt="Nexus360"
        style={{ height: altura }}
        className="w-auto object-contain"
      />
    )
  }

  return (
    <>
      <LogoNexus360 tamanho={altura} />
      <span className={classeNome}>Nexus360</span>
    </>
  )
}
