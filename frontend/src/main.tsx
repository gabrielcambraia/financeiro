import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { aplicarClasseTema, carregarTemaInicial } from './store/lojaTema'

aplicarClasseTema(carregarTemaInicial())

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
