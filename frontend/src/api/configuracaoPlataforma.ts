import cliente from './cliente'

export interface RespostaConfiguracaoPlataforma {
  temLogo: boolean
  temLogoLogin: boolean
}

export const obterConfiguracaoPlataforma = () =>
  cliente.get<RespostaConfiguracaoPlataforma>('/configuracao-plataforma').then(r => r.data)

export const enviarLogoPlataforma = (arquivo: File) => {
  const formData = new FormData()
  formData.append('arquivo', arquivo)
  return cliente.post<RespostaConfiguracaoPlataforma>('/configuracao-plataforma/logo', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  }).then(r => r.data)
}

export const removerLogoPlataforma = () =>
  cliente.delete<RespostaConfiguracaoPlataforma>('/configuracao-plataforma/logo').then(r => r.data)

// Usado direto em <img src> / <link rel="icon">, fora do axios — a rota é
// pública (ver ConfiguracaoSeguranca), pois essas tags não mandam Authorization.
export const logoPlataformaUrl = () => '/api/configuracao-plataforma/logo'

// Banner exibido na tela de login — slot independente da logo acima.
export const enviarLogoLoginPlataforma = (arquivo: File) => {
  const formData = new FormData()
  formData.append('arquivo', arquivo)
  return cliente.post<RespostaConfiguracaoPlataforma>('/configuracao-plataforma/logo-login', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  }).then(r => r.data)
}

export const removerLogoLoginPlataforma = () =>
  cliente.delete<RespostaConfiguracaoPlataforma>('/configuracao-plataforma/logo-login').then(r => r.data)

export const logoLoginPlataformaUrl = () => '/api/configuracao-plataforma/logo-login'
