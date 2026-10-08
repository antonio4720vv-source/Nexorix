package com.nexorix.importer;

/**
 * Error al importar un extracto. code permite a la pagina reaccionar,
 * por ejemplo pidiendo la contrasena del PDF.
 */
public class ImportException extends RuntimeException {

    public static final String PASSWORD_REQUIRED = "PASSWORD_REQUIRED";
    public static final String PASSWORD_INVALID = "PASSWORD_INVALID";
    public static final String INVALID_FILE = "INVALID_FILE";
    public static final String ALREADY_IMPORTED = "ALREADY_IMPORTED";
    public static final String NOT_FOUND = "NOT_FOUND";

    private final String code;

    public ImportException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
