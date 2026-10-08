package com.nexorix.importer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Clasificacion por reglas (Fase 2). Mira la descripcion del movimiento
 * y busca palabras de comercios y conceptos comunes en Colombia.
 *
 * Es deliberadamente simple y explicable: cada categoria sale de una
 * palabra concreta. En la Fase 3 la IA mejorara los casos dificiles.
 */
public final class TransactionClassifier {

    private TransactionClassifier() {
    }

    public static final String OTHER_EXPENSE = "OTROS_GASTOS";
    public static final String OTHER_INCOME = "OTROS_INGRESOS";

    /** Nombre bonito de cada categoria, en el orden en que se muestran. */
    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put("SALARIO", "Salario");
        LABELS.put("TRANSFERENCIA", "Transferencias");
        LABELS.put("MERCADO", "Mercado");
        LABELS.put("RESTAURANTES", "Restaurantes y domicilios");
        LABELS.put("TRANSPORTE", "Transporte");
        LABELS.put("SERVICIOS", "Servicios públicos y telefonía");
        LABELS.put("VIVIENDA", "Arriendo y vivienda");
        LABELS.put("SALUD", "Salud");
        LABELS.put("EDUCACION", "Educación");
        LABELS.put("ENTRETENIMIENTO", "Entretenimiento y suscripciones");
        LABELS.put("COMPRAS", "Compras");
        LABELS.put("RETIROS", "Retiros en efectivo");
        LABELS.put("BANCOS", "Comisiones e impuestos bancarios");
        LABELS.put("IMPUESTOS", "Impuestos");
        LABELS.put(OTHER_INCOME, "Otros ingresos");
        LABELS.put(OTHER_EXPENSE, "Otros gastos");
    }

    private record Rule(String category, boolean income, boolean expense, List<String> words) {
    }

    /**
     * Las reglas se revisan en orden: la primera que coincide gana.
     * Por eso "comision transferencia" queda en BANCOS y no en TRANSFERENCIA.
     */
    private static final List<Rule> RULES = List.of(
            new Rule("BANCOS", false, true, List.of(
                    "cuota de manejo", "cuota manejo", "comision", "gmf", "4x1000", "4 x 1000",
                    "gravamen", "iva comision", "costo transaccion", "seguro deudor")),
            new Rule("SALARIO", true, false, List.of(
                    "nomina", "salario", "sueldo", "honorarios", "pago de nomina", "prima")),
            new Rule("IMPUESTOS", false, true, List.of(
                    "dian", "impuesto", "predial", "retencion", "industria y comercio")),
            new Rule("RETIROS", false, true, List.of(
                    "retiro", "cajero", "atm", "avance efectivo")),
            new Rule("TRANSFERENCIA", true, true, List.of(
                    "transferencia", "transf", "trans", "envio", "enviado", "enviaste", "recibido de",
                    "recibiste", "te enviaron", "traslado", "bre-b", "breb")),
            new Rule("MERCADO", false, true, List.of(
                    "exito", "carulla", "d1", "tiendas d1", "ara", "olimpica", "jumbo",
                    "makro", "surtimax", "isimo", "supermercado", "mercado",
                    "fruver", "pricesmart", "colsubsidio supermercado")),
            new Rule("RESTAURANTES", false, true, List.of(
                    "restaurante", "rappi", "ifood", "domicilio", "mcdonald", "burger",
                    "kfc", "frisby", "crepes", "el corral", "juan valdez", "starbucks",
                    "oma", "tostao", "pizza", "panaderia", "cafe", "comida")),
            new Rule("TRANSPORTE", false, true, List.of(
                    "uber", "didi", "cabify", "indriver", "taxi", "transmilenio", "tullave",
                    "sitp", "metro de medellin", "terpel", "primax", "texaco", "biomax",
                    "gasolina", "combustible", "peaje", "parqueadero", "avianca", "latam airlines")),
            new Rule("SERVICIOS", false, true, List.of(
                    "epm", "enel", "codensa", "vanti", "gas natural", "acueducto", "eaab",
                    "claro", "movistar", "tigo", "etb", "wom", "internet", "energia",
                    "servicios publicos", "celular", "triple a", "emcali")),
            new Rule("VIVIENDA", false, true, List.of(
                    "arriendo", "arrendamiento", "administracion", "inmobiliaria", "canon")),
            new Rule("SALUD", false, true, List.of(
                    "drogueria", "farmacia", "cruz verde", "farmatodo", "la rebaja",
                    "colsanitas", "sura", "eps", "clinica", "hospital", "odontolog",
                    "laboratorio", "medico", "optica")),
            new Rule("EDUCACION", false, true, List.of(
                    "universidad", "colegio", "matricula", "curso", "platzi", "udemy",
                    "coursera", "icetex", "libreria")),
            new Rule("ENTRETENIMIENTO", false, true, List.of(
                    "netflix", "spotify", "disney", "hbo", "max", "prime video", "youtube",
                    "cine", "cinecolombia", "cinemark", "procinal", "steam", "playstation",
                    "xbox", "apple com", "google play", "deezer",
                    "bwin", "wplay", "betplay", "rushbet", "codere", "zamba", "apuestas")),
            new Rule("COMPRAS", false, true, List.of(
                    "falabella", "amazon", "mercado libre", "mercadolibre", "alkosto",
                    "ktronix", "homecenter", "zara", "adidas", "nike", "flamingo",
                    "tienda", "almacen", "compra")),
            new Rule(OTHER_INCOME, true, false, List.of(
                    "intereses", "rendimientos", "reintegro", "devolucion", "reembolso",
                    "abono", "consignacion", "deposito"))
    );

    /** Devuelve el codigo de categoria para una descripcion y un tipo. */
    public static String classify(String description, String type) {

        boolean income = "INGRESO".equalsIgnoreCase(type);
        String normalized = StatementValues.normalizeForMatching(description);
        String text = " " + normalized + " ";

        // Bancolombia escribe las transferencias como "Para NOMBRE" y "De NOMBRE".
        if ((!income && normalized.startsWith("para "))
                || (income && (normalized.startsWith("de ") || normalized.startsWith("desde ")))) {
            return "TRANSFERENCIA";
        }

        for (Rule rule : RULES) {
            if ((income && !rule.income()) || (!income && !rule.expense())) {
                continue;
            }
            for (String word : rule.words()) {
                // Palabra completa: "ara" no debe coincidir con "para".
                if (text.contains(" " + word + " ")) {
                    return rule.category();
                }
            }
        }

        return income ? OTHER_INCOME : OTHER_EXPENSE;
    }

    public static String label(String category) {
        if (category == null) {
            return null;
        }
        return LABELS.getOrDefault(category, "Otros");
    }

    public static Map<String, String> labels() {
        return LABELS;
    }
}
