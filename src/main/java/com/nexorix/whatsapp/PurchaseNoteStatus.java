package com.nexorix.whatsapp;

public enum PurchaseNoteStatus {
    /** Creada, todavia sin mandar la pregunta. */
    PENDIENTE,
    /** La pregunta ya salio por WhatsApp; esperando respuesta. */
    PREGUNTADA,
    /** La persona respondio y se llenaron las columnas. */
    RESPONDIDA,
    /** No se pudo mandar la pregunta o entender la respuesta. */
    ERROR
}
