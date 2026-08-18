package com.financeiro;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class FinanceiroApplication {
    public static void main(String[] args) {
        // Algumas redes anunciam IPv6 sem rotear de verdade (comum em rede
        // doméstica); a JVM prefere IPv6 por padrão quando o host tem AAAA
        // (ex.: smtp.gmail.com), travando em timeout em vez de cair pro IPv4
        // que funciona. Precisa ser definido antes de qualquer classe de rede
        // carregar, por isso é a primeira linha do main.
        System.setProperty("java.net.preferIPv4Stack", "true");
        SpringApplication.run(FinanceiroApplication.class, args);
    }
}
