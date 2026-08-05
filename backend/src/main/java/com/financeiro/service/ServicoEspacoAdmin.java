package com.financeiro.service;

import com.financeiro.dto.RespostaEspacoAdmin;
import com.financeiro.dto.RespostaPaginada;
import com.financeiro.dto.RespostaVinculoEspaco;
import com.financeiro.entity.Assinatura;
import com.financeiro.entity.Plano;
import com.financeiro.entity.Espaco;
import com.financeiro.entity.enums.CodigoPlano;
import com.financeiro.entity.enums.ModuloEspaco;
import com.financeiro.entity.enums.PapelUsuario;
import com.financeiro.entity.enums.TipoEspaco;
import com.financeiro.erro.ExcecaoRecursoNaoEncontrado;
import com.financeiro.repository.AssinaturaRepository;
import com.financeiro.repository.EspacoRepository;
import com.financeiro.repository.PlanoRepository;
import com.financeiro.repository.UsuarioEspacoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Suporta a tela de admin que lista todos os espaços da plataforma (com
 * dono, vínculos e módulos habilitados) e permite reclassificar o tipo do
 * espaço (PESSOAL/FAMILIA/EMPRESA) e os módulos habilitados.
 */
@Service
@RequiredArgsConstructor
public class ServicoEspacoAdmin {

    private static final int TAMANHO_PAGINA = 10;

    private final EspacoRepository espacoRepository;
    private final UsuarioEspacoRepository usuarioEspacoRepository;
    private final AssinaturaRepository assinaturaRepository;
    private final PlanoRepository planoRepository;

    @Transactional(readOnly = true)
    public RespostaPaginada<RespostaEspacoAdmin> listar(int pagina) {
        int paginaSegura = Math.max(pagina, 0);
        Page<Espaco> paginaEspacos = espacoRepository.listarPaginado(PageRequest.of(paginaSegura, TAMANHO_PAGINA));

        if (paginaEspacos.isEmpty()) {
            return RespostaPaginada.de(paginaEspacos, List.of());
        }

        List<Long> ids = paginaEspacos.getContent().stream().map(Espaco::getId).toList();

        Map<Long, List<RespostaVinculoEspaco>> vinculosPorEspaco = usuarioEspacoRepository
                .listarVinculosDosEspacos(ids).stream()
                .collect(Collectors.groupingBy(RespostaVinculoEspaco::getEspacoId));

        Map<Long, Set<ModuloEspaco>> modulosPorEspaco = agruparModulos(espacoRepository.listarModulosDosEspacos(ids));

        Map<Long, CodigoPlano> codigosPorEspaco = buscarCodigosPorEspaco(ids);

        List<RespostaEspacoAdmin> itens = paginaEspacos.getContent().stream()
                .map(espaco -> montarResposta(
                        espaco,
                        vinculosPorEspaco.getOrDefault(espaco.getId(), List.of()),
                        modulosPorEspaco.getOrDefault(espaco.getId(), Set.of()),
                        codigosPorEspaco.get(espaco.getId())))
                .toList();

        return RespostaPaginada.de(paginaEspacos, itens);
    }

    private Map<Long, CodigoPlano> buscarCodigosPorEspaco(List<Long> espacoIds) {
        List<Assinatura> assinaturas = assinaturaRepository.findByEspacoIdIn(espacoIds);
        Set<Long> planoIds = assinaturas.stream().map(Assinatura::getPlanoId).collect(Collectors.toSet());
        Map<Long, CodigoPlano> codigoPorPlanoId = planoRepository.findAllById(planoIds).stream()
                .collect(Collectors.toMap(Plano::getId, Plano::getCodigo));
        // HashMap aceita null values; usar toMap para preservar os que não têm plano.
        Map<Long, CodigoPlano> resultado = new java.util.HashMap<>();
        assinaturas.forEach(a -> resultado.put(a.getEspacoId(), codigoPorPlanoId.get(a.getPlanoId())));
        return resultado;
    }

    @Transactional
    public RespostaEspacoAdmin alterarTipo(Long espacoId, TipoEspaco tipo) {
        Espaco espaco = buscarEspaco(espacoId);
        espaco.setTipo(tipo);
        espacoRepository.save(espaco);
        return montarRespostaComVinculos(espaco);
    }

    @Transactional
    public RespostaEspacoAdmin alterarModulos(Long espacoId, Set<ModuloEspaco> modulos) {
        Espaco espaco = buscarEspaco(espacoId);
        espaco.setModulosHabilitados(new HashSet<>(modulos));
        espacoRepository.save(espaco);
        return montarRespostaComVinculos(espaco);
    }

    private Espaco buscarEspaco(Long espacoId) {
        return espacoRepository.findById(espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Espaço não encontrado: " + espacoId));
    }

    private RespostaEspacoAdmin montarRespostaComVinculos(Espaco espaco) {
        List<RespostaVinculoEspaco> vinculos = usuarioEspacoRepository.listarVinculosDosEspacos(List.of(espaco.getId()));
        CodigoPlano codigoPlano = buscarCodigoPlano(espaco.getId());
        return montarResposta(espaco, vinculos, espaco.getModulosHabilitados(), codigoPlano);
    }

    private CodigoPlano buscarCodigoPlano(Long espacoId) {
        return assinaturaRepository.findByEspacoId(espacoId)
                .flatMap(a -> planoRepository.findById(a.getPlanoId()))
                .map(Plano::getCodigo)
                .orElse(null);
    }

    private Map<Long, Set<ModuloEspaco>> agruparModulos(List<Object[]> linhas) {
        Map<Long, Set<ModuloEspaco>> resultado = new java.util.HashMap<>();
        for (Object[] linha : linhas) {
            Long espacoId = ((Number) linha[0]).longValue();
            ModuloEspaco modulo = ModuloEspaco.valueOf((String) linha[1]);
            resultado.computeIfAbsent(espacoId, k -> new HashSet<>()).add(modulo);
        }
        return resultado;
    }

    private RespostaEspacoAdmin montarResposta(Espaco espaco, List<RespostaVinculoEspaco> vinculos,
                                                Set<ModuloEspaco> modulosHabilitados, CodigoPlano codigoPlano) {
        String emailDono = vinculos.stream()
                .filter(v -> v.getPapel() == PapelUsuario.DONO)
                .findFirst()
                .map(RespostaVinculoEspaco::getEmail)
                .orElse(null);

        return new RespostaEspacoAdmin(
                espaco.getId(),
                espaco.getNome(),
                espaco.getTipo(),
                espaco.getPlano(),
                codigoPlano,
                espaco.getCriadoEm(),
                emailDono,
                vinculos.size(),
                vinculos,
                modulosHabilitados
        );
    }
}
