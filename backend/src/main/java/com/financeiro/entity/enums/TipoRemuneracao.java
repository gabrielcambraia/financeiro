package com.financeiro.entity.enums;

/**
 * Indexador de rendimento automático de um {@link com.financeiro.entity.Ativo}.
 * O significado de {@code taxa} depende do tipo:
 * <ul>
 *   <li>{@code NENHUMA}: sem rendimento automático — ativo 100% manual (via
 *   {@code AtivoService.registrarRendimento}), como sempre foi.</li>
 *   <li>{@code PRE_FIXADA}: taxa = % ao ano (ex. 12 = 12% a.a.).</li>
 *   <li>{@code POS_CDI}: taxa = % do CDI (ex. 110 = 110% do CDI).</li>
 *   <li>{@code POS_SELIC}: taxa = % da Selic (mesma leitura do CDI).</li>
 *   <li>{@code IPCA_MAIS}: taxa = spread % ao ano sobre o IPCA (ex. 6 = IPCA + 6%).</li>
 * </ul>
 */
public enum TipoRemuneracao {
    NENHUMA, PRE_FIXADA, POS_CDI, POS_SELIC, IPCA_MAIS
}
