package com.nexorix.bank;

import java.util.List;

/**
 * Un banco o billetera real de Colombia.
 *
 * @param name    nombre con el que se guarda en las cuentas
 * @param wallet  true si es billetera digital (se sugiere el tipo BILLETERA)
 * @param aliases como aparece en las notificaciones del celular (en minusculas, sin tildes)
 */
public record Bank(String name, boolean wallet, List<String> aliases) {
}
