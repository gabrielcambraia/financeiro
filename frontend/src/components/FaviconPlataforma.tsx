import { useEffect } from 'react'
import { useConfiguracaoPlataforma } from '../hooks/useConfiguracaoPlataforma'
import { logoPlataformaUrl } from '../api/configuracaoPlataforma'

const HREF_PADRAO = '/favicon.svg'

// Sem UI própria: só troca o <link rel="icon"> do index.html pela logo
// enviada pelo admin, quando existir. Fica fora de RotaProtegida porque o
// favicon deve valer em qualquer tela, inclusive o login.
export default function FaviconPlataforma() {
  const { data } = useConfiguracaoPlataforma()

  useEffect(() => {
    const link = document.getElementById('favicon') as HTMLLinkElement | null
    if (!link) return
    if (data?.temLogo) {
      link.removeAttribute('type')
      link.href = logoPlataformaUrl()
    } else {
      link.setAttribute('type', 'image/svg+xml')
      link.href = HREF_PADRAO
    }
  }, [data?.temLogo])

  return null
}
