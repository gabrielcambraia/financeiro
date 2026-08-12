export function apenasDigitos(v: string): string {
  return v.replace(/\D/g, '')
}

export function limparCnpj(v: string): string {
  return v.replace(/[.\-/]/g, '').toUpperCase()
}

export function validarCpf(cpf: string): boolean {
  if (cpf.length !== 11 || /^(.)\1+$/.test(cpf)) return false
  let s1 = 0, s2 = 0
  for (let i = 0; i < 9; i++) s1 += parseInt(cpf[i]) * (10 - i)
  const d1 = s1 % 11 < 2 ? 0 : 11 - s1 % 11
  for (let i = 0; i < 10; i++) s2 += parseInt(cpf[i]) * (11 - i)
  const d2 = s2 % 11 < 2 ? 0 : 11 - s2 % 11
  return d1 === parseInt(cpf[9]) && d2 === parseInt(cpf[10])
}

export function validarCnpj(cnpj: string): boolean {
  if (cnpj.length !== 14 || /^(.)\1+$/.test(cnpj)) return false
  if (!/^[A-Z0-9]{12}\d{2}$/.test(cnpj)) return false
  const p1 = [5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]
  const p2 = [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]
  let s1 = 0, s2 = 0
  for (let i = 0; i < 12; i++) s1 += (cnpj.charCodeAt(i) - 48) * p1[i]
  const d1 = s1 % 11 < 2 ? 0 : 11 - s1 % 11
  for (let i = 0; i < 13; i++) s2 += (cnpj.charCodeAt(i) - 48) * p2[i]
  const d2 = s2 % 11 < 2 ? 0 : 11 - s2 % 11
  return d1 === parseInt(cnpj[12]) && d2 === parseInt(cnpj[13])
}

export function formatarCpf(v: string): string {
  const d = apenasDigitos(v)
  if (d.length !== 11) return v
  return `${d.slice(0, 3)}.${d.slice(3, 6)}.${d.slice(6, 9)}-${d.slice(9)}`
}

export function formatarCnpj(v: string): string {
  const d = limparCnpj(v)
  if (d.length !== 14) return v
  return `${d.slice(0, 2)}.${d.slice(2, 5)}.${d.slice(5, 8)}/${d.slice(8, 12)}-${d.slice(12)}`
}
