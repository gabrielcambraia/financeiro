import axios from 'axios'
import { useLojaAutenticacao } from '../store/lojaAutenticacao'

const cliente = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
  withCredentials: true,
})

cliente.interceptors.request.use(config => {
  const token = useLojaAutenticacao.getState().sessao?.token
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// As chamadas de /auth/renovar e /auth/sair nunca disparam a renovação
// silenciosa abaixo (evitaria loop); usam axios cru para não passar pelo
// interceptor de request (não precisam de Authorization, só do cookie).
const clienteRenovacao = axios.create({ baseURL: '/api', withCredentials: true })

let renovacaoEmAndamento: Promise<string | null> | null = null

const renovarSessao = (): Promise<string | null> => {
  if (!renovacaoEmAndamento) {
    renovacaoEmAndamento = clienteRenovacao.post('/auth/renovar')
      .then(resposta => {
        useLojaAutenticacao.getState().definirSessao(resposta.data)
        return resposta.data.token as string
      })
      .catch(() => {
        useLojaAutenticacao.getState().limparSessao()
        return null
      })
      .finally(() => {
        renovacaoEmAndamento = null
      })
  }
  return renovacaoEmAndamento
}

const ROTAS_SEM_RENOVACAO = ['/auth/login', '/auth/register', '/auth/renovar', '/auth/sair']

cliente.interceptors.response.use(
  resposta => resposta,
  async erro => {
    const requisicaoOriginal = erro.config
    const rotaSemRenovacao = ROTAS_SEM_RENOVACAO.some(rota => requisicaoOriginal?.url?.includes(rota))

    if (erro.response?.status === 401 && !rotaSemRenovacao && !requisicaoOriginal._retry) {
      requisicaoOriginal._retry = true
      const novoToken = await renovarSessao()
      if (novoToken) {
        requisicaoOriginal.headers.Authorization = `Bearer ${novoToken}`
        return cliente(requisicaoOriginal)
      }
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    }

    if (erro.response?.status === 403 && erro.response?.data?.codigo === 'SENHA_TEMPORARIA') {
      if (window.location.pathname !== '/trocar-senha') {
        window.location.href = '/trocar-senha'
      }
    }
    return Promise.reject(erro)
  }
)

export default cliente
export { renovarSessao }
