import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { KeyRound, Palette, Image, Trash2, Upload, CreditCard, Users } from 'lucide-react'
import { trocarSenha } from '../api/autenticacao'
import { buscarAssinatura } from '../api/filiais'
import {
  enviarLogoPlataforma, removerLogoPlataforma, logoPlataformaUrl,
  enviarLogoLoginPlataforma, removerLogoLoginPlataforma, logoLoginPlataformaUrl,
} from '../api/configuracaoPlataforma'
import { useConfiguracaoPlataforma } from '../hooks/useConfiguracaoPlataforma'
import { useLojaAutenticacao } from '../store/lojaAutenticacao'
import AlternadorTema from '../components/AlternadorTema'
import CampoSenha from '../components/CampoSenha'
import type { Assinatura } from '../types'


function SecaoLogoPlataforma() {
  const qc = useQueryClient()
  const { data: configuracaoPlataforma } = useConfiguracaoPlataforma()
  const inputArquivoRef = useRef<HTMLInputElement>(null)
  const [erro, setErro] = useState('')

  const invalidar = () => qc.invalidateQueries({ queryKey: ['configuracao-plataforma'] })

  const uploadMutation = useMutation({
    mutationFn: enviarLogoPlataforma,
    onSuccess: () => { invalidar(); setErro(''); toast.success('Logo enviada') },
    onError: () => setErro('Falha ao enviar imagem. Use PNG, JPEG ou WEBP de até 1MB.'),
  })

  const removerMutation = useMutation({
    mutationFn: removerLogoPlataforma,
    onSuccess: () => { invalidar(); toast.success('Logo removida') },
  })

  const arquivoSelecionado = (e: React.ChangeEvent<HTMLInputElement>) => {
    const arquivo = e.target.files?.[0]
    e.target.value = ''
    if (arquivo) uploadMutation.mutate(arquivo)
  }

  return (
    <div className="card">
      <div className="flex items-center gap-2 mb-4">
        <Image size={16} className="text-acento" />
        <h2 className="text-sm font-semibold text-conteudo">Logo da plataforma</h2>
      </div>
      <p className="text-xs text-conteudo-suave mb-4">
        Usada como ícone da aba do navegador e no lugar do ícone Nexus360 na barra lateral.
      </p>
      <div className="flex items-center gap-4">
        <div className="w-14 h-14 rounded-xl bg-superficie-2 flex items-center justify-center shrink-0 overflow-hidden">
          {configuracaoPlataforma?.temLogo ? (
            <img src={logoPlataformaUrl()} alt="Logo atual" className="w-full h-full object-contain" />
          ) : (
            <Image size={20} className="text-conteudo-suave" />
          )}
        </div>
        <div className="flex gap-2">
          <input ref={inputArquivoRef} type="file" accept="image/png,image/jpeg,image/webp" className="hidden" onChange={arquivoSelecionado} />
          <button type="button" onClick={() => inputArquivoRef.current?.click()}
            className="btn-ghost flex items-center gap-2 text-sm">
            <Upload size={14} /> {uploadMutation.isPending ? 'Enviando...' : 'Enviar logo'}
          </button>
          {configuracaoPlataforma?.temLogo && (
            <button type="button" onClick={() => removerMutation.mutate()}
              className="btn-ghost flex items-center gap-2 text-sm text-red-500 hover:text-red-400">
              <Trash2 size={14} /> Remover
            </button>
          )}
        </div>
      </div>
      {erro && <p className="text-sm text-red-500 mt-3">{erro}</p>}
    </div>
  )
}

// Slot independente da logo acima — a tela de login costuma usar uma peça de
// marca mais larga (com nome/tagline embutidos), por isso o preview aqui é
// retangular em vez do quadrado usado para o ícone da barra lateral.
function SecaoLogoLogin() {
  const qc = useQueryClient()
  const { data: configuracaoPlataforma } = useConfiguracaoPlataforma()
  const inputArquivoRef = useRef<HTMLInputElement>(null)
  const [erro, setErro] = useState('')

  const invalidar = () => qc.invalidateQueries({ queryKey: ['configuracao-plataforma'] })

  const uploadMutation = useMutation({
    mutationFn: enviarLogoLoginPlataforma,
    onSuccess: () => { invalidar(); setErro(''); toast.success('Logo enviada') },
    onError: () => setErro('Falha ao enviar imagem. Use PNG, JPEG ou WEBP de até 1MB.'),
  })

  const removerMutation = useMutation({
    mutationFn: removerLogoLoginPlataforma,
    onSuccess: () => { invalidar(); toast.success('Logo removida') },
  })

  const arquivoSelecionado = (e: React.ChangeEvent<HTMLInputElement>) => {
    const arquivo = e.target.files?.[0]
    e.target.value = ''
    if (arquivo) uploadMutation.mutate(arquivo)
  }

  return (
    <div className="card">
      <div className="flex items-center gap-2 mb-4">
        <Image size={16} className="text-acento" />
        <h2 className="text-sm font-semibold text-conteudo">Logo da tela de login</h2>
      </div>
      <p className="text-xs text-conteudo-suave mb-4">
        Banner exibido na tela de login, no lugar do ícone Nexus360. Independente da logo da barra lateral.
      </p>
      <div className="flex items-center gap-4">
        <div className="h-14 w-28 rounded-xl bg-superficie-2 flex items-center justify-center shrink-0 overflow-hidden">
          {configuracaoPlataforma?.temLogoLogin ? (
            <img src={logoLoginPlataformaUrl()} alt="Logo atual" className="w-full h-full object-contain" />
          ) : (
            <Image size={20} className="text-conteudo-suave" />
          )}
        </div>
        <div className="flex gap-2">
          <input ref={inputArquivoRef} type="file" accept="image/png,image/jpeg,image/webp" className="hidden" onChange={arquivoSelecionado} />
          <button type="button" onClick={() => inputArquivoRef.current?.click()}
            className="btn-ghost flex items-center gap-2 text-sm">
            <Upload size={14} /> {uploadMutation.isPending ? 'Enviando...' : 'Enviar logo'}
          </button>
          {configuracaoPlataforma?.temLogoLogin && (
            <button type="button" onClick={() => removerMutation.mutate()}
              className="btn-ghost flex items-center gap-2 text-sm text-red-500 hover:text-red-400">
              <Trash2 size={14} /> Remover
            </button>
          )}
        </div>
      </div>
      {erro && <p className="text-sm text-red-500 mt-3">{erro}</p>}
    </div>
  )
}

function SecaoPlano() {
  const { data: assinatura } = useQuery<Assinatura>({ queryKey: ['assinatura'], queryFn: buscarAssinatura })

  return (
    <div className="card">
      <div className="flex items-center gap-2 mb-4">
        <CreditCard size={16} className="text-acento" />
        <h2 className="text-sm font-semibold text-conteudo">Plano</h2>
      </div>
      {assinatura ? (
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-conteudo">{assinatura.nomePlano}</p>
              <p className="text-xs text-conteudo-suave mt-0.5">
                {assinatura.filiaisUsadas}/{assinatura.limiteFiliais} filial{assinatura.limiteFiliais !== 1 ? 'is' : ''} usada{assinatura.filiaisUsadas !== 1 ? 's' : ''}
              </p>
            </div>
            <span className="text-xs px-2 py-0.5 rounded-full bg-pastel-lilas text-pastel-lilas-texto font-medium">
              {assinatura.status === 'ATIVA' ? 'Ativa' : assinatura.status}
            </span>
          </div>
          <Link to="/filiais" className="flex items-center gap-2 text-sm text-acento hover:underline">
            <Users size={14} /> Gerenciar filiais
          </Link>
        </div>
      ) : (
        <Link to="/filiais" className="flex items-center gap-2 text-sm text-acento hover:underline">
          <Users size={14} /> Ver filiais
        </Link>
      )}
    </div>
  )
}

function SecaoAparencia() {
  return (
    <div className="card">
      <div className="flex items-center gap-2 mb-4">
        <Palette size={16} className="text-acento" />
        <h2 className="text-sm font-semibold text-conteudo">Aparência</h2>
      </div>
      <div className="flex items-center justify-between">
        <div>
          <p className="text-sm text-conteudo">Tema claro ou escuro</p>
          <p className="text-xs text-conteudo-suave mt-0.5">Escolha como o Financeiro deve aparecer para você.</p>
        </div>
        <AlternadorTema />
      </div>
    </div>
  )
}

function SecaoTrocarSenha() {
  const definirSessao = useLojaAutenticacao(s => s.definirSessao)
  const [senhaAtual, setSenhaAtual] = useState('')
  const [novaSenha, setNovaSenha] = useState('')
  const [erro, setErro] = useState('')
  const [sucesso, setSucesso] = useState(false)

  const mutation = useMutation({
    mutationFn: trocarSenha,
    onSuccess: resposta => {
      definirSessao(resposta)
      setSenhaAtual('')
      setNovaSenha('')
      setSucesso(true)
      setErro('')
    },
    onError: (e: unknown) => {
      const status = (e as { response?: { status?: number } })?.response?.status
      setErro(status === 401 ? 'Senha atual incorreta' : 'Não foi possível trocar a senha')
      setSucesso(false)
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setErro('')
    setSucesso(false)
    mutation.mutate({ senhaAtual, novaSenha })
  }

  return (
    <div className="card">
      <div className="flex items-center gap-2 mb-4">
        <KeyRound size={16} className="text-acento" />
        <h2 className="text-sm font-semibold text-conteudo">Segurança — Trocar senha</h2>
      </div>
      <form onSubmit={handleSubmit} className="space-y-4 max-w-sm">
        <div>
          <label className="label">Senha atual</label>
          <CampoSenha required
            value={senhaAtual} onChange={e => setSenhaAtual(e.target.value)} />
        </div>
        <div>
          <label className="label">Nova senha</label>
          <CampoSenha required minLength={8}
            value={novaSenha} onChange={e => setNovaSenha(e.target.value)} />
        </div>

        {erro && <p className="text-sm text-red-500">{erro}</p>}
        {sucesso && <p className="text-sm text-emerald-500">Senha atualizada com sucesso.</p>}

        <button type="submit" disabled={mutation.isPending} className="btn-primary">
          {mutation.isPending ? 'Salvando...' : 'Trocar senha'}
        </button>
      </form>
    </div>
  )
}


export default function Perfil() {
  const sessao = useLojaAutenticacao(s => s.sessao)

  return (
    <div className="p-6 space-y-5 max-w-2xl">
      <div>
        <h1 className="text-2xl font-bold text-conteudo">Perfil</h1>
        <p className="text-sm text-conteudo-suave mt-0.5">Suas informações, aparência e segurança.</p>
      </div>

      <SecaoPlano />
      {sessao?.nivelAcesso === 'ADMIN' && <SecaoLogoPlataforma />}
      {sessao?.nivelAcesso === 'ADMIN' && <SecaoLogoLogin />}
      <SecaoAparencia />
      <SecaoTrocarSenha />
    </div>
  )
}
