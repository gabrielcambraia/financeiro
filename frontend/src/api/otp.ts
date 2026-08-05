import cliente from './cliente'
import type { RespostaAutenticacao } from './autenticacao'

export const solicitarOtp = (email: string) =>
  cliente.post('/auth/otp/solicitar', { email })

export const verificarOtp = (email: string, codigo: string) =>
  cliente.post<RespostaAutenticacao>('/auth/otp/verificar', { email, codigo }).then(r => r.data)
