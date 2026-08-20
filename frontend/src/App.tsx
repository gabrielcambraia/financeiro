import { lazy, Suspense } from 'react'
import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Toaster } from 'sonner'
import { useLojaTema } from './store/lojaTema'
import Estrutura from './components/Estrutura'
import RotaProtegida from './components/RotaProtegida'
import FaviconPlataforma from './components/FaviconPlataforma'
import RotaAdmin from './components/RotaAdmin'

const Painel = lazy(() => import('./pages/Painel'))
const Transacoes = lazy(() => import('./pages/Transacoes'))
const Pagamentos = lazy(() => import('./pages/Lancamentos/Pagamentos'))
const Recebimentos = lazy(() => import('./pages/Lancamentos/Recebimentos'))
const Recorrencias = lazy(() => import('./pages/Lancamentos/Recorrencias'))
const ComprasCartao = lazy(() => import('./pages/Lancamentos/ComprasCartao'))
const FaturasLancamentos = lazy(() => import('./pages/Lancamentos/Faturas'))
const Contas = lazy(() => import('./pages/Contas'))
const Cartoes = lazy(() => import('./pages/Cartoes'))
const CartaoDetalhe = lazy(() => import('./pages/CartaoDetalhe'))
const Categorias = lazy(() => import('./pages/Categorias'))
const Orcamentos = lazy(() => import('./pages/Orcamentos'))
const Metas = lazy(() => import('./pages/Metas'))
const Calendario = lazy(() => import('./pages/Calendario'))
const Dividas = lazy(() => import('./pages/Dividas'))
const Investimentos = lazy(() => import('./pages/Investimentos'))
const Simulacao = lazy(() => import('./pages/Simulacao'))
const AdminBancos = lazy(() => import('./pages/AdminBancos'))
const AdminEspacos = lazy(() => import('./pages/AdminEspacos'))
const Login = lazy(() => import('./pages/Login'))
const LoginCodigo = lazy(() => import('./pages/LoginCodigo'))
const Registro = lazy(() => import('./pages/Registro'))
const TrocarSenha = lazy(() => import('./pages/TrocarSenha'))
const Perfil = lazy(() => import('./pages/Perfil'))
const Entidades = lazy(() => import('./pages/Entidades'))
const CentrosCusto = lazy(() => import('./pages/CentrosCusto'))

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, staleTime: 30_000 },
  },
})

export default function App() {
  const tema = useLojaTema(s => s.tema)
  return (
    <QueryClientProvider client={queryClient}>
      <Toaster position="top-right" richColors closeButton duration={4000} theme={tema === 'escuro' ? 'dark' : 'light'} />
      <FaviconPlataforma />
      <BrowserRouter>
        <Suspense fallback={null}>
          <Routes>
            <Route path="/login" element={<Login />} />
            <Route path="/entrar-codigo" element={<LoginCodigo />} />
            <Route path="/registro" element={<Registro />} />
            <Route path="/trocar-senha" element={<TrocarSenha />} />
            <Route element={<RotaProtegida />}>
              <Route path="/" element={<Estrutura />}>
                <Route index element={<Painel />} />
                <Route path="lancamentos/transacoes" element={<Transacoes />} />
                <Route path="lancamentos/a-pagar" element={<Pagamentos />} />
                <Route path="lancamentos/a-receber" element={<Recebimentos />} />
                <Route path="lancamentos/recorrencias" element={<Recorrencias />} />
                <Route path="lancamentos/compras-cartao" element={<ComprasCartao />} />
                <Route path="lancamentos/faturas" element={<FaturasLancamentos />} />
                <Route path="contas" element={<Contas />} />
                <Route path="cartoes" element={<Cartoes />} />
                <Route path="cartoes/:id" element={<CartaoDetalhe />} />
                <Route path="categorias" element={<Categorias />} />
                <Route path="orcamentos" element={<Orcamentos />} />
                <Route path="metas" element={<Metas />} />
                <Route path="calendario" element={<Calendario />} />
                <Route path="dividas" element={<Dividas />} />
                <Route path="investimentos" element={<Investimentos />} />
                <Route path="simulacao" element={<Simulacao />} />
                <Route path="perfil" element={<Perfil />} />
                <Route path="entidades" element={<Entidades />} />
                <Route path="centros-custo" element={<CentrosCusto />} />
                <Route element={<RotaAdmin />}>
                  <Route path="admin/bancos" element={<AdminBancos />} />
                  <Route path="admin/espacos" element={<AdminEspacos />} />
                </Route>
              </Route>
            </Route>
          </Routes>
        </Suspense>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
