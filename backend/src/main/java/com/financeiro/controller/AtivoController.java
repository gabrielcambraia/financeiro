package com.financeiro.controller;

import com.financeiro.dto.AtivoDTO;
import com.financeiro.dto.MovimentacaoAtivoDTO;
import com.financeiro.dto.PatrimonioDTO;
import com.financeiro.dto.RespostaImpacto;
import com.financeiro.service.AtivoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ativos")
@RequiredArgsConstructor
public class AtivoController {

    private final AtivoService service;

    @GetMapping
    public List<AtivoDTO> findAll() {
        return service.findAll();
    }

    @GetMapping("/patrimonio")
    public PatrimonioDTO patrimonio(@RequestParam(defaultValue = "6") int meses) {
        return service.patrimonio(Math.min(24, Math.max(1, meses)));
    }

    @GetMapping("/{id}/movimentacoes")
    public List<MovimentacaoAtivoDTO> movimentacoes(@PathVariable Long id) {
        return service.movimentacoes(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AtivoDTO create(@Valid @RequestBody AtivoDTO dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    public AtivoDTO update(@PathVariable Long id, @Valid @RequestBody AtivoDTO dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PatchMapping("/{id}/cancelar")
    public AtivoDTO cancelar(@PathVariable Long id) {
        return service.cancelar(id);
    }

    @PatchMapping("/{id}/aportar")
    public AtivoDTO aportar(@PathVariable Long id, @Valid @RequestBody MovimentacaoAtivoDTO dto) {
        return service.aportar(id, dto);
    }

    @PatchMapping("/{id}/resgatar")
    public AtivoDTO resgatar(@PathVariable Long id, @Valid @RequestBody MovimentacaoAtivoDTO dto) {
        return service.resgatar(id, dto);
    }

    @PatchMapping("/{id}/rendimento")
    public AtivoDTO rendimento(@PathVariable Long id, @Valid @RequestBody MovimentacaoAtivoDTO dto) {
        return service.registrarRendimento(id, dto);
    }

    @GetMapping("/{id}/impacto-cancelamento")
    public RespostaImpacto impactoCancelamento(@PathVariable Long id) {
        return service.calcularImpactoCancelamento(id);
    }

    @GetMapping("/{id}/impacto-exclusao")
    public RespostaImpacto impactoExclusao(@PathVariable Long id) {
        return service.calcularImpactoExclusao(id);
    }
}
