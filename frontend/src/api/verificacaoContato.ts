import cliente from './cliente'

export const solicitarVerificacaoEmail = () =>
  cliente.post('/auth/verificar-contato/email/solicitar')

export const confirmarVerificacaoEmail = (codigo: string) =>
  cliente.post('/auth/verificar-contato/email/confirmar', { codigo })

export const solicitarVerificacaoTelefone = () =>
  cliente.post('/auth/verificar-contato/telefone/solicitar')

export const confirmarVerificacaoTelefone = (codigo: string) =>
  cliente.post('/auth/verificar-contato/telefone/confirmar', { codigo })
