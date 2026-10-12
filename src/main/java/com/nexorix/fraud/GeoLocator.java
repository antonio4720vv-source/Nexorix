package com.nexorix.fraud;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Convierte lo que manda el banco (coordenadas, o ciudad / pais del comercio o de la IP)
 * en un {@link Geo}, y encuentra la ciudad dentro del texto de una notificacion
 * ("Compra por $50.000 en TIENDA BOGOTA"). Usa un catalogo de ciudades de Colombia y de los
 * destinos mas comunes en el exterior; si el agregador manda coordenadas exactas se usan esas.
 *
 * Ciudades que tambien son palabras, apellidos o nombres de comercios (Roma, Lima, Paris, Santiago,
 * Barcelona, San Jose, Pasto, Bello, Armenia, Chia...) NO estan: una compra normal no debe parecer un viaje imposible.
 */
@Component
public class GeoLocator {

    private record City(String name, String country, double latitude, double longitude) {
    }

    /** Clave normalizada (sin tildes, minusculas) -> ciudad. */
    private static final Map<String, City> CITIES = new LinkedHashMap<>();

    private static void add(String name, String country, double lat, double lon, String... aliases) {
        City city = new City(name, country, lat, lon);
        CITIES.put(normalize(name), city);
        for (String alias : aliases) {
            CITIES.put(normalize(alias), city);
        }
    }

    static {
        // Colombia
        add("Bogotá", "CO", 4.711, -74.072, "bogota dc", "bogota d c");
        add("Medellín", "CO", 6.244, -75.581);
        add("Cali", "CO", 3.452, -76.532);
        add("Barranquilla", "CO", 10.969, -74.781);
        add("Cartagena", "CO", 10.391, -75.479, "cartagena de indias");
        add("Bucaramanga", "CO", 7.119, -73.123);
        add("Cúcuta", "CO", 7.894, -72.504);
        add("Pereira", "CO", 4.813, -75.696);
        add("Santa Marta", "CO", 11.241, -74.199);
        add("Manizales", "CO", 5.070, -75.514);
        add("Ibagué", "CO", 4.438, -75.232);
        add("Villavicencio", "CO", 4.142, -73.627);
        add("Montería", "CO", 8.748, -75.881);
        add("Neiva", "CO", 2.927, -75.282);
        add("Popayán", "CO", 2.444, -76.614);
        add("Valledupar", "CO", 10.477, -73.253);
        add("Tunja", "CO", 5.535, -73.367);
        add("Sincelejo", "CO", 9.304, -75.398);
        add("Riohacha", "CO", 11.544, -72.907);
        add("Yopal", "CO", 5.337, -72.395);
        add("San Andrés", "CO", 12.584, -81.700);
        add("Soacha", "CO", 4.579, -74.217);
        add("Envigado", "CO", 6.171, -75.591);
        add("Itagüí", "CO", 6.172, -75.612);
        add("Zipaquirá", "CO", 5.022, -74.004);
        add("Buenaventura", "CO", 3.883, -77.031);
        add("Barrancabermeja", "CO", 7.065, -73.855);
        // Exterior
        add("Miami", "US", 25.762, -80.192);
        add("Orlando", "US", 28.538, -81.379);
        add("New York", "US", 40.713, -74.006, "nueva york");
        add("Los Angeles", "US", 34.052, -118.244);
        add("Houston", "US", 29.760, -95.370);
        add("Atlanta", "US", 33.749, -84.388);
        add("Chicago", "US", 41.878, -87.630);
        add("Las Vegas", "US", 36.170, -115.140);
        add("San Francisco", "US", 37.775, -122.419);
        add("Madrid", "ES", 40.417, -3.704);
        add("Londres", "GB", 51.507, -0.128, "london");
        add("Ámsterdam", "NL", 52.368, 4.904);
        add("Lisboa", "PT", 38.722, -9.139);
        add("Dubái", "AE", 25.205, 55.271, "dubai");
        add("Estambul", "TR", 41.008, 28.978);
        add("Ciudad de Panamá", "PA", 8.982, -79.520, "panama", "panama city");
        add("Ciudad de México", "MX", 19.433, -99.133, "mexico df", "cdmx");
        add("Cancún", "MX", 21.161, -86.851);
        add("Quito", "EC", -0.180, -78.468);
        add("Guayaquil", "EC", -2.171, -79.922);
        add("Buenos Aires", "AR", -34.604, -58.382);
        add("São Paulo", "BR", -23.551, -46.633, "sao paulo");
        add("Río de Janeiro", "BR", -22.907, -43.173, "rio de janeiro");
        add("Caracas", "VE", 10.481, -66.904);
        add("Punta Cana", "DO", 18.582, -68.405);
        add("Santo Domingo", "DO", 18.486, -69.931);
        add("La Habana", "CU", 23.113, -82.366);
        add("Toronto", "CA", 43.653, -79.383);
        add("Tokio", "JP", 35.676, 139.650, "tokyo");
        add("Singapur", "SG", 1.352, 103.820, "singapore");
        add("Hong Kong", "HK", 22.320, 114.170);
        add("Sídney", "AU", -33.869, 151.209, "sydney");
    }

    /** Del mas largo al mas corto, para que "Santa Marta" gane sobre un posible "Marta". */
    private static final List<String> BY_LENGTH = new ArrayList<>(CITIES.keySet());

    static {
        BY_LENGTH.sort(Comparator.comparingInt(String::length).reversed());
    }

    private static final Map<String, double[]> COUNTRIES = Map.of(
            "CO", new double[]{4.5709, -74.2973},
            "US", new double[]{39.8283, -98.5795},
            "ES", new double[]{40.4637, -3.7492},
            "MX", new double[]{23.6345, -102.5528},
            "PA", new double[]{8.5380, -80.7821});

    /** Coordenadas explicitas > ciudad conocida > centro del pais > null (sin ubicacion). */
    public Geo resolve(String city, String country, Double latitude, Double longitude) {
        String land = country == null ? null : country.trim().toUpperCase(Locale.ROOT);
        if (latitude != null && longitude != null && Math.abs(latitude) <= 90 && Math.abs(longitude) <= 180) {
            return new Geo(city, land, latitude, longitude);
        }
        City known = city == null ? null : CITIES.get(normalize(city));
        if (known != null) {
            return new Geo(city, land != null ? land : known.country(), known.latitude(), known.longitude());
        }
        double[] point = land == null ? null : COUNTRIES.get(land);
        return point == null ? null : new Geo(city, land, point[0], point[1]);
    }

    /** La ciudad (de las que conocemos) que se nombra dentro de un texto, o vacio. */
    public Optional<Geo> findInText(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String haystack = " " + normalize(text) + " ";
        for (String key : BY_LENGTH) {
            if (haystack.contains(" " + key + " ")) {
                City city = CITIES.get(key);
                return Optional.of(new Geo(city.name(), city.country(), city.latitude(), city.longitude()));
            }
        }
        return Optional.empty();
    }

    static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }
}
