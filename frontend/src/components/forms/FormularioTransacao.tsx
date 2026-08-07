import { useState, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { X } from 'lucide-react'
import { format } from 'date-fns'
import { buscarContas } from '../../api/contas'
import { buscarCategorias } from '../../api/categorias'
import { buscarCartoes } from '../../api/cartoes'
import { criarTransacao, atualizarTransacao, type EscopoAtualizacao } from '../../api/transacoes'
import { criarItemFatura, atualizarItemFatura } from '../../api/itensFatura'
import SobreposicaoModal from '../SobreposicaoModal'
import CampoEntidade from './CampoEntidade'
import type { Transacao, ItemFatura, TipoTransacao, TipoPagamento } from '../../types'

// A tela de Lançamentos mescla dois tipos de registro (ver Transacoes.tsx):
// Transacao (débito, amarrada a uma conta) e ItemFatura (crédito, amarrado a
// um cartão, sem vencimento/pagamento — vira despesa na conta de pagamento
// só quando a fatura fecha). Este form decide qual API chamar conforme o
// tipo/forma de pagamento escolhidos.
export type EdicaoLancamento =
  | { origem: 'TRANSACAO'; tx: Transacao }
  | { origem: 'ITEM_FATURA'; item: ItemFatura }

const fmtParcela = (valor: string | number, totalParcelas: string | number) => {
  const n = Number(totalParcelas)
  const v = Number(valor) / n
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(v || 0)
}

interface Props {
  onClose: () => void
  editing?: EdicaoLancamento
}

export default function FormularioTransacao({ onClose, editing }: Props) {
  const qc = useQueryClient()
  const editingTx = editing?.origem === 'TRANSACAO' ? editing.tx : undefined
  const editingItem = editing?.origem === 'ITEM_FATURA' ? editing.item : undefined

  const [tipo, setTipo] = useState<TipoTransacao>(editingTx?.tipo ?? 'DESPESA')
  const hoje = format(new Date(), 'yyyy-MM-dd')
  const dataInicial = editingTx?.data ?? editingItem?.data ?? hoje
  // Numa transferência, a linha editada pode ser a perna de saída ou a de
  // entrada (o usuário pode clicar em qualquer uma na lista) — normaliza para
  // sempre mostrar origem/destino na ordem certa, independente de qual foi clicada.
  const contaOrigemInicial = editingTx?.tipo === 'TRANSFERENCIA'
    ? (editingTx.direcaoTransferencia === 'SAIDA' ? editingTx.contaId : editingTx.contaVinculada?.id) ?? ''
    : editingTx?.contaId ?? ''
  const contaDestinoInicial = editingTx?.tipo === 'TRANSFERENCIA'
    ? (editingTx.direcaoTransferencia === 'SAIDA' ? editingTx.contaVinculada?.id : editingTx.contaId) ?? ''
    : ''
  const [form, setForm] = useState({
    contaId: contaOrigemInicial,
    contaDestinoId: contaDestinoInicial,
    cartaoId: editingItem?.cartaoId ?? '',
    categoriaId: editingTx?.categoriaId ?? editingItem?.categoriaId ?? '',
    tipoPagamento: (editingTx?.tipoPagamento ?? (editingItem ? 'CREDITO' : 'DEBITO')) as TipoPagamento,
    valor: editingTx?.valor ?? editingItem?.valor ?? '',
    descricao: editingTx?.descricao ?? editingItem?.descricao ?? '',
    data: dataInicial,
    dataVencimento: editingTx?.dataVencimento ?? dataInicial,
    // Vazio = pendente. Ao criar, nasce preenchida se a data já chegou (mantém
    // o fluxo leve de hoje); o usuário pode desmarcar para deixar a pagar.
    dataPagamento: editingTx ? (editingTx.dataPagamento ?? '') : (dataInicial <= hoje ? dataInicial : ''),
    fixa: editingTx?.fixa ?? false,
    totalParcelas: editingTx?.totalParcelas ?? editingItem?.totalParcelas ?? '',
    entidadeId: (editingTx?.entidadeId ?? null) as number | null | undefined,
  })
  const paga = form.dataPagamento !== ''
  // Uma Transacao legada com tipoPagamento=CREDITO (criada antes desta
  // mudança) continua editável como Transacao normal — só uma compra que já
  // nasceu como ItemFatura (ou uma nova sendo criada em crédito) usa o modo
  // "cartão" do formulário.
  const credito = editingTx ? false : (editingItem ? true : (tipo === 'DESPESA' && form.tipoPagamento === 'CREDITO'))

  const { data: contas = [] } = useQuery({ queryKey: ['contas'], queryFn: buscarContas })
  const { data: cartoes = [] } = useQuery({ queryKey: ['cartoes'], queryFn: buscarCartoes, enabled: credito })

  useEffect(() => {
    if (!editing && contas.length === 1 && !form.contaId) {
      set('contaId', contas[0].id)
    }
  }, [contas])

  useEffect(() => {
    if (!editing && credito && cartoes.length === 1 && !form.cartaoId) {
      set('cartaoId', cartoes[0].id)
    }
  }, [credito, cartoes])

  // Receita não pode ser em crédito (não faz sentido) — força débito ao trocar
  // para Receita ou Transferência.
  useEffect(() => {
    if (tipo !== 'DESPESA' && form.tipoPagamento === 'CREDITO') {
      set('tipoPagamento', 'DEBITO')
    }
  }, [tipo])

  const { data: categorias = [] } = useQuery({
    queryKey: ['categorias', tipo],
    queryFn: () => buscarCategorias(tipo),
    enabled: tipo !== 'TRANSFERENCIA',
  })

  const invalidarTudo = async () => {
    await Promise.all([
      qc.invalidateQueries({ queryKey: ['transacoes'] }),
      qc.invalidateQueries({ queryKey: ['itensFatura'] }),
      qc.invalidateQueries({ queryKey: ['painel'] }),
      qc.invalidateQueries({ queryKey: ['contas'] }),
      qc.invalidateQueries({ queryKey: ['cartoes'] }),
    ])
  }

  const mutation = useMutation({
    mutationFn: async () => {
      if (credito) {
        const payload = {
          cartaoId: Number(form.cartaoId),
          categoriaId: form.categoriaId ? Number(form.categoriaId) : undefined,
          valor: Number(form.valor),
          descricao: form.descricao || undefined,
          data: form.data,
          totalParcelas: editingItem ? undefined : (form.totalParcelas ? Number(form.totalParcelas) : undefined),
        }
        if (editingItem) { await atualizarItemFatura(editingItem.id, payload); return }
        await criarItemFatura(payload)
        return
      }
      const payload = {
        contaId: Number(form.contaId),
        contaDestinoId: tipo === 'TRANSFERENCIA' ? Number(form.contaDestinoId) : undefined,
        categoriaId: form.categoriaId ? Number(form.categoriaId) : undefined,
        tipo,
        tipoPagamento: form.tipoPagamento,
        valor: Number(form.valor),
        descricao: form.descricao || undefined,
        data: form.data,
        dataVencimento: form.dataVencimento || form.data,
        dataPagamento: paga ? (form.dataPagamento || form.data) : undefined,
        quitarNaCriacao: paga,
        fixa: tipo === 'TRANSFERENCIA' ? false : form.fixa,
        totalParcelas: tipo === 'TRANSFERENCIA' ? undefined : (form.totalParcelas ? Number(form.totalParcelas) : undefined),
        entidadeId: form.entidadeId ?? null,
      }
      if (editingTx) { await atualizarTransacao(editingTx.id, payload, editingTx.fixa ? escopoEdicao : 'UNICA'); return }
      await criarTransacao(payload)
    },
    onSuccess: async () => {
      await invalidarTudo()
      toast.success('Transação salva')
      onClose()
    },
    onError: (e: unknown) => {
      const msg = (e as { response?: { data?: { mensagem?: string } } })?.response?.data?.mensagem
      setErro(msg ?? 'Erro ao salvar. Tente novamente.')
    },
  })

  const [escopoEdicao, setEscopoEdicao] = useState<EscopoAtualizacao>('UNICA')
  const [erro, setErro] = useState('')

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (tipo === 'TRANSFERENCIA' && Number(form.contaId) === Number(form.contaDestinoId)) {
      setErro('Conta destino deve ser diferente da conta origem')
      return
    }
    setErro('')
    mutation.mutate()
  }

  const set = (k: string, v: unknown) => setForm(f => ({ ...f, [k]: v }))

  return (
    <SobreposicaoModal aoFechar={onClose}>
      <div className="cartao-modal max-w-lg">
        <div className="cartao-modal-cabecalho">
          <h2 className="text-lg font-semibold text-conteudo">
            {editing ? 'Editar Lançamento' : 'Novo Lançamento'}
          </h2>
          <button onClick={onClose} className="btn-ghost p-1.5"><X size={18} /></button>
        </div>

        <form onSubmit={handleSubmit} className="cartao-modal-corpo">
          <CampoEntidade value={form.entidadeId} onChange={v => set('entidadeId', v)} />

          {/* Tipo */}
          <div className="flex rounded-xl overflow-hidden border border-borda">
            {(['DESPESA', 'RECEITA', 'TRANSFERENCIA'] as TipoTransacao[]).map(t => {
              const desabilitado = !!editing && (editingTx?.tipo === 'TRANSFERENCIA' ? t !== 'TRANSFERENCIA' : t === 'TRANSFERENCIA')
              return (
                <button
                  key={t}
                  type="button"
                  disabled={desabilitado}
                  onClick={() => { setTipo(t); set('categoriaId', '') }}
                  className={`flex-1 py-2.5 text-sm font-medium transition-colors
                    ${tipo === t
                      ? t === 'DESPESA' ? 'bg-red-600 text-white' : t === 'RECEITA' ? 'bg-emerald-600 text-white' : 'bg-blue-600 text-white'
                      : 'text-conteudo-suave hover:text-conteudo'}
                    ${desabilitado ? 'opacity-40 cursor-not-allowed hover:text-conteudo-suave' : ''}`}
                >
                  {t === 'DESPESA' ? 'Despesa' : t === 'RECEITA' ? 'Receita' : 'Transferência'}
                </button>
              )
            })}
          </div>

          {/* Conta/Cartão e Categoria (ou Conta origem/destino para transferência) */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="label">{tipo === 'TRANSFERENCIA' ? 'Conta origem' : credito ? 'Cartão' : 'Conta'}</label>
              {credito ? (
                <select
                  className="select" value={form.cartaoId} onChange={e => set('cartaoId', e.target.value)}
                  disabled={!!editingItem} required
                >
                  <option value="">Selecione...</option>
                  {[...cartoes].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')).map(c => <option key={c.id} value={c.id}>{c.nome}</option>)}
                </select>
              ) : (
                <select
                  className="select" value={form.contaId} onChange={e => set('contaId', e.target.value)}
                  disabled={!!editing && tipo === 'TRANSFERENCIA'} required
                >
                  <option value="">Selecione...</option>
                  {[...contas].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')).map(c => <option key={c.id} value={c.id}>{c.nome}</option>)}
                </select>
              )}
            </div>
            {tipo === 'TRANSFERENCIA' ? (
              <div>
                <label className="label">Conta destino</label>
                <select
                  className="select" value={form.contaDestinoId} onChange={e => set('contaDestinoId', e.target.value)}
                  disabled={!!editing} required
                >
                  <option value="">Selecione...</option>
                  {[...contas].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')).map(c => <option key={c.id} value={c.id}>{c.nome}</option>)}
                </select>
              </div>
            ) : (
              <div>
                <label className="label">Categoria</label>
                <select className="select" value={form.categoriaId} onChange={e => set('categoriaId', e.target.value)}>
                  <option value="">Sem categoria</option>
                  {[...categorias].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')).map(c => <option key={c.id} value={c.id}>{c.nome}</option>)}
                </select>
              </div>
            )}
          </div>
          {erro && <p className="text-sm text-red-500">{erro}</p>}

          {/* Valor e Data */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="label">Valor (R$)</label>
              <input
                className="input" type="number" step="0.01" min="0.01" placeholder="0,00"
                value={form.valor} onChange={e => set('valor', e.target.value)} required
              />
            </div>
            <div>
              <label className="label">Data</label>
              <input
                className="input" type="date"
                value={form.data} onChange={e => set('data', e.target.value)} required
              />
            </div>
          </div>

          {/* Vencimento e pagamento — não se aplica a compras no crédito:
              ficam atreladas ao cartão, sem vencimento nem pagamento próprios,
              até a fatura fechar (vira uma despesa em débito na conta do cartão). */}
          {!credito && (
            <>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="label">Vencimento</label>
                  <input
                    className="input" type="date"
                    value={form.dataVencimento} onChange={e => set('dataVencimento', e.target.value)} required
                  />
                </div>
                {paga && (
                  <div>
                    <label className="label">Data de pagamento</label>
                    <input
                      className="input" type="date" max={hoje}
                      value={form.dataPagamento} onChange={e => set('dataPagamento', e.target.value)}
                    />
                  </div>
                )}
              </div>

              <div className="flex items-center gap-3 p-3 rounded-xl bg-superficie-2">
                <input
                  id="paga" type="checkbox"
                  checked={paga}
                  onChange={e => set('dataPagamento', e.target.checked ? (form.dataPagamento || (form.data <= hoje ? form.data : hoje)) : '')}
                  className="w-4 h-4 accent-acento"
                />
                <label htmlFor="paga" className="text-sm text-conteudo">
                  {tipo === 'RECEITA' ? 'Já foi recebida' : tipo === 'TRANSFERENCIA' ? 'Já foi transferida' : 'Já foi paga'}
                  <span className="block text-xs text-conteudo-suave">Desmarque para deixar como pendente (não afeta o saldo até ser paga)</span>
                </label>
              </div>
            </>
          )}

          {credito && (
            <p className="text-xs text-conteudo-suave px-1">
              Essa compra entra na fatura do cartão. No fechamento, vira uma despesa em débito na conta de pagamento do cartão.
            </p>
          )}

          {/* Descrição */}
          <div>
            <label className="label">Descrição</label>
            <input
              className="input" placeholder="Ex: Mercado, Aluguel..."
              value={form.descricao} onChange={e => set('descricao', e.target.value)}
            />
          </div>

          {/* Débito / Crédito — transferência não usa forma de pagamento;
              receita não pode ser em crédito (não faz sentido). */}
          {tipo !== 'TRANSFERENCIA' && (
          <div>
            <label className="label">Forma de pagamento</label>
            <div className="flex gap-2">
              {(tipo === 'RECEITA' ? (['DEBITO'] as TipoPagamento[]) : (['DEBITO', 'CREDITO'] as TipoPagamento[])).map(p => (
                <button
                  key={p} type="button"
                  disabled={!!editing}
                  onClick={() => set('tipoPagamento', p)}
                  className={`flex-1 py-2 text-sm rounded-lg border transition-colors
                    ${form.tipoPagamento === p
                      ? 'border-acento bg-acento/20 text-acento'
                      : 'border-borda text-conteudo-suave hover:border-conteudo-suave'}
                    ${editing ? 'opacity-60 cursor-not-allowed' : ''}`}
                >
                  {p === 'DEBITO' ? 'Débito' : 'Crédito'}
                </button>
              ))}
            </div>
          </div>
          )}

          {/* Fixa / Parcelada — não se aplica a transferências nem à edição de
              uma compra já no cartão (parcelamento só se decide na criação) */}
          {!editing && tipo !== 'TRANSFERENCIA' && (
            <div className="space-y-3">
              {!credito && (
                <div className="flex items-center gap-3 p-3 rounded-xl bg-superficie-2">
                  <input
                    id="fixa" type="checkbox"
                    checked={form.fixa}
                    onChange={e => { set('fixa', e.target.checked); if (e.target.checked) set('totalParcelas', '') }}
                    className="w-4 h-4 accent-acento"
                  />
                  <label htmlFor="fixa" className="text-sm text-conteudo">
                    Repetir todo mês (fixa)
                  </label>
                </div>
              )}

              {!form.fixa && (
                <div>
                  <label className="label">Parcelar em quantas vezes? (deixe em branco = à vista)</label>
                  <input
                    className="input" type="number" min="2" max="60" placeholder="Ex: 3"
                    value={form.totalParcelas}
                    onChange={e => set('totalParcelas', e.target.value)}
                  />
                  {!!form.totalParcelas && Number(form.totalParcelas) > 1 && (
                    <p className="text-xs text-conteudo-suave mt-1">
                      O valor acima é o total da compra — cada parcela fica em {fmtParcela(form.valor, form.totalParcelas)}.
                    </p>
                  )}
                </div>
              )}
            </div>
          )}

          {editingTx?.fixa && (
            <div className="space-y-2 p-3 rounded-xl bg-superficie-2">
              <label className="text-sm font-medium text-conteudo">Aplicar alteração em:</label>
              <div className="flex gap-2">
                {(['UNICA', 'FUTURAS'] as EscopoAtualizacao[]).map(s => (
                  <button
                    key={s}
                    type="button"
                    onClick={() => setEscopoEdicao(s)}
                    className={`flex-1 py-2 text-sm rounded-lg border transition-colors
                      ${escopoEdicao === s
                        ? 'border-acento bg-acento/20 text-acento'
                        : 'border-borda text-conteudo-suave hover:border-conteudo-suave'}`}
                  >
                    {s === 'UNICA' ? 'Só este mês' : 'Este e os próximos'}
                  </button>
                ))}
              </div>
            </div>
          )}

          <div className="flex gap-3 pt-2">
            <button type="button" onClick={onClose} className="flex-1 py-2.5 rounded-lg border border-borda text-conteudo-suave hover:text-conteudo hover:border-conteudo-suave transition-colors text-sm font-medium">
              Cancelar
            </button>
            <button type="submit" disabled={mutation.isPending} className="flex-1 btn-primary">
              {mutation.isPending ? 'Salvando...' : 'Salvar'}
            </button>
          </div>
        </form>
      </div>
    </SobreposicaoModal>
  )
}
