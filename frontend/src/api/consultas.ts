import cliente from './cliente'

export interface DadosConsultaCnpj {
  razaoSocial: string
  nomeFantasia: string | null
  email: string | null
  telefone: string | null
  cep: string | null
  logradouro: string | null
  numero: string | null
  complemento: string | null
  bairro: string | null
  cidade: string | null
  uf: string | null
}

export interface DadosConsultaCep {
  cep: string
  logradouro: string | null
  bairro: string | null
  cidade: string | null
  uf: string | null
}

export async function consultarCnpj(cnpj: string): Promise<DadosConsultaCnpj> {
  const { data } = await cliente.get<DadosConsultaCnpj>(`/consultas/cnpj/${cnpj}`)
  return data
}

export async function consultarCep(cep: string): Promise<DadosConsultaCep> {
  const { data } = await cliente.get<DadosConsultaCep>(`/consultas/cep/${cep}`)
  return data
}
