package com.financeiro.repository;

import com.financeiro.entity.Assinatura;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AssinaturaRepository extends JpaRepository<Assinatura, Long> {
    Optional<Assinatura> findByEspacoId(Long espacoId);

    List<Assinatura> findByEspacoIdIn(Collection<Long> espacoIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Assinatura a WHERE a.espacoId = :espacoId")
    Optional<Assinatura> findByEspacoIdWithLock(@Param("espacoId") Long espacoId);
}
