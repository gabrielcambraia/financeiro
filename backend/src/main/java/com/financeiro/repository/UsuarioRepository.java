package com.financeiro.repository;

import com.financeiro.dto.RespostaVinculoEspaco;
import com.financeiro.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmail(String email);

    List<Usuario> findByEspacoIdOrderByPapelAscNomeAsc(Long espacoId);

    @Query("select new com.financeiro.dto.RespostaVinculoEspaco(u.espacoId, u.id, u.nome, u.email, u.papel) " +
           "from Usuario u where u.espacoId in :espacoIds order by u.papel, u.email")
    List<RespostaVinculoEspaco> listarVinculosDosEspacos(@Param("espacoIds") List<Long> espacoIds);
}
