import { useConfiguracaoPlataforma } from '../hooks/useConfiguracaoPlataforma'
import { logoLoginPlataformaUrl } from '../api/configuracaoPlataforma'
import LogoNexus360 from './LogoNexus360'

interface Props {
  altura?: number
  classeNome?: string
  // 'grande': painel esquerdo do desktop — o banner ocupa a largura do
  // bloco de texto abaixo (título "Controle total da sua vida financeira."),
  // em vez de ficar do tamanho de um ícone ao lado do nome.
  variante?: 'compacta' | 'grande'
}

// Banner exibido na tela de login — slot próprio (Perfil > Logo da tela de
// login), independente do ícone da barra lateral (ver LogoPlataforma.tsx),
// já que costuma ser uma peça de marca larga (com nome/tagline embutidos),
// não um ícone quadrado. Sem upload, cai no ícone+nome padrão.
export default function LogoLogin({ altura = 36, classeNome = '', variante = 'compacta' }: Props) {
  const { data } = useConfiguracaoPlataforma()

  if (data?.temLogoLogin) {
    return variante === 'grande' ? (
      <img
        src={logoLoginPlataformaUrl()}
        alt="Nexus360"
        className="w-full max-w-[380px] h-auto object-contain"
      />
    ) : (
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
