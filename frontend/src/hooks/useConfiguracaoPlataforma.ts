import { useQuery } from '@tanstack/react-query'
import { obterConfiguracaoPlataforma } from '../api/configuracaoPlataforma'

// staleTime Infinity: a logo muda raramente (só quando um admin troca) e a
// mutation de upload/remoção invalida esta query explicitamente.
export const useConfiguracaoPlataforma = () =>
  useQuery({
    queryKey: ['configuracao-plataforma'],
    queryFn: obterConfiguracaoPlataforma,
    staleTime: Infinity,
  })
