export const iniciaisDoNome = (nome: string) =>
  nome.split(' ').filter(Boolean).slice(0, 2).map(p => p[0]!.toUpperCase()).join('')

export const primeiroNome = (nome: string) =>
  nome.split(' ').filter(Boolean)[0] ?? nome

export const saudacaoPorHora = (data: Date = new Date()): string => {
  const hora = data.getHours()
  if (hora < 12) return 'Bom dia'
  if (hora < 18) return 'Boa tarde'
  return 'Boa noite'
}
