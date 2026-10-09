package com.nexorix.whatsapp;

import com.nexorix.user.User;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Una fila de la tabla personalizada de compras: la pregunta que Nexorix
 * mando por WhatsApp despues de un gasto y lo que la persona respondio.
 *
 * Guarda una copia del monto, la descripcion y la fecha del movimiento para
 * que la tabla se lea sin depender del movimiento original.
 */
@Entity
@Table(name = "purchase_notes", indexes = {
        @Index(name = "idx_purchase_notes_user_status", columnList = "user_id,status"),
        @Index(name = "idx_purchase_notes_question", columnList = "question_message_id")
})
public class PurchaseNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** El gasto que origino la pregunta (puede no existir si la fila se creo a mano). */
    @Column(name = "transaction_id", unique = true)
    private Long transactionId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(name = "purchase_date", nullable = false)
    private LocalDateTime purchaseDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PurchaseNoteStatus status;

    /** Id del mensaje de WhatsApp con la pregunta (wamid...). Sirve cuando la persona responde citandolo. */
    @Column(name = "question_message_id", length = 128)
    private String questionMessageId;

    /** Lo que la persona dijo, tal cual (transcripcion de la nota de voz o el texto). */
    @Column(columnDefinition = "text")
    private String transcript;

    /** Valores de las columnas personalizadas, en JSON: {"producto":"Arroz","tienda":"Exito"}. */
    @Column(name = "values_json", columnDefinition = "text")
    private String valuesJson;

    /**
     * "Donde se mete" la compra: helados, cigarrillos, mercado... La persona la elige; la IA la propone
     * a partir de lo que dijo y de lo que ya ha hecho con esa misma tienda.
     */
    @Column(length = 40)
    private String category;

    /** VOZ o TEXTO. */
    @Column(name = "answer_type", length = 10)
    private String answerType;

    @Column(name = "error_message", length = 255)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    protected PurchaseNote() {
    }

    public PurchaseNote(User user, Long transactionId, BigDecimal amount, String description,
                        LocalDateTime purchaseDate) {
        this.user = user;
        this.transactionId = transactionId;
        this.amount = amount;
        this.description = description;
        this.purchaseDate = purchaseDate;
        this.status = PurchaseNoteStatus.PENDIENTE;
        this.createdAt = LocalDateTime.now();
    }

    public void questionSent(String messageId) {
        this.questionMessageId = messageId;
        this.status = PurchaseNoteStatus.PREGUNTADA;
        this.errorMessage = null;
    }

    public void answered(String answerType, String transcript, String valuesJson) {
        this.answerType = answerType;
        this.transcript = transcript;
        this.valuesJson = valuesJson;
        this.status = PurchaseNoteStatus.RESPONDIDA;
        this.answeredAt = LocalDateTime.now();
        this.errorMessage = null;
    }

    /** No se pudo mandar la pregunta. */
    public void failed(String message) {
        this.status = PurchaseNoteStatus.ERROR;
        this.errorMessage = shorten(message);
    }

    /** No se entendio una respuesta: la pregunta sigue abierta para que la persona lo intente otra vez. */
    public void answerFailed(String message) {
        this.errorMessage = shorten(message);
    }

    private static String shorten(String message) {
        return message == null ? null : message.length() > 255 ? message.substring(0, 255) : message;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getPurchaseDate() {
        return purchaseDate;
    }

    public PurchaseNoteStatus getStatus() {
        return status;
    }

    public String getQuestionMessageId() {
        return questionMessageId;
    }

    public String getTranscript() {
        return transcript;
    }

    public String getValuesJson() {
        return valuesJson;
    }

    /** Edicion a mano desde la pagina. */
    public void setValuesJson(String valuesJson) {
        this.valuesJson = valuesJson;
        if (status != PurchaseNoteStatus.RESPONDIDA) {
            this.status = PurchaseNoteStatus.RESPONDIDA;
            this.answerType = "MANUAL";
            this.answeredAt = LocalDateTime.now();
        }
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getAnswerType() {
        return answerType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getAnsweredAt() {
        return answeredAt;
    }
}
