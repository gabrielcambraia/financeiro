export function obterTokenCor(nome: string): string {
  const valor = getComputedStyle(document.documentElement).getPropertyValue(`--${nome}`).trim()
  if (!valor) return '#000000'
  const partes = valor.split(/\s+/).map(Number)
  if (partes.length === 3 && partes.every(n => !isNaN(n))) {
    return `rgb(${partes[0]}, ${partes[1]}, ${partes[2]})`
  }
  return valor
}
