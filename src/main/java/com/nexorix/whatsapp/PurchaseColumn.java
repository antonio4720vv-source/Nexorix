package com.nexorix.whatsapp;

import com.nexorix.user.User;
import jakarta.persistence.*;

/**
 * Una columna de la tabla personalizada de compras.
 *
 * Cada persona decide que quiere guardar de sus compras (producto, tienda,
 * para quien era, garantia...). La IA llena cada columna con lo que la
 * persona dijo en su nota de voz, usando la descripcion como guia.
 */
@Entity
@Table(name = "purchase_columns",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "column_key"}))
public class PurchaseColumn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Identificador estable (ej. "producto"): es la llave en los valores guardados. */
    @Column(name = "column_key", nullable = false, length = 40)
    private String key;

    /** Nombre visible (ej. "Producto"). */
    @Column(nullable = false, length = 40)
    private String label;

    /** Que debe poner la IA aqui (ej. "Que se compro, con marca si la dice"). */
    @Column(length = 200)
    private String hint;

    @Column(nullable = false)
    private int position;

    protected PurchaseColumn() {
    }

    public PurchaseColumn(User user, String key, String label, String hint, int position) {
        this.user = user;
        this.key = key;
        this.label = label;
        this.hint = hint;
        this.position = position;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getHint() {
        return hint;
    }

    public void setHint(String hint) {
        this.hint = hint;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }
}
