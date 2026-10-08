package com.nexorix.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class DiditConfig {

    /**
     * RestTemplate para llamar a Didit.
     *
     * Usamos BufferingClientHttpRequestFactory para que el cuerpo
     * de la peticion se arme completo en memoria antes de enviarlo.
     * Asi la peticion sale con la cabecera Content-Length en lugar de
     * "Transfer-Encoding: chunked". Algunos servidores no leen cuerpos
     * enviados por partes y los reciben vacios, lo que provoca errores
     * como "workflow_id: This field is required."
     */
    @Bean
    public RestTemplate restTemplate() {

        SimpleClientHttpRequestFactory baseFactory =
                new SimpleClientHttpRequestFactory();

        baseFactory.setConnectTimeout(10_000);
        baseFactory.setReadTimeout(30_000);

        return new RestTemplate(
                new BufferingClientHttpRequestFactory(baseFactory)
        );
    }
}
