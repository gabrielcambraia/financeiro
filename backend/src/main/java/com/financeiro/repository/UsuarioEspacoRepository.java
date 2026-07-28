package com.financeiro.repository;

import com.financeiro.dto.RespostaMembro;
import com.financeiro.dto.RespostaVinculoEspaco;
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

    // Versão em lote da query acima, usada pela tela de admin de espaços
    // para evitar N+1: busca os vínculos de várias páginas de espaços numa
    // única query. Chamador deve garantir que espacoIds não vem vazio (IN
    // vazio é erro de sintaxe em JPQL).
    @Query("select new com.financeiro.dto.RespostaVinculoEspaco(ue.id.espacoId, u.id, u.nome, u.email, ue.papel) " +
           "from UsuarioEspaco ue join Usuario u on u.id = ue.id.usuarioId " +
           "where ue.id.espacoId in :espacoIds order by ue.papel, u.email")
    List<RespostaVinculoEspaco> listarVinculosDosEspacos(@Param("espacoIds") List<Long> espacoIds);
}
