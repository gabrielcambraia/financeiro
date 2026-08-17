package com.financeiro.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Configuration
public class ConfiguracaoSpa implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Bundles do Vite em /assets/** têm hash no nome do arquivo: o conteúdo
        // de um hash nunca muda, então pode cachear por muito tempo.
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable());

        // index.html (e o fallback do SPA) não pode ficar em cache: se o
        // navegador guardar uma versão antiga, ela referencia bundles de
        // /assets/** que um deploy seguinte já apagou, e o pedido desses
        // arquivos inexistentes cai no fallback abaixo e devolve HTML no
        // lugar do JS/CSS esperado, quebrando o app silenciosamente.
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noStore())
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        // Se o arquivo existe (js, css, imagens etc), serve ele
                        // Se não existe, serve o index.html para o React Router tratar
                        return (requested.exists() && requested.isReadable())
                                ? requested
                                : new ClassPathResource("/static/index.html");
                    }
                });
    }
}
