package com.nexorix.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * Envia el codigo de recuperacion por correo.
 *
 * Si el correo no esta configurado (desarrollo), el codigo se escribe
 * en la consola de IntelliJ para poder probar sin un servidor de correo.
 */
@Component
public class RecoveryMailer {

    private static final Logger log = LoggerFactory.getLogger(RecoveryMailer.class);

    private final JavaMailSenderImpl sender;
    private final String from;

    public RecoveryMailer(
            @Value("${nexorix.mail.host:}") String host,
            @Value("${nexorix.mail.port:587}") int port,
            @Value("${nexorix.mail.username:}") String username,
            @Value("${nexorix.mail.password:}") String password,
            @Value("${nexorix.mail.from:}") String from
    ) {
        this.from = from.isBlank() ? username : from;

        if (host.isBlank()) {
            this.sender = null;
            log.warn("Correo NO configurado: los codigos de recuperacion se mostraran "
                    + "en la consola. Esto solo sirve para desarrollo.");
            return;
        }

        JavaMailSenderImpl mail = new JavaMailSenderImpl();
        mail.setHost(host);
        mail.setPort(port);
        mail.setUsername(username);
        mail.setPassword(password);
        mail.setDefaultEncoding("UTF-8");

        Properties properties = mail.getJavaMailProperties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.starttls.required", "true");
        properties.put("mail.smtp.connectiontimeout", "10000");
        properties.put("mail.smtp.timeout", "10000");

        this.sender = mail;
        log.info("Correo configurado: {} (remitente {})", host, this.from);
    }

    public void sendRecoveryCode(String to, String name, String code) {

        if (sender == null) {
            log.warn("=== CODIGO DE RECUPERACION (solo desarrollo) para {}: {} ===",
                    maskEmail(to), code);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("Tu código de Nexorix: " + code);
        message.setText("""
                Hola %s,

                Tu código para recuperar el acceso a Nexorix es:

                    %s

                Vence en 15 minutos. Si no fuiste tú, ignora este correo:
                nadie podrá entrar a tu cuenta solo con este código, porque
                también debe verificar tu identidad.

                Equipo Nexorix
                """.formatted(name, code));

        sender.send(message);
        log.info("Codigo de recuperacion enviado a {}", maskEmail(to));
    }

    /** ana.prueba@nexorix.com -> an*******@nexorix.com */
    static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int at = email.indexOf('@');
        String visible = email.substring(0, Math.min(2, at));
        return visible + "*".repeat(Math.max(3, at - visible.length())) + email.substring(at);
    }
}
