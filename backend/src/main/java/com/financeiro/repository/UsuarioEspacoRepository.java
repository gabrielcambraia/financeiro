package com.financeiro.repository;

import com.financeiro.entity.UsuarioEspaco;
import com.financeiro.entity.UsuarioEspacoId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UsuarioEspacoRepository extends JpaRepository<UsuarioEspaco, UsuarioEspacoId> {
    List<UsuarioEspaco> findByIdUsuarioId(Long usuarioId);
}
