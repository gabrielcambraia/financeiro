import type { ReactNode } from 'react'
import SobreposicaoModal from './SobreposicaoModal'
import Botao from './Botao'
import type { RespostaImpacto } from '../types'

const fmt = (v: number) =>
  new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(v)

type BotaoModal = {
  rotulo: string
  variante: 'perigo' | 'atencao' | 'neutro'
  aoClicar: () => void
  carregando?: boolean
}

interface Props {
  aberto: boolean
  titulo: string
  aoFechar: () => void
  botoes?: BotaoModal[]
  carregandoImpacto?: boolean
  impacto?: RespostaImpacto | null
  children?: ReactNode
}

export default function ModalConfirmacao({
  aberto, titulo, aoFechar, botoes = [], carregandoImpacto, impacto, children,
}: Props) {
  if (!aberto) return null

  const algumCarregando = botoes.some(b => b.carregando)

  return (
    <SobreposicaoModal aoFechar={aoFechar}>
      <div className="cartao-modal w-full max-w-md">
        <div className="cartao-modal-cabecalho">
          <span className="font-semibold text-conteudo">{titulo}</span>
        </div>
        <div className="cartao-modal-corpo space-y-4">
          {children && <div className="text-sm text-conteudo-suave">{children}</div>}

          {carregandoImpacto && (
            <p className="text-sm text-conteudo-suave animate-pulse">Verificando impacto...</p>
          )}

          {impacto?.bloqueado && (
            <div className="rounded-lg border border-red-800/50 bg-red-900/20 p-3 text-sm text-red-400">
              {impacto.motivoBloqueio}
            </div>
          )}

          {!impacto?.bloqueado && impacto?.origem && (
            <div className="rounded-lg border border-orange-800/50 bg-orange-900/20 p-3 text-sm text-orange-400">
              ⚠ {impacto.origem.efeito}
            </div>
          )}

          {!impacto?.bloqueado && (impacto?.itensAfetados?.length ?? 0) > 0 && (
            <div className="space-y-1">
              <p className="text-xs font-medium text-conteudo-suave uppercase tracking-wide">
                Lançamentos afetados
              </p>
              <div className="max-h-48 overflow-y-auto space-y-1">
                {impacto!.itensAfetados.map((item, i) => (
                  <div key={i} className="flex items-center justify-between text-xs text-conteudo-suave bg-superficie-2 rounded px-2 py-1">
                    <span className="truncate">{item.descricao}</span>
                    <span className="ml-2 shrink-0 font-medium text-conteudo">{fmt(item.valor)}</span>
                  </div>
                ))}
              </div>
            </div>
          )}

          <div className="space-y-2 pt-1">
            {!impacto?.bloqueado && botoes.map((b, i) => (
              <Botao key={i} variante={b.variante} carregando={b.carregando} onClick={b.aoClicar} className="w-full">
                {b.rotulo}
              </Botao>
            ))}
            <Botao variante="neutro" disabled={algumCarregando} onClick={aoFechar}>
              Voltar
            </Botao>
          </div>
        </div>
      </div>
    </SobreposicaoModal>
  )
}
