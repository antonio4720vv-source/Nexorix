package com.nexorix.whatsapp;

import com.nexorix.user.User;
import com.nexorix.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PurchaseNoteServiceTest {

    private WhatsappLinkRepository linkRepository;
    private PurchaseColumnRepository columnRepository;
    private PurchaseNoteRepository noteRepository;
    private PurchaseNoteService service;
    private User ana;

    @BeforeEach
    void setUp() {
        linkRepository = mock(WhatsappLinkRepository.class);
        columnRepository = mock(PurchaseColumnRepository.class);
        noteRepository = mock(PurchaseNoteRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        service = new PurchaseNoteService(linkRepository, columnRepository, noteRepository, userRepository,
                JsonMapper.builder().build(), "+57 300 000 0000");

        ana = new User("Ana", "ana", "ana@nexorix.com", "1", "hash");
        ReflectionTestUtils.setField(ana, "id", 1L);
        when(userRepository.findByUsername("ana")).thenReturn(Optional.of(ana));
        when(linkRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(columnRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(noteRepository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void normalizaElNumero() {
        assertThat(PurchaseNoteService.normalizePhone("300 123 4567")).isEqualTo("573001234567");
        assertThat(PurchaseNoteService.normalizePhone("+57 (300) 123-4567")).isEqualTo("573001234567");
        assertThat(PurchaseNoteService.normalizePhone("+1 555 000 1111")).isEqualTo("15550001111");
        assertThatThrownBy(() -> PurchaseNoteService.normalizePhone("123")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void creaLlavesSinTildesNiEspacios() {
        assertThat(PurchaseNoteService.keyOf("Para quién")).isEqualTo("para_quien");
        assertThat(PurchaseNoteService.keyOf("¿Garantía (meses)?")).isEqualTo("garantia_meses");
        assertThat(PurchaseNoteService.keyOf("!!!")).isEqualTo("columna");
    }

    @Test
    void alGuardarElNumeroQuedaPendienteConUnCodigo() {
        when(linkRepository.findByPhone("573001234567")).thenReturn(Optional.empty());
        when(linkRepository.findByUserId(1L)).thenReturn(Optional.empty());

        PurchaseNoteService.Settings settings = service.savePhone("ana", "300 123 4567");

        assertThat(settings.phone()).isEqualTo("573001234567");
        assertThat(settings.verified()).isFalse();
        assertThat(settings.verificationCode()).matches("\\d{6}");
        assertThat(settings.businessPhone()).isEqualTo("573000000000");
    }

    @Test
    void noDejaUsarElNumeroDeOtraCuenta() {
        User otra = new User("Otra", "otra", "o@nexorix.com", "2", "hash");
        ReflectionTestUtils.setField(otra, "id", 2L);
        when(linkRepository.findByPhone("573001234567")).thenReturn(Optional.of(new WhatsappLink(otra, "573001234567")));

        assertThatThrownBy(() -> service.savePhone("ana", "3001234567"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("otra cuenta");
    }

    @Test
    void laPrimeraVezCreaLasColumnasDeEjemplo() {
        when(columnRepository.findByUserIdOrderByPositionAscIdAsc(1L)).thenReturn(List.of(), List.of(
                new PurchaseColumn(ana, "producto", "Producto", null, 0)));

        assertThat(service.columns("ana")).hasSize(1);
        org.mockito.Mockito.verify(columnRepository, org.mockito.Mockito.times(PurchaseNoteService.DEFAULT_COLUMNS.size()))
                .save(any(PurchaseColumn.class));
    }

    @Test
    void noSePuedeBorrarLaUltimaColumna() {
        PurchaseColumn only = new PurchaseColumn(ana, "producto", "Producto", null, 0);
        when(columnRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(only));
        when(columnRepository.countByUserId(1L)).thenReturn(1L);

        assertThatThrownBy(() -> service.deleteColumn("ana", 5L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void laEdicionAManoSoloGuardaColumnasExistentes() {
        PurchaseNote note = new PurchaseNote(ana, 1L, new BigDecimal("1000"), "Tienda", LocalDateTime.now());
        when(noteRepository.findByIdAndUserId(7L, 1L)).thenReturn(Optional.of(note));
        when(columnRepository.findByUserIdOrderByPositionAscIdAsc(1L)).thenReturn(List.of(
                new PurchaseColumn(ana, "producto", "Producto", null, 0)));

        PurchaseNoteService.NoteView view = service.updateValues("ana", 7L,
                Map.of("producto", " Leche ", "hackeo", "x"));

        assertThat(view.values()).containsOnlyKeys("producto").containsEntry("producto", "Leche");
        assertThat(view.status()).isEqualTo("RESPONDIDA");
    }

    @Test
    void elCsvEstaProtegidoContraFormulas() {
        PurchaseNote note = new PurchaseNote(ana, 1L, new BigDecimal("1000"), "=HYPERLINK(\"x\")", LocalDateTime.now());
        note.answered("TEXTO", "dije algo", "{\"producto\":\"+malo\"}");
        when(noteRepository.findByUserIdOrderByPurchaseDateDescIdDesc(1L)).thenReturn(List.of(note));
        when(columnRepository.findByUserIdOrderByPositionAscIdAsc(1L)).thenReturn(List.of(
                new PurchaseColumn(ana, "producto", "Producto", null, 0)));

        String csv = new String(service.csv("ana"), StandardCharsets.UTF_8);

        assertThat(csv).contains("Producto").contains("'+malo").contains("'=HYPERLINK").contains("1000,00");
    }
}
