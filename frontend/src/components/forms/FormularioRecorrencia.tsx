import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { X } from 'lucide-react'
import { format } from 'date-fns'
import { buscarContas } from '../../api/contas'
import { buscarCategorias } from '../../api/categorias'
import { buscarCentrosCusto } from '../../api/centrosCusto'
import { buscarCartoes } from '../../api/cartoes'
import { criarRecorrencia, atualizarRecorrencia } from '../../api/recorrencias'
import SobreposicaoModal from '../SobreposicaoModal'
import SeletorFormaPagamento from './SeletorFormaPagamento'
import type { Recorrencia, TipoTransacao, TipoPagamento } from '../../types'

interface Props {
  onClose: () => void
  editing?: Recorrencia
}

export default function FormularioRecorrencia({ onClose, editing }: Props) {
  const qc = useQueryClient()
  const hoje = format(new Date(), 'yyyy-MM-dd')

  const [tipo, setTipo] = useState<TipoTransacao>(editing?.tipo ?? 'DESPESA')
  const [tipoPagamento, setTipoPagamento] = useState<TipoPagamento>(editing?.tipoPagamento ?? 'DEBITO')
  const [form, setForm] = useState({
    contaId: editing?.contaId ?? '',
    cartaoId: editing?.cartaoId ?? '',
    categoriaId: editing?.categoriaId ?? '',
    centroCustoId: (editing?.centroCustoId ?? '') as number | '',
    valor: editing?.valor ?? '',
    descricao: editing?.descricao ?? '',
    diaCompetencia: editing?.diaCompetencia ?? '',
    diaVencimento: (editing?.diaVencimento ?? '') as number | '',
    debitoAutomatico: editing?.debitoAutomatico ?? false,
    dataInicio: editing?.dataInicio ?? hoje.substring(0, 7) + '-01',
    dataFim: editing?.dataFim ?? '',
    ativa: editing?.ativa ?? true,
  })

  const credito = tipo === 'DESPESA' && tipoPagamento === 'CREDITO'
  const debitoAutomaticoHabilitado = tipo === 'DESPESA' && tipoPagamento === 'DEBITO'
  const vencimentoHabilitado = tipo === 'DESPESA' && tipoPagamento === 'DEBITO'

  const set = (campo: string, valor: unknown) => setForm(f => ({ ...f, [campo]: valor }))

  const { data: contas = [] } = useQuery({ queryKey: ['contas'], queryFn: buscarContas })
  const { data: categorias = [] } = useQuery({ queryKey: ['categorias'], queryFn: () => buscarCategorias() })
  const { data: centrosCusto = [] } = useQuery({ queryKey: ['centros-custo'], queryFn: buscarCentrosCusto })
  const { data: cartoes = [] } = useQuery({ queryKey: ['cartoes'], queryFn: buscarCartoes })

  const mutation = useMutation({
    mutationFn: (payload: Parameters<typeof criarRecorrencia>[0]) =>
      editing ? atualizarRecorrencia(editing.id, payload) : criarRecorrencia(payload),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ['recorrencias'] })
      toast.success(editing ? 'Recorrência atualizada' : 'Recorrência criada')
      onClose()
    },
    onError: (e: unknown) => {
      const msg = (e as { response?: { data?: { mensagem?: string } } })?.response?.data?.mensagem
      toast.error(msg ?? 'Erro ao salvar recorrência')
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!form.diaCompetencia || Number(form.diaCompetencia) < 1 || Number(form.diaCompetencia) > 31) {
      toast.error('Dia de competência deve ser entre 1 e 31')
      return
    }
    mutation.mutate({
      tipo,
      tipoPagamento,
      contaId: credito ? null : (form.contaId ? Number(form.contaId) : null),
      cartaoId: credito ? (form.cartaoId ? Number(form.cartaoId) : null) : null,
      categoriaId: form.categoriaId ? Number(form.categoriaId) : null,
      centroCustoId: form.centroCustoId ? Number(form.centroCustoId) : null,
      valor: Number(form.valor),
      descricao: form.descricao || undefined,
      diaCompetencia: Number(form.diaCompetencia),
      diaVencimento: vencimentoHabilitado && form.diaVencimento ? Number(form.diaVencimento) : null,
      debitoAutomatico: debitoAutomaticoHabilitado ? form.debitoAutomatico : false,
      ativa: form.ativa,
      dataInicio: form.dataInicio,
      dataFim: form.dataFim || null,
    })
  }

  const categoriasDoTipo = categorias.filter(c => c.tipo === tipo)

  const trocarTipo = (t: TipoTransacao) => {
    setTipo(t)
    if (t === 'RECEITA') setTipoPagamento('DEBITO')
    set('categoriaId', '')
  }

  return (
    <SobreposicaoModal aoFechar={onClose}>
      <div className="cartao-modal max-w-lg">
        <div className="cartao-modal-cabecalho">
          <h3 className="text-base font-semibold text-conteudo">
            {editing ? 'Editar recorrência' : 'Nova recorrência'}
          </h3>
          <button onClick={onClose} className="p-1.5 rounded-lg text-conteudo-suave hover:text-conteudo hover:bg-superficie-2 transition-colors">
            <X size={16} />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="cartao-modal-corpo">
          {/* Tipo — mesmo toggle segmentado do popup de lançamento, sem a
              opção Transferência (recorrência não suporta). */}
          <div className="flex rounded-xl overflow-hidden border border-borda">
            {(['DESPESA', 'RECEITA'] as TipoTransacao[]).map(t => (
              <button
                key={t}
                type="button"
                onClick={() => trocarTipo(t)}
                className={`flex-1 py-2.5 text-sm font-medium transition-colors
                  ${tipo === t
                    ? t === 'DESPESA' ? 'bg-red-600 text-white' : 'bg-emerald-600 text-white'
                    : 'text-conteudo-suave hover:text-conteudo'}`}
              >
                {t === 'DESPESA' ? 'Despesa' : 'Receita'}
              </button>
            ))}
          </div>

          {/* Forma de pagamento — só se aplica a despesa. */}
          {tipo === 'DESPESA' && (
            <SeletorFormaPagamento valor={tipoPagamento} aoTrocar={setTipoPagamento} />
          )}

          {/* Conta ou Cartão */}
          {credito ? (
            <div>
              <label className="label">Cartão</label>
              <select className="input w-full" value={form.cartaoId}
                onChange={e => set('cartaoId', e.target.value)} required>
                <option value="">Selecione o cartão</option>
                {[...cartoes].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')).map(c => (
                  <option key={c.id} value={c.id}>{c.nome}</option>
                ))}
              </select>
            </div>
          ) : (
            <div>
              <label className="label">Conta</label>
              <select className="input w-full" value={form.contaId}
                onChange={e => set('contaId', e.target.value)} required>
                <option value="">Selecione a conta</option>
                {[...contas].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')).map(c => (
                  <option key={c.id} value={c.id}>{c.nome}</option>
                ))}
              </select>
            </div>
          )}

          {/* Valor e Descrição */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="label">Valor (R$)</label>
              <input className="input" type="number" step="0.01" min="0.01" placeholder="0,00"
                value={form.valor} onChange={e => set('valor', e.target.value)} required />
            </div>
            <div>
              <label className="label">Descrição</label>
              <input className="input" type="text" placeholder="Ex: Aluguel"
                value={form.descricao} onChange={e => set('descricao', e.target.value)} />
            </div>
          </div>

          {/* Categoria e Centro de custo */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="label">Categoria</label>
              <select className="input w-full" value={form.categoriaId}
                onChange={e => set('categoriaId', e.target.value)}>
                <option value="">Sem categoria</option>
                {[...categoriasDoTipo].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')).map(c => (
                  <option key={c.id} value={c.id}>{c.nome}</option>
                ))}
              </select>
            </div>
            {centrosCusto.length > 0 && (
              <div>
                <label className="label">Centro de Custo</label>
                <select className="input w-full" value={form.centroCustoId}
                  onChange={e => set('centroCustoId', e.target.value ? Number(e.target.value) : '')}>
                  <option value="">Sem centro de custo</option>
                  {[...centrosCusto].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')).map(cc => (
                    <option key={cc.id} value={cc.id}>{cc.nome}</option>
                  ))}
                </select>
              </div>
            )}
          </div>

          {/* Dias */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="label">Dia de competência (1–31)</label>
              <input className="input" type="number" min="1" max="31" placeholder="Ex: 5"
                value={form.diaCompetencia} onChange={e => set('diaCompetencia', e.target.value)} required />
              <p className="text-xs text-conteudo-suave mt-1">Dia do mês em que a transação será lançada</p>
            </div>
            {vencimentoHabilitado && (
              <div>
                <label className="label">Dia de vencimento (opcional)</label>
                <input className="input" type="number" min="1" max="31" placeholder="Igual ao competência"
                  value={form.diaVencimento} onChange={e => set('diaVencimento', e.target.value ? Number(e.target.value) : '')} />
                <p className="text-xs text-conteudo-suave mt-1">Se diferente do dia de competência</p>
              </div>
            )}
          </div>

          {/* Período */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="label">Data de início</label>
              <input className="input" type="date"
                value={form.dataInicio} onChange={e => set('dataInicio', e.target.value)} required />
            </div>
            <div>
              <label className="label">Data de fim (opcional)</label>
              <input className="input" type="date" min={form.dataInicio}
                value={form.dataFim} onChange={e => set('dataFim', e.target.value)} />
            </div>
          </div>

          {/* Débito automático */}
          {debitoAutomaticoHabilitado && (
            <label className="flex items-center gap-2 cursor-pointer">
              <input type="checkbox" className="w-4 h-4 rounded accent-acento"
                checked={form.debitoAutomatico}
                onChange={e => set('debitoAutomatico', e.target.checked)} />
              <span className="text-sm text-conteudo">Débito automático</span>
            </label>
          )}

          {/* Ativa (só edição) */}
          {editing && (
            <label className="flex items-center gap-2 cursor-pointer">
              <input type="checkbox" className="w-4 h-4 rounded accent-acento"
                checked={form.ativa}
                onChange={e => set('ativa', e.target.checked)} />
              <span className="text-sm text-conteudo">Recorrência ativa</span>
            </label>
          )}

          <div className="flex gap-3 pt-2">
            <button type="button" onClick={onClose}
              className="flex-1 py-2.5 rounded-lg border border-borda text-conteudo-suave hover:text-conteudo transition-colors text-sm font-medium">
              Cancelar
            </button>
            <button type="submit" disabled={mutation.isPending} className="flex-1 btn-primary">
              {mutation.isPending ? 'Salvando...' : editing ? 'Salvar' : 'Criar recorrência'}
            </button>
          </div>
        </form>
      </div>
    </SobreposicaoModal>
  )
}
