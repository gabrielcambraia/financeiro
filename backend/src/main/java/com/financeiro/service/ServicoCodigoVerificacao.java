package com.financeiro.service;

import com.financeiro.entity.CodigoVerificacao;
import com.financeiro.entity.enums.CanalNotificacao;
import com.financeiro.entity.enums.PropositoCodigo;
import com.financeiro.repository.CodigoVerificacaoRepository;
import com.financeiro.service.notificacao.RoteadorNotificacao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServicoCodigoVerificacao {

    private static final int TTL_MINUTOS = 10;
    private static final int MAX_TENTATIVAS = 5;
    private static final int COOLDOWN_SEGUNDOS = 60;
    private static final int MAX_POR_HORA = 5;

    private final CodigoVerificacaoRepository codigoVerificacaoRepository;
    private final RoteadorNotificacao roteadorNotificacao;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public void solicitar(String destinatario, CanalNotificacao canal, PropositoCodigo proposito,
                          String ipOrigem, Long usuarioId) {
        long totalHora = codigoVerificacaoRepository.countByDestinatarioAndPropositoAndCriadoEmAfter(
                destinatario, proposito, LocalDateTime.now().minusHours(1));
        if (totalHora >= MAX_POR_HORA) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Muitos códigos solicitados. Aguarde antes de tentar novamente.");
        }

        Optional<CodigoVerificacao> recente = codigoVerificacaoRepository
                .findFirstByDestinatarioAndPropositoAndUsadoEmIsNullOrderByCriadoEmDesc(destinatario, proposito);
        if (recente.isPresent() && recente.get().getCriadoEm().plusSeconds(COOLDOWN_SEGUNDOS).isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Aguarde antes de solicitar outro código.");
        }

        codigoVerificacaoRepository.deleteByDestinatarioAndPropositoAndExpiraEmBefore(
                destinatario, proposito, LocalDateTime.now());

        String codigo = String.format("%06d", secureRandom.nextInt(1_000_000));
        LocalDateTime agora = LocalDateTime.now();

        codigoVerificacaoRepository.save(CodigoVerificacao.builder()
                .usuarioId(usuarioId)
                .destinatario(destinatario)
                .canal(canal)
                .proposito(proposito)
                .codigoHash(CifradorDados.hashSha256(codigo))
                .expiraEm(agora.plusMinutes(TTL_MINUTOS))
                .ipOrigem(ipOrigem)
                .build());

        // Envio assíncrono (via @Async no RoteadorNotificacao) — não bloqueia a transação
        roteadorNotificacao.enviar(canal, destinatario, proposito, codigo);
    }

    @Transactional
    public void verificar(String destinatario, PropositoCodigo proposito, String codigoInformado) {
        CodigoVerificacao codigo = codigoVerificacaoRepository
                .findFirstByDestinatarioAndPropositoAndUsadoEmIsNullOrderByCriadoEmDesc(destinatario, proposito)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Código inválido ou expirado"));

        if (codigo.getExpiraEm().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Código expirado");
        }

        if (codigo.getTentativas() >= MAX_TENTATIVAS) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Número máximo de tentativas atingido");
        }

        codigo.setTentativas(codigo.getTentativas() + 1);

        String hashInformado = CifradorDados.hashSha256(codigoInformado);
        boolean valido = MessageDigest.isEqual(
                hashInformado.getBytes(StandardCharsets.UTF_8),
                codigo.getCodigoHash().getBytes(StandardCharsets.UTF_8));

        if (!valido) {
            codigoVerificacaoRepository.save(codigo);
            log.warn("Código inválido para {} (proposito={})", destinatario, proposito);
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Código inválido");
        }

        codigoVerificacaoRepository.delete(codigo);
    }
}
