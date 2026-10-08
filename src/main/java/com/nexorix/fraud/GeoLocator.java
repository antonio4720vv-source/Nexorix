package com.nexorix.fraud;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

/**
 * Convierte lo que manda el banco (coordenadas, o ciudad / pais del comercio o de la IP)
 * en un {@link Geo}. En la demo usa un catalogo corto; en produccion se cambia por el
 * geocodificador del agregador o por un servicio de IP (MaxMind, ipinfo...).
 */
@Component
public class GeoLocator {

    private static final Map<String, double[]> CITIES = Map.ofEntries(
            Map.entry("bogota", new double[]{4.7110, -74.0721}),
            Map.entry("medellin", new double[]{6.2442, -75.5812}),
            Map.entry("cali", new double[]{3.4516, -76.5320}),
            Map.entry("barranquilla", new double[]{10.9685, -74.7813}),
            Map.entry("cartagena", new double[]{10.3910, -75.4794}),
            Map.entry("miami", new double[]{25.7617, -80.1918}),
            Map.entry("orlando", new double[]{28.5383, -81.3792}),
            Map.entry("new york", new double[]{40.7128, -74.0060}),
            Map.entry("los angeles", new double[]{34.0522, -118.2437}),
            Map.entry("madrid", new double[]{40.4168, -3.7038}),
            Map.entry("ciudad de panama", new double[]{8.9824, -79.5199}),
            Map.entry("ciudad de mexico", new double[]{19.4326, -99.1332}));

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
        double[] point = city == null ? null : CITIES.get(normalize(city));
        if (point == null && land != null) {
            point = COUNTRIES.get(land);
        }
        return point == null ? null : new Geo(city, land, point[0], point[1]);
    }

    static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }
}
