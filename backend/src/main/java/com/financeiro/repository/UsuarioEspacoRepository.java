package com.financeiro.repository;

import com.financeiro.dto.RespostaMembro;
import com.financeiro.entity.UsuarioEspaco;
import com.financeiro.entity.UsuarioEspacoId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UsuarioEspacoRepository extends JpaRepository<UsuarioEspaco, UsuarioEspacoId> {
    List<UsuarioEspaco> findByIdUsuarioId(Long usuarioId);

    @Query("select new com.financeiro.dto.RespostaMembro(u.id, u.nome, u.email, ue.papel, u.precisaTrocarSenha) " +
           "from UsuarioEspaco ue join Usuario u on u.id = ue.id.usuarioId " +
           "where ue.id.espacoId = :espacoId order by ue.papel, u.nome")
    List<RespostaMembro> listarMembrosDoEspaco(@Param("espacoId") Long espacoId);
}
