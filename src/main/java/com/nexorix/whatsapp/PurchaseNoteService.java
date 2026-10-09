package com.nexorix.whatsapp;

import com.nexorix.report.CsvReportWriter;
import com.nexorix.user.User;
import com.nexorix.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * La tabla personalizada de compras: el numero de WhatsApp de la persona,
 * sus columnas y sus filas.
 */
@Service
public class PurchaseNoteService {

    static final int MAX_COLUMNS = 15;
    static final int CODE_MINUTES = 30;

    /** Columnas con las que empieza cada persona (las puede cambiar o borrar). */
    static final List<String[]> DEFAULT_COLUMNS = List.of(
            new String[]{"producto", "Producto", "Qué se compró, con marca si la dice"},
            new String[]{"cantidad", "Cantidad", "Cuántas unidades o cuánto (kilos, litros...)"},
            new String[]{"tienda", "Tienda", "Dónde se compró"},
            new String[]{"para_quien", "Para quién", "Para quién era la compra (yo, la casa, un hijo...)"},
            new String[]{"motivo", "Motivo", "Para qué o por qué se compró"}
    );

    private static final TypeReference<Map<String, String>> MAP = new TypeReference<>() {
    };
    private static final SecureRandom RANDOM = new SecureRandom();

    private final WhatsappLinkRepository linkRepository;
    private final PurchaseColumnRepository columnRepository;
    private final PurchaseNoteRepository noteRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final String businessPhone;

    public PurchaseNoteService(
            WhatsappLinkRepository linkRepository,
            PurchaseColumnRepository columnRepository,
            PurchaseNoteRepository noteRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper,
            @Value("${nexorix.whatsapp.business-phone:}") String businessPhone
    ) {
        this.linkRepository = linkRepository;
        this.columnRepository = columnRepository;
        this.noteRepository = noteRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.businessPhone = digits(businessPhone);
    }

    // ============================================================
    // NUMERO DE WHATSAPP
    // ============================================================

    public record Settings(String phone, boolean verified, boolean enabled, String verificationCode,
                           String businessPhone) {
    }

    @Transactional(readOnly = true)
    public Settings settings(String username) {
        User user = user(username);
        return linkRepository.findByUserId(user.getId())
                .map(this::toSettings)
                .orElse(new Settings(null, false, false, null, businessPhone));
    }

    /** Guarda el numero y crea un codigo nuevo: queda sin verificar hasta que la persona lo mande. */
    @Transactional
    public Settings savePhone(String username, String rawPhone) {
        User user = user(username);
        String phone = normalizePhone(rawPhone);

        linkRepository.findByPhone(phone)
                .filter(other -> !other.getUser().getId().equals(user.getId()))
                .ifPresent(other -> {
                    throw new IllegalArgumentException("Ese número de WhatsApp ya está registrado en otra cuenta.");
                });

        WhatsappLink link = linkRepository.findByUserId(user.getId())
                .orElseGet(() -> new WhatsappLink(user, phone));
        if (!link.isVerified() || !link.getPhone().equals(phone) || link.getVerificationCode() == null) {
            link.changePhone(phone, newCode(), LocalDateTime.now().plusMinutes(CODE_MINUTES));
        }
        return toSettings(linkRepository.save(link));
    }

    @Transactional
    public Settings setEnabled(String username, boolean enabled) {
        User user = user(username);
        WhatsappLink link = linkRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Primero registra tu número de WhatsApp."));
        link.setEnabled(enabled);
        return toSettings(linkRepository.save(link));
    }

    @Transactional
    public void removePhone(String username) {
        User user = user(username);
        linkRepository.findByUserId(user.getId()).ifPresent(linkRepository::delete);
    }

    private Settings toSettings(WhatsappLink link) {
        boolean codeValid = link.getVerificationCode() != null && link.getCodeExpiresAt() != null
                && link.getCodeExpiresAt().isAfter(LocalDateTime.now());
        return new Settings(link.getPhone(), link.isVerified(), link.isEnabled(),
                link.isVerified() || !codeValid ? null : link.getVerificationCode(), businessPhone);
    }

    /** Solo digitos. Si son 10 digitos que empiezan por 3 (celular de Colombia), le pone el 57. */
    public static String normalizePhone(String raw) {
        String phone = digits(raw);
        if (phone.length() == 10 && phone.startsWith("3")) {
            phone = "57" + phone;
        }
        if (phone.length() < 10 || phone.length() > 15) {
            throw new IllegalArgumentException("Escribe tu número de WhatsApp con indicativo, por ejemplo +57 300 123 4567.");
        }
        return phone;
    }

    static String digits(String raw) {
        return raw == null ? "" : raw.replaceAll("\\D", "");
    }

    private static String newCode() {
        return String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
    }

    // ============================================================
    // COLUMNAS
    // ============================================================

    public record ColumnView(Long id, String key, String label, String hint, int position) {
        static ColumnView of(PurchaseColumn column) {
            return new ColumnView(column.getId(), column.getKey(), column.getLabel(), column.getHint(), column.getPosition());
        }
    }

    @Transactional
    public List<ColumnView> columns(String username) {
        return columnsOf(user(username)).stream().map(ColumnView::of).toList();
    }

    /** Las columnas de la persona; la primera vez se crean las de ejemplo. */
    @Transactional
    public List<PurchaseColumn> columnsOf(User user) {
        List<PurchaseColumn> columns = columnRepository.findByUserIdOrderByPositionAscIdAsc(user.getId());
        // Siempre queda al menos una columna (ver deleteColumn): vacio = primera vez.
        if (columns.isEmpty()) {
            int position = 0;
            for (String[] column : DEFAULT_COLUMNS) {
                columnRepository.save(new PurchaseColumn(user, column[0], column[1], column[2], position++));
            }
            columns = columnRepository.findByUserIdOrderByPositionAscIdAsc(user.getId());
        }
        return columns;
    }

    @Transactional
    public ColumnView addColumn(String username, String label, String hint) {
        User user = user(username);
        List<PurchaseColumn> existing = columnsOf(user);
        if (existing.size() >= MAX_COLUMNS) {
            throw new IllegalArgumentException("Puedes tener máximo " + MAX_COLUMNS + " columnas.");
        }
        String cleanLabel = cleanLabel(label);
        String base = keyOf(cleanLabel);
        String key = base;
        for (int i = 2; columnRepository.existsByUserIdAndKey(user.getId(), key); i++) {
            key = base + "_" + i;
        }
        int position = existing.stream().mapToInt(PurchaseColumn::getPosition).max().orElse(-1) + 1;
        return ColumnView.of(columnRepository.save(new PurchaseColumn(user, key, cleanLabel, cleanHint(hint), position)));
    }

    /** Cambia nombre y descripcion. La llave no cambia, para no perder los valores ya guardados. */
    @Transactional
    public ColumnView updateColumn(String username, Long id, String label, String hint, Integer position) {
        User user = user(username);
        PurchaseColumn column = columnRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Columna no encontrada."));
        column.setLabel(cleanLabel(label));
        column.setHint(cleanHint(hint));
        if (position != null) {
            column.setPosition(Math.max(0, Math.min(position, 99)));
        }
        return ColumnView.of(columnRepository.save(column));
    }

    @Transactional
    public void deleteColumn(String username, Long id) {
        User user = user(username);
        PurchaseColumn column = columnRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Columna no encontrada."));
        if (columnRepository.countByUserId(user.getId()) <= 1) {
            throw new IllegalArgumentException("La tabla debe tener al menos una columna.");
        }
        columnRepository.delete(column);
    }

    private static String cleanLabel(String label) {
        String clean = label == null ? "" : label.trim().replaceAll("\\s+", " ");
        if (clean.isEmpty()) {
            throw new IllegalArgumentException("Escribe el nombre de la columna.");
        }
        if (clean.length() > 40) {
            throw new IllegalArgumentException("El nombre de la columna tiene máximo 40 caracteres.");
        }
        return clean;
    }

    private static String cleanHint(String hint) {
        String clean = hint == null ? "" : hint.trim().replaceAll("\\s+", " ");
        if (clean.length() > 200) {
            throw new IllegalArgumentException("La descripción tiene máximo 200 caracteres.");
        }
        return clean.isEmpty() ? null : clean;
    }

    /** "Para quién" -> "para_quien". */
    static String keyOf(String label) {
        String key = Normalizer.normalize(label, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (key.isEmpty()) {
            key = "columna";
        }
        return key.length() > 30 ? key.substring(0, 30) : key;
    }

    // ============================================================
    // FILAS
    // ============================================================

    public record NoteView(Long id, Long transactionId, String amount, String description, String purchaseDate,
                           String status, String answerType, String transcript, Map<String, String> values,
                           String errorMessage, String category) {
    }

    public record CategoryView(String name, long purchases, String total) {
    }

    @Transactional(readOnly = true)
    public List<NoteView> notes(String username) {
        User user = user(username);
        return noteRepository.findByUserIdOrderByPurchaseDateDescIdDesc(user.getId()).stream()
                .map(this::toView)
                .toList();
    }

    /** Correccion a mano: solo se guardan las llaves de columnas que existen. */
    @Transactional
    public NoteView updateValues(String username, Long id, Map<String, String> values) {
        User user = user(username);
        PurchaseNote note = noteRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compra no encontrada."));

        Map<String, String> merged = valuesOf(note);
        for (PurchaseColumn column : columnsOf(user)) {
            if (values != null && values.containsKey(column.getKey())) {
                String value = values.get(column.getKey());
                String clean = value == null ? "" : value.trim();
                if (clean.length() > PurchaseAnswerReader.MAX_VALUE_LENGTH) {
                    throw new IllegalArgumentException("Cada valor tiene máximo "
                            + PurchaseAnswerReader.MAX_VALUE_LENGTH + " caracteres.");
                }
                if (clean.isEmpty()) {
                    merged.remove(column.getKey());
                } else {
                    merged.put(column.getKey(), clean);
                }
            }
        }
        note.setValuesJson(writeValues(merged));
        return toView(noteRepository.save(note));
    }

    // ============================================================
    // CATEGORIAS ("la columna de helados, la de cigarrillos...")
    // ============================================================

    static final int MAX_CATEGORIES = 40;

    @Transactional(readOnly = true)
    public List<CategoryView> categories(String username) {
        return noteRepository.categoryTotals(user(username).getId()).stream()
                .map(row -> new CategoryView((String) row[0], (Long) row[1], ((java.math.BigDecimal) row[2]).toPlainString()))
                .toList();
    }

    /** Nombres de las categorias que ya usa la persona (para que la IA elija una existente). */
    @Transactional(readOnly = true)
    public List<String> categoryNames(User user) {
        return noteRepository.categoryTotals(user.getId()).stream().map(row -> (String) row[0]).toList();
    }

    /** Lo ultimo que la persona hizo con ese comercio, si ya lo habia clasificado. */
    @Transactional(readOnly = true)
    public String suggestedCategory(User user, String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return noteRepository.findFirstByUserIdAndDescriptionIgnoreCaseAndCategoryNotNullOrderByIdDesc(
                user.getId(), description.trim()).map(PurchaseNote::getCategory).orElse(null);
    }

    /** La persona elige (o crea) la categoria de una compra. Vacio = quitarla. */
    @Transactional
    public NoteView updateCategory(String username, Long id, String category) {
        User user = user(username);
        PurchaseNote note = noteRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compra no encontrada."));
        String clean = cleanCategory(category);
        if (clean != null && !clean.equalsIgnoreCase(note.getCategory())
                && noteRepository.categoryTotals(user.getId()).size() >= MAX_CATEGORIES
                && noteRepository.categoryTotals(user.getId()).stream().noneMatch(r -> clean.equalsIgnoreCase((String) r[0]))) {
            throw new IllegalArgumentException("Puedes tener máximo " + MAX_CATEGORIES + " categorías.");
        }
        note.setCategory(canonical(user, clean));
        return toView(noteRepository.save(note));
    }

    /** Si ya existe "Helados", escribir "helados" reutiliza el nombre existente. */
    public String canonical(User user, String clean) {
        if (clean == null) {
            return null;
        }
        return categoryNames(user).stream().filter(clean::equalsIgnoreCase).findFirst().orElse(clean);
    }

    /** Recorta, quita espacios repetidos y pone la primera letra en mayuscula. Null si queda vacio. */
    public static String cleanCategory(String category) {
        String clean = category == null ? "" : category.trim().replaceAll("\\s+", " ");
        if (clean.isEmpty() || clean.equalsIgnoreCase("null")) {
            return null;
        }
        if (clean.length() > 40) {
            throw new IllegalArgumentException("La categoría tiene máximo 40 caracteres.");
        }
        return clean.substring(0, 1).toUpperCase(Locale.ROOT) + clean.substring(1);
    }

    @Transactional
    public void deleteNote(String username, Long id) {
        User user = user(username);
        PurchaseNote note = noteRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compra no encontrada."));
        noteRepository.delete(note);
    }

    /** CSV para Excel (punto y coma, protegido contra inyeccion de formulas). */
    @Transactional
    public byte[] csv(String username) {
        User user = user(username);
        List<PurchaseColumn> columns = columnsOf(user);
        DateTimeFormatter date = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        StringBuilder csv = new StringBuilder("﻿");
        List<String> head = new ArrayList<>(List.of("Fecha", "Descripción", "Monto"));
        columns.forEach(column -> head.add(column.getLabel()));
        head.add("Lo que dijiste");
        csv.append(String.join(";", head.stream().map(CsvReportWriter::cell).toList())).append("\r\n");

        for (PurchaseNote note : noteRepository.findByUserIdOrderByPurchaseDateDescIdDesc(user.getId())) {
            Map<String, String> values = valuesOf(note);
            List<String> row = new ArrayList<>();
            row.add(CsvReportWriter.cell(note.getPurchaseDate().format(date)));
            row.add(CsvReportWriter.cell(note.getDescription()));
            row.add(CsvReportWriter.number(note.getAmount()));
            columns.forEach(column -> row.add(CsvReportWriter.cell(values.get(column.getKey()))));
            row.add(CsvReportWriter.cell(note.getTranscript()));
            csv.append(String.join(";", row)).append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private NoteView toView(PurchaseNote note) {
        return new NoteView(note.getId(), note.getTransactionId(), note.getAmount().toPlainString(),
                note.getDescription(), note.getPurchaseDate().toString(), note.getStatus().name(),
                note.getAnswerType(), note.getTranscript(), valuesOf(note), note.getErrorMessage(), note.getCategory());
    }

    Map<String, String> valuesOf(PurchaseNote note) {
        if (note.getValuesJson() == null || note.getValuesJson().isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return new LinkedHashMap<>(objectMapper.readValue(note.getValuesJson(), MAP));
        } catch (Exception exception) {
            return new LinkedHashMap<>();
        }
    }

    String writeValues(Map<String, String> values) {
        return objectMapper.writeValueAsString(values);
    }

    private User user(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Inicia sesión para continuar."));
    }
}
