package com.nexorix.whatsapp;

import com.nexorix.transaction.PurchaseRegisteredEvent;
import com.nexorix.user.User;
import com.nexorix.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseQuestionNotifierTest {

    private WhatsappLinkRepository linkRepository;
    private PurchaseNoteRepository noteRepository;
    private WhatsappClient whatsapp;
    private PurchaseQuestionNotifier notifier;
    private WhatsappLink link;
    private PurchaseNote saved;

    private final PurchaseRegisteredEvent event = new PurchaseRegisteredEvent(
            50L, 1L, new BigDecimal("25000.00"), "Exito Calle 80", LocalDateTime.now());

    @BeforeEach
    void setUp() {
        linkRepository = mock(WhatsappLinkRepository.class);
        noteRepository = mock(PurchaseNoteRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        whatsapp = mock(WhatsappClient.class);

        User ana = new User("Ana", "ana", "ana@nexorix.com", "1", "hash");
        ReflectionTestUtils.setField(ana, "id", 1L);
        link = new WhatsappLink(ana, "573001234567");
        link.markVerified();

        when(whatsapp.isEnabled()).thenReturn(true);
        when(userRepository.getReferenceById(1L)).thenReturn(ana);
        when(linkRepository.findByUserId(1L)).thenReturn(Optional.of(link));
        when(noteRepository.save(any())).thenAnswer(call -> {
            saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 9L);
            return saved;
        });
        when(noteRepository.findById(9L)).thenAnswer(call -> Optional.ofNullable(saved));

        notifier = new PurchaseQuestionNotifier(linkRepository, noteRepository, userRepository, whatsapp,
                new TransactionTemplate(mock(PlatformTransactionManager.class)), new SyncTaskExecutor(),
                "pregunta_compra", "es", 20);
    }

    @Test
    void preguntaConLaPlantillaYGuardaElIdDelMensaje() {
        when(whatsapp.sendTemplate(eq("573001234567"), eq("pregunta_compra"), eq("es"), any())).thenReturn("wamid.q");

        notifier.onPurchase(event);

        verify(whatsapp).sendTemplate("573001234567", "pregunta_compra", "es",
                List.of(PurchaseQuestionNotifier.money(new BigDecimal("25000.00")), "Exito Calle 80"));
        assertThat(saved.getStatus()).isEqualTo(PurchaseNoteStatus.PREGUNTADA);
        assertThat(saved.getQuestionMessageId()).isEqualTo("wamid.q");
        assertThat(saved.getTransactionId()).isEqualTo(50L);
    }

    @Test
    void siWhatsappFallaLaFilaQuedaConError() {
        when(whatsapp.sendTemplate(anyString(), anyString(), anyString(), any()))
                .thenThrow(new WhatsappException("WhatsApp rechazó la solicitud."));

        notifier.onPurchase(event);

        assertThat(saved.getStatus()).isEqualTo(PurchaseNoteStatus.ERROR);
        assertThat(saved.getErrorMessage()).contains("rechazó");
    }

    @Test
    void noEscribeANumerosSinVerificarNiEnPausa() {
        WhatsappLink pending = new WhatsappLink(link.getUser(), "573001234567");
        when(linkRepository.findByUserId(1L)).thenReturn(Optional.of(pending));
        notifier.onPurchase(event);

        link.setEnabled(false);
        when(linkRepository.findByUserId(1L)).thenReturn(Optional.of(link));
        notifier.onPurchase(event);

        verify(whatsapp, never()).sendTemplate(anyString(), anyString(), anyString(), any());
        verify(noteRepository, never()).save(any());
    }

    @Test
    void respetaElLimiteDiario() {
        when(noteRepository.countByUserIdAndCreatedAtAfter(eq(1L), any())).thenReturn(20L);
        notifier.onPurchase(event);
        verify(whatsapp, never()).sendTemplate(anyString(), anyString(), anyString(), any());
    }

    @Test
    void formateaElMontoEnPesos() {
        assertThat(PurchaseQuestionNotifier.money(new BigDecimal("25000.00"))).contains("25.000").doesNotContain(",00");
        assertThat(PurchaseQuestionNotifier.money(new BigDecimal("1500.50"))).contains("1.500,50");
    }
}
