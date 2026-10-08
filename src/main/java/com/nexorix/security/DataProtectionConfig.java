package com.nexorix.security;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Carga la clave de cifrado antes de que Hibernate lea o escriba datos.
 * En produccion (cookie segura activada) la clave es OBLIGATORIA: sin ella la app no arranca.
 */
@Configuration
public class DataProtectionConfig {

    private static final Logger log = LoggerFactory.getLogger(DataProtectionConfig.class);

    private final String dataKey;
    private final boolean production;

    public DataProtectionConfig(
            @Value("${nexorix.security.data-key:}") String dataKey,
            @Value("${server.servlet.session.cookie.secure:false}") boolean production
    ) {
        this.dataKey = dataKey;
        this.production = production;
        // En el constructor: este bean se crea antes que el EntityManager.
        FieldCipher.configure(dataKey);
    }

    @PostConstruct
    void check() {
        if (FieldCipher.isEnabled()) {
            log.info("Cifrado de datos sensibles ACTIVO (AES-256-GCM).");
            return;
        }
        if (production) {
            throw new IllegalStateException(
                    "Falta NEXORIX_DATA_KEY. En produccion los datos sensibles deben ir cifrados. "
                            + "Genera la clave con: openssl rand -base64 32");
        }
        log.warn("*** Cifrado de datos DESACTIVADO (falta NEXORIX_DATA_KEY). Solo aceptable en desarrollo. ***");
    }
}
