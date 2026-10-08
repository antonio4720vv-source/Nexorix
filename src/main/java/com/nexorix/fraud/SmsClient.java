package com.nexorix.fraud;

import com.nexorix.whatsapp.WhatsappException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * SMS con Twilio (https://www.twilio.com/docs/sms/api/message-resource#create-a-message-resource).
 * Sin TWILIO_ACCOUNT_SID / TWILIO_AUTH_TOKEN / TWILIO_FROM no envia nada: solo deja el
 * mensaje en el log como "[DEMO SMS]", para poder probar todo sin cuenta.
 */
@Component
public class SmsClient {

    private static final Logger log = LoggerFactory.getLogger(SmsClient.class);

    private final RestTemplate restTemplate;
    private final String accountSid;
    private final String authToken;
    private final String from;

    public SmsClient(
            @Value("${nexorix.sms.twilio.account-sid:}") String accountSid,
            @Value("${nexorix.sms.twilio.auth-token:}") String authToken,
            @Value("${nexorix.sms.twilio.from:}") String from
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(20_000);
        this.restTemplate = new RestTemplate(factory);
        this.accountSid = accountSid.trim();
        this.authToken = authToken.trim();
        this.from = from.trim();
        log.info("SMS (Twilio) configurado: {}", isEnabled());
    }

    public boolean isEnabled() {
        return !accountSid.isEmpty() && !authToken.isEmpty() && !from.isEmpty();
    }

    /** Devuelve true si Twilio acepto el mensaje; false si esta en modo demo. Lanza WhatsappException si falla. */
    public boolean send(String phoneDigits, String text) {
        if (!isEnabled()) {
            log.warn("[DEMO SMS] a +{}: {}", phoneDigits, text);
            return false;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(accountSid, authToken);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", "+" + phoneDigits);
        form.add("From", from);
        form.add("Body", text);
        try {
            restTemplate.postForObject("https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json",
                    new HttpEntity<>(form, headers), String.class);
            return true;
        } catch (RestClientException exception) {
            throw new WhatsappException("No fue posible enviar el SMS.", exception);
        }
    }
}
