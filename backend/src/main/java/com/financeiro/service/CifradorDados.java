package com.financeiro.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * AES-256-GCM para dados PII (CPF/CNPJ).
 * Formato do blob: versao(1B) | iv(12B) | ciphertext+tag(var).
 * HMAC-SHA-256 com chave separada para gerar um hash de busca.
 * Env vars: FINANCEIRO_CHAVE_CIFRAGEM (base64, 32 bytes) e FINANCEIRO_CHAVE_HMAC_DOC (base64, ≥16 bytes).
 */
@Component
public class CifradorDados {

    private static final byte VERSAO = 1;
    private static final int IV_SIZE = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec chaveCifragem;
    private final SecretKeySpec chaveHmac;
    private final SecureRandom secureRandom = new SecureRandom();

    public CifradorDados(
            @Value("${financeiro.cifragem.chave}") String chaveBase64,
            @Value("${financeiro.cifragem.chave-hmac}") String chaveHmacBase64) {
        byte[] chaveBytes = Base64.getDecoder().decode(chaveBase64);
        byte[] chaveHmacBytes = Base64.getDecoder().decode(chaveHmacBase64);
        if (chaveBytes.length != 32) {
            throw new IllegalStateException("FINANCEIRO_CHAVE_CIFRAGEM deve ter exatamente 32 bytes (256 bits)");
        }
        if (chaveHmacBytes.length < 16) {
            throw new IllegalStateException("FINANCEIRO_CHAVE_HMAC_DOC deve ter pelo menos 16 bytes");
        }
        this.chaveCifragem = new SecretKeySpec(chaveBytes, "AES");
        this.chaveHmac = new SecretKeySpec(chaveHmacBytes, "HmacSHA256");
    }

    public byte[] cifrar(String dados) {
        try {
            byte[] iv = new byte[IV_SIZE];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, chaveCifragem, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(dados.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buf = ByteBuffer.allocate(1 + IV_SIZE + ct.length);
            buf.put(VERSAO);
            buf.put(iv);
            buf.put(ct);
            return buf.array();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao cifrar dado", e);
        }
    }

    public String decifrar(byte[] blob) {
        try {
            ByteBuffer buf = ByteBuffer.wrap(blob);
            byte versao = buf.get();
            if (versao != VERSAO) {
                throw new IllegalArgumentException("Versão de cifragem desconhecida: " + versao);
            }
            byte[] iv = new byte[IV_SIZE];
            buf.get(iv);
            byte[] ct = new byte[buf.remaining()];
            buf.get(ct);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, chaveCifragem, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao decifrar dado", e);
        }
    }

    public String hashDocumento(String documento) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(chaveHmac);
            return HexFormat.of().formatHex(mac.doFinal(documento.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar hash de documento", e);
        }
    }

    public static String hashSha256(String valor) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
