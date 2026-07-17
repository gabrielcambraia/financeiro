package com.financeiro.repository;

import com.financeiro.entity.TokenAtualizacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface TokenAtualizacaoRepository extends JpaRepository<TokenAtualizacao, Long> {

    Optional<TokenAtualizacao> findByTokenHash(String tokenHash);

    @Modifying
    @Transactional
    @Query("update TokenAtualizacao t set t.revogado = true, t.revogadoEm = :agora " +
           "where t.usuarioId = :usuarioId and t.revogado = false")
    void revogarTodosDoUsuario(@Param("usuarioId") Long usuarioId, @Param("agora") LocalDateTime agora);
}
