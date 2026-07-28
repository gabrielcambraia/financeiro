package com.financeiro.repository;

import com.financeiro.entity.Espaco;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EspacoRepository extends JpaRepository<Espaco, Long> {

    // Ordena por id (não por criadoEm, que é TEXT via ConversorLocalDateTime)
    // para garantir estabilidade entre páginas: id é sequencial e único.
    @Query("select e from Espaco e order by e.id desc")
    Page<Espaco> listarPaginado(Pageable pageable);

    // espaco_modulos é uma @ElementCollection (sem entidade própria), então
    // a busca em lote (evitando N+1 ao listar uma página de espaços) precisa
    // ser nativa. Cada linha é [espaco_id (Number), modulo (String)].
    @Query(value = "select espaco_id, modulo from espaco_modulos where espaco_id in :espacoIds", nativeQuery = true)
    List<Object[]> listarModulosDosEspacos(@Param("espacoIds") List<Long> espacoIds);
}
