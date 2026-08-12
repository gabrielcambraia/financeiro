import axios from 'axios'
import { toast } from 'sonner'
import { useLojaAutenticacao } from '../store/lojaAutenticacao'
import { useLojaEntidadeAtual } from '../store/lojaEntidadeAtual'

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
  const entidadeAtualId = useLojaEntidadeAtual.getState().entidadeAtualId
  if (entidadeAtualId != null && !config.url?.startsWith('/entidades') && !config.url?.startsWith('/consultas')) {
    config.headers['X-Entidade-Id'] = String(entidadeAtualId)
  }
  return config
})

// As chamadas de /auth/renovar e /auth/sair nunca disparam a renovação
// silenciosa abaixo (evitaria loop); usam axios cru para não passar pelo
// interceptor de request (não precisam de Authorization, só do cookie).
const clienteRenovacao = axios.create({ baseURL: '/api', withCredentials: true })

let renovacaoEmAndamento: Promise<string | null> | null = null

const aguardar = (ms: number) => new Promise(resolve => setTimeout(resolve, ms))

// Duas abas podem renovar quase ao mesmo tempo: a que perde a corrida recebe
// 401 "Sessão sendo renovada" (ServicoTokenAtualizacao trata isso como janela
// de graça, não como roubo). Nesse caso o cookie httpOnly já foi atualizado
// pela aba vencedora, então uma única retentativa costuma bastar — só limpa
// a sessão se a segunda tentativa também falhar.
const tentarRenovar = (): Promise<string> =>
  clienteRenovacao.post('/auth/renovar').then(resposta => {
    useLojaAutenticacao.getState().definirSessao(resposta.data)
    return resposta.data.token as string
  })

const renovarSessao = (): Promise<string | null> => {
  if (!renovacaoEmAndamento) {
    renovacaoEmAndamento = tentarRenovar()
      .catch(() => aguardar(1000).then(tentarRenovar))
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
      return Promise.reject(erro)
    }

    // Dispara toast global para qualquer erro não tratado acima (exceto cancelamentos e erros de rede sem resposta)
    const jaFoiTratado = erro.response?.status === 401 || erro.code === 'ERR_CANCELED'
    if (!jaFoiTratado) {
      const mensagem: string = erro.response?.data?.mensagem ?? 'Erro inesperado. Tente novamente.'
      // Anexa mensagem tratada para quem precisar exibir inline (ex: Login, Registro)
      ;(erro as { mensagemUsuario?: string }).mensagemUsuario = mensagem
      toast.error(mensagem)
    }

    return Promise.reject(erro)
  }
)

export default cliente
export { renovarSessao }
