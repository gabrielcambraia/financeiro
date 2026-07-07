import axios from 'axios'
import { useLojaAutenticacao } from '../store/lojaAutenticacao'

const cliente = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
})

cliente.interceptors.request.use(config => {
  const token = useLojaAutenticacao.getState().sessao?.token
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

cliente.interceptors.response.use(
  resposta => resposta,
  erro => {
    if (erro.response?.status === 401) {
      useLojaAutenticacao.getState().limparSessao()
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
