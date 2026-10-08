package com.nexorix.importer;

/** Estado resumido de un archivo subido (para la lista de la pagina). */
public record ImportStatus(
        Long batchId,
        String fileName,
        String status,
        int total,
        int newRows,
        int duplicates,
        int invalid,
        boolean aiUsed,
        String checkMessage,
        String errorMessage
) {

    public static ImportStatus of(ImportBatch batch) {
        return new ImportStatus(batch.getId(), batch.getFileName(), batch.getStatus(),
                batch.getTotalRows(), batch.getNewRows(), batch.getDuplicateRows(), batch.getInvalidRows(),
                batch.isAiUsed(), batch.getCheckMessage(), batch.getErrorMessage());
    }

    /** Archivo rechazado antes de crear su lote (repetido, vacio, formato no valido...). */
    public static ImportStatus rejected(String fileName, String message) {
        return new ImportStatus(null, fileName, ImportBatch.FAILED, 0, 0, 0, 0, false, null, message);
    }
}
