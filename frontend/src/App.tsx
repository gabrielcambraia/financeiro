import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import Estrutura from './components/Estrutura'
import Painel from './pages/Painel'
import Transacoes from './pages/Transacoes'
import Contas from './pages/Contas'
import Categorias from './pages/Categorias'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, staleTime: 30_000 },
  },
})

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<Estrutura />}>
            <Route index element={<Painel />} />
            <Route path="transacoes" element={<Transacoes />} />
            <Route path="contas" element={<Contas />} />
            <Route path="categorias" element={<Categorias />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
