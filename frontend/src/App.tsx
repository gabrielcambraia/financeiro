import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Toaster } from 'sonner'
import Estrutura from './components/Estrutura'
import RotaProtegida from './components/RotaProtegida'
import FaviconPlataforma from './components/FaviconPlataforma'
import RotaAdmin from './components/RotaAdmin'
import Painel from './pages/Painel'
import Transacoes from './pages/Transacoes'
import Contas from './pages/Contas'
import Cartoes from './pages/Cartoes'
import CartaoDetalhe from './pages/CartaoDetalhe'
import Categorias from './pages/Categorias'
import Orcamentos from './pages/Orcamentos'
import Metas from './pages/Metas'
import Calendario from './pages/Calendario'
import Dividas from './pages/Dividas'
import Investimentos from './pages/Investimentos'
import Simulacao from './pages/Simulacao'
import AdminBancos from './pages/AdminBancos'
import AdminEspacos from './pages/AdminEspacos'
import Login from './pages/Login'
import LoginCodigo from './pages/LoginCodigo'
import Registro from './pages/Registro'
import TrocarSenha from './pages/TrocarSenha'
import Perfil from './pages/Perfil'
import Entidades from './pages/Entidades'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, staleTime: 30_000 },
  },
})

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <Toaster position="top-right" richColors closeButton duration={4000} theme="dark" />
      <FaviconPlataforma />
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/entrar-codigo" element={<LoginCodigo />} />
          <Route path="/registro" element={<Registro />} />
          <Route path="/trocar-senha" element={<TrocarSenha />} />
          <Route element={<RotaProtegida />}>
            <Route path="/" element={<Estrutura />}>
              <Route index element={<Painel />} />
              <Route path="transacoes" element={<Transacoes />} />
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
              <Route element={<RotaAdmin />}>
                <Route path="admin/bancos" element={<AdminBancos />} />
                <Route path="admin/espacos" element={<AdminEspacos />} />
              </Route>
            </Route>
          </Route>
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
