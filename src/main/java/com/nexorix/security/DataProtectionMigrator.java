package com.nexorix.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Al arrancar: ensancha las columnas (el texto cifrado es mas largo) y cifra los datos que
 * quedaron en claro de versiones anteriores. Es idempotente: si ya esta todo cifrado no hace nada.
 */
@Component
@Order(0)
public class DataProtectionMigrator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataProtectionMigrator.class);

    private final JdbcTemplate jdbc;

    public DataProtectionMigrator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (FieldCipher.isEnabled()) {
                widenColumns();
            }
            encryptLegacyRows();
        } catch (RuntimeException exception) {
            // No tumbar la app por esto, pero que se vea: los datos viejos siguen legibles.
            log.error("No se pudo completar la migracion de cifrado: {}", exception.getMessage());
        }
    }

    private void widenColumns() {
        for (String column : List.of("email", "cedula", "security_phone")) {
            jdbc.execute("ALTER TABLE users ALTER COLUMN " + column + " TYPE VARCHAR(400)");
        }
    }

    private void encryptLegacyRows() {
        // Con clave: cifra lo que este en claro. Sin clave: solo completa las huellas de busqueda.
        String pending = FieldCipher.isEnabled()
                ? "email NOT LIKE 'enc:v1:%' OR cedula NOT LIKE 'enc:v1:%' "
                + "OR (security_phone IS NOT NULL AND security_phone NOT LIKE 'enc:v1:%') "
                + "OR email_idx IS NULL OR cedula_idx IS NULL"
                : "email_idx IS NULL OR cedula_idx IS NULL";
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, email, cedula, security_phone FROM users WHERE " + pending);
        for (Map<String, Object> row : rows) {
            String email = FieldCipher.decrypt((String) row.get("email"));
            String cedula = FieldCipher.decrypt((String) row.get("cedula"));
            String phone = FieldCipher.decrypt((String) row.get("security_phone"));
            jdbc.update("UPDATE users SET email = ?, cedula = ?, security_phone = ?, email_idx = ?, cedula_idx = ? WHERE id = ?",
                    FieldCipher.encrypt(email), FieldCipher.encrypt(cedula), FieldCipher.encrypt(phone),
                    FieldCipher.blindIndex(email), FieldCipher.blindIndex(cedula), row.get("id"));
        }
        if (!rows.isEmpty()) {
            log.info("Cifrados {} usuarios que estaban en claro.", rows.size());
        }
    }
}
