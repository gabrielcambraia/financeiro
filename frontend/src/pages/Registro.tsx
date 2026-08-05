import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { registrar } from '../api/autenticacao'
import { confirmarVerificacaoEmail, solicitarVerificacaoEmail } from '../api/verificacaoContato'
import { useLojaAutenticacao } from '../store/lojaAutenticacao'
import CampoSenha from '../components/CampoSenha'
import type { TipoPessoa } from '../types'

type Passo = 1 | 2 | 3

interface FormPerfil {
  nome: string
  email: string
  senha: string
  telefone: string
}

interface FormEntidade {
  tipoPessoa: TipoPessoa
  nome: string
  nomeFantasia: string
  documento: string
  inscricaoEstadual: string
  dataNascimento: string
  email: string
  telefone: string
  cep: string
  logradouro: string
  numero: string
  complemento: string
  bairro: string
  cidade: string
  uf: string
}

const entidadeInicial: FormEntidade = {
  tipoPessoa: 'FISICA',
  nome: '',
  nomeFantasia: '',
  documento: '',
  inscricaoEstadual: '',
  dataNascimento: '',
  email: '',
  telefone: '',
  cep: '',
  logradouro: '',
  numero: '',
  complemento: '',
  bairro: '',
  cidade: '',
  uf: '',
}

export default function Registro() {
  const navigate = useNavigate()
  const definirSessao = useLojaAutenticacao(s => s.definirSessao)
  const [passo, setPasso] = useState<Passo>(1)
  const [perfil, setPerfil] = useState<FormPerfil>({ nome: '', email: '', senha: '', telefone: '' })
  const [entidade, setEntidade] = useState<FormEntidade>(entidadeInicial)
  const [codigoEmail, setCodigoEmail] = useState('')
  const [erro, setErro] = useState('')
  const [carregando, setCarregando] = useState(false)

  const setPf = (k: keyof FormPerfil, v: string) => setPerfil(p => ({ ...p, [k]: v }))
  const setEnt = (k: keyof FormEntidade, v: string) => setEntidade(e => ({ ...e, [k]: v }))

  const submitPasso1 = (e: React.FormEvent) => {
    e.preventDefault()
    setErro('')
    setPasso(2)
  }

  const submitPasso2 = async (e: React.FormEvent) => {
    e.preventDefault()
    setErro('')
    setCarregando(true)
    try {
      const resposta = await registrar({
        nome: perfil.nome,
        email: perfil.email,
        senha: perfil.senha,
        telefone: perfil.telefone || undefined,
        entidade: {
          tipoPessoa: entidade.tipoPessoa,
          nome: entidade.nome,
          nomeFantasia: entidade.nomeFantasia || undefined,
          documento: entidade.documento,
          inscricaoEstadual: entidade.inscricaoEstadual || undefined,
          dataNascimento: entidade.dataNascimento || undefined,
          email: entidade.email || undefined,
          telefone: entidade.telefone || undefined,
          cep: entidade.cep || undefined,
          logradouro: entidade.logradouro || undefined,
          numero: entidade.numero || undefined,
          complemento: entidade.complemento || undefined,
          bairro: entidade.bairro || undefined,
          cidade: entidade.cidade || undefined,
          uf: entidade.uf || undefined,
        },
      })
      definirSessao(resposta)
      setPasso(3)
    } catch (err: unknown) {
      const e = err as { response?: { status?: number; data?: { mensagem?: string } } }
      const msg = e?.response?.data?.mensagem
      if (e?.response?.status === 409) setErro('E-mail ou documento já cadastrado')
      else if (e?.response?.status === 422) setErro(msg ?? 'Documento inválido')
      else setErro('Não foi possível criar a conta')
    } finally {
      setCarregando(false)
    }
  }

  const submitPasso3 = async (e: React.FormEvent) => {
    e.preventDefault()
    setErro('')
    setCarregando(true)
    try {
      await confirmarVerificacaoEmail(codigoEmail)
      navigate('/')
    } catch {
      setErro('Código inválido ou expirado')
    } finally {
      setCarregando(false)
    }
  }

  const reenviarCodigo = async () => {
    try {
      await solicitarVerificacaoEmail()
    } catch {
      // silencioso
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center p-4">
      <div className="card w-full max-w-lg">
        <h1 className="text-2xl font-bold text-conteudo mb-1">💰 Financeiro</h1>
        <p className="text-sm text-conteudo-suave mb-1">Crie sua conta</p>
        <div className="flex gap-1 mb-6">
          {([1, 2, 3] as Passo[]).map(p => (
            <div
              key={p}
              className={`h-1 flex-1 rounded ${passo >= p ? 'bg-acento' : 'bg-superficie-destaque'}`}
            />
          ))}
        </div>

        {passo === 1 && (
          <form onSubmit={submitPasso1} className="space-y-4">
            <h2 className="font-semibold text-conteudo">Seu perfil</h2>
            <div>
              <label className="label">Nome completo</label>
              <input className="input" required value={perfil.nome}
                onChange={e => setPf('nome', e.target.value)} />
            </div>
            <div>
              <label className="label">E-mail</label>
              <input className="input" type="email" required value={perfil.email}
                onChange={e => setPf('email', e.target.value)} />
            </div>
            <div>
              <label className="label">Senha</label>
              <CampoSenha required minLength={8} value={perfil.senha}
                onChange={e => setPf('senha', e.target.value)} />
            </div>
            <div>
              <label className="label">Telefone <span className="text-conteudo-suave text-xs">(opcional)</span></label>
              <input className="input" type="tel" value={perfil.telefone}
                onChange={e => setPf('telefone', e.target.value)}
                placeholder="(11) 99999-9999" />
            </div>
            {erro && <p className="text-sm text-red-500">{erro}</p>}
            <button type="submit" className="w-full btn-primary">Continuar</button>
            <p className="text-sm text-conteudo-suave text-center">
              Já tem conta? <Link to="/login" className="text-acento hover:underline">Entrar</Link>
            </p>
          </form>
        )}

        {passo === 2 && (
          <form onSubmit={submitPasso2} className="space-y-4">
            <h2 className="font-semibold text-conteudo">Dados da entidade</h2>
            <p className="text-xs text-conteudo-suave">
              Seu espaço começa no plano Individual com 1 entidade. Para mais entidades, contate o suporte.
            </p>

            <div className="flex gap-2">
              {(['FISICA', 'JURIDICA'] as TipoPessoa[]).map(t => (
                <button
                  key={t}
                  type="button"
                  onClick={() => setEnt('tipoPessoa', t)}
                  className={`flex-1 py-2 rounded text-sm font-medium border transition-colors ${
                    entidade.tipoPessoa === t
                      ? 'border-acento bg-acento text-white'
                      : 'border-borda text-conteudo-suave hover:border-acento'
                  }`}
                >
                  {t === 'FISICA' ? 'Pessoa Física' : 'Pessoa Jurídica'}
                </button>
              ))}
            </div>

            <div>
              <label className="label">{entidade.tipoPessoa === 'FISICA' ? 'Nome completo' : 'Razão social'}</label>
              <input className="input" required value={entidade.nome}
                onChange={e => setEnt('nome', e.target.value)} />
            </div>

            {entidade.tipoPessoa === 'JURIDICA' && (
              <div>
                <label className="label">Nome fantasia <span className="text-conteudo-suave text-xs">(opcional)</span></label>
                <input className="input" value={entidade.nomeFantasia}
                  onChange={e => setEnt('nomeFantasia', e.target.value)} />
              </div>
            )}

            <div>
              <label className="label">{entidade.tipoPessoa === 'FISICA' ? 'CPF' : 'CNPJ'}</label>
              <input className="input" required value={entidade.documento}
                onChange={e => setEnt('documento', e.target.value)}
                placeholder={entidade.tipoPessoa === 'FISICA' ? '000.000.000-00' : '00.000.000/0001-00'} />
            </div>

            {entidade.tipoPessoa === 'FISICA' && (
              <div>
                <label className="label">Data de nascimento <span className="text-conteudo-suave text-xs">(opcional)</span></label>
                <input className="input" type="date" value={entidade.dataNascimento}
                  onChange={e => setEnt('dataNascimento', e.target.value)} />
              </div>
            )}

            {entidade.tipoPessoa === 'JURIDICA' && (
              <div>
                <label className="label">Inscrição estadual <span className="text-conteudo-suave text-xs">(opcional)</span></label>
                <input className="input" value={entidade.inscricaoEstadual}
                  onChange={e => setEnt('inscricaoEstadual', e.target.value)} />
              </div>
            )}

            <div className="grid grid-cols-2 gap-2">
              <div>
                <label className="label">E-mail <span className="text-conteudo-suave text-xs">(opcional)</span></label>
                <input className="input" type="email" value={entidade.email}
                  onChange={e => setEnt('email', e.target.value)} />
              </div>
              <div>
                <label className="label">Telefone <span className="text-conteudo-suave text-xs">(opcional)</span></label>
                <input className="input" type="tel" value={entidade.telefone}
                  onChange={e => setEnt('telefone', e.target.value)} />
              </div>
            </div>

            <div className="grid grid-cols-3 gap-2">
              <div>
                <label className="label">CEP</label>
                <input className="input" value={entidade.cep}
                  onChange={e => setEnt('cep', e.target.value)} />
              </div>
              <div className="col-span-2">
                <label className="label">Logradouro</label>
                <input className="input" value={entidade.logradouro}
                  onChange={e => setEnt('logradouro', e.target.value)} />
              </div>
            </div>

            <div className="grid grid-cols-3 gap-2">
              <div>
                <label className="label">Número</label>
                <input className="input" value={entidade.numero}
                  onChange={e => setEnt('numero', e.target.value)} />
              </div>
              <div className="col-span-2">
                <label className="label">Complemento</label>
                <input className="input" value={entidade.complemento}
                  onChange={e => setEnt('complemento', e.target.value)} />
              </div>
            </div>

            <div className="grid grid-cols-3 gap-2">
              <div>
                <label className="label">Bairro</label>
                <input className="input" value={entidade.bairro}
                  onChange={e => setEnt('bairro', e.target.value)} />
              </div>
              <div>
                <label className="label">Cidade</label>
                <input className="input" value={entidade.cidade}
                  onChange={e => setEnt('cidade', e.target.value)} />
              </div>
              <div>
                <label className="label">UF</label>
                <input className="input" maxLength={2} value={entidade.uf}
                  onChange={e => setEnt('uf', e.target.value.toUpperCase())} />
              </div>
            </div>

            {erro && <p className="text-sm text-red-500">{erro}</p>}

            <div className="flex gap-2">
              <button type="button" onClick={() => setPasso(1)}
                className="flex-1 py-2 rounded border border-borda text-conteudo-suave hover:bg-superficie-destaque">
                Voltar
              </button>
              <button type="submit" disabled={carregando} className="flex-1 btn-primary">
                {carregando ? 'Criando...' : 'Criar conta'}
              </button>
            </div>
          </form>
        )}

        {passo === 3 && (
          <form onSubmit={submitPasso3} className="space-y-4">
            <h2 className="font-semibold text-conteudo">Verifique seu e-mail</h2>
            <p className="text-sm text-conteudo-suave">
              Enviamos um código de 6 dígitos para <strong>{perfil.email}</strong>.
              Digite abaixo para confirmar sua conta.
            </p>
            <div>
              <label className="label">Código de verificação</label>
              <input
                className="input text-center text-2xl tracking-widest"
                required maxLength={6} pattern="\d{6}"
                value={codigoEmail}
                onChange={e => setCodigoEmail(e.target.value.replace(/\D/g, ''))}
                placeholder="000000"
              />
            </div>
            {erro && <p className="text-sm text-red-500">{erro}</p>}
            <button type="submit" disabled={carregando} className="w-full btn-primary">
              {carregando ? 'Verificando...' : 'Verificar e entrar'}
            </button>
            <p className="text-sm text-center text-conteudo-suave">
              Não recebeu?{' '}
              <button type="button" onClick={reenviarCodigo}
                className="text-acento hover:underline">
                Reenviar código
              </button>
            </p>
            <p className="text-sm text-center text-conteudo-suave">
              <button type="button" onClick={() => navigate('/')}
                className="text-acento hover:underline">
                Pular por enquanto
              </button>
            </p>
          </form>
        )}
      </div>
    </div>
  )
}
