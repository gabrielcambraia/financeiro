package com.financeiro.service;

import com.financeiro.entity.Categoria;
import com.financeiro.entity.enums.TipoTransacao;
import com.financeiro.repository.CategoriaRepository;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.financeiro.entity.enums.TipoTransacao.DESPESA;
import static com.financeiro.entity.enums.TipoTransacao.RECEITA;

/**
 * Cria as categorias padrão para um espaço novo. Mesma lista usada no
 * seed inicial do banco (ver V2__seed_categories.sql / V4__traducao_pt_br.sql),
 * agora replicada em código para ser aplicada a cada espaço criado no registro.
 */
@Service
public class SemeadorCategoriasPadrao {

    private record CategoriaPadrao(String nome, TipoTransacao tipo, String cor, String icone) {
    }

    private static final List<CategoriaPadrao> CATEGORIAS_PADRAO = List.of(
            new CategoriaPadrao("Alimentação", DESPESA, "#ef4444", "utensils"),
            new CategoriaPadrao("Supermercado", DESPESA, "#f97316", "shopping-cart"),
            new CategoriaPadrao("Transporte", DESPESA, "#eab308", "car"),
            new CategoriaPadrao("Saúde", DESPESA, "#22c55e", "heart-pulse"),
            new CategoriaPadrao("Moradia", DESPESA, "#3b82f6", "home"),
            new CategoriaPadrao("Lazer", DESPESA, "#a855f7", "gamepad-2"),
            new CategoriaPadrao("Roupas", DESPESA, "#ec4899", "shirt"),
            new CategoriaPadrao("Educação", DESPESA, "#06b6d4", "book-open"),
            new CategoriaPadrao("Assinaturas", DESPESA, "#8b5cf6", "tv"),
            new CategoriaPadrao("Outros", DESPESA, "#6b7280", "ellipsis"),
            new CategoriaPadrao("Salário", RECEITA, "#22c55e", "briefcase"),
            new CategoriaPadrao("Freelance", RECEITA, "#10b981", "laptop"),
            new CategoriaPadrao("Investimentos", RECEITA, "#f59e0b", "trending-up"),
            new CategoriaPadrao("Outros", RECEITA, "#6b7280", "plus-circle")
    );

    private final CategoriaRepository categoriaRepository;

    public SemeadorCategoriasPadrao(CategoriaRepository categoriaRepository) {
        this.categoriaRepository = categoriaRepository;
    }

    public void semear(Long espacoId) {
        List<Categoria> categorias = CATEGORIAS_PADRAO.stream()
                .map(padrao -> Categoria.builder()
                        .nome(padrao.nome())
                        .tipo(padrao.tipo())
                        .cor(padrao.cor())
                        .icone(padrao.icone())
                        .espacoId(espacoId)
                        .build())
                .toList();

        categoriaRepository.saveAll(categorias);
    }
}
