package com.nexorix.fraud;

/** Un lugar con coordenadas. */
public record Geo(String city, String country, double latitude, double longitude) {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    /** Distancia sobre la esfera (haversine) en km. No necesita extensiones de PostgreSQL. */
    public double distanceKm(Geo other) {
        double lat1 = Math.toRadians(latitude);
        double lat2 = Math.toRadians(other.latitude);
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(other.longitude - longitude);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    public String label() {
        String place = city == null || city.isBlank() ? "" : city;
        String land = country == null || country.isBlank() ? "" : country;
        return place.isEmpty() ? land : land.isEmpty() ? place : place + ", " + land;
    }
}
