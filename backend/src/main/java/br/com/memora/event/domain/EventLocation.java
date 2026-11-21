package br.com.memora.event.domain;

import br.com.memora.shared.error.ValidationException;

/**
 * Onde o evento acontece. Cidade e endereço só existem em eventos presenciais: para eventos online ou
 * sem local eles são descartados.
 */
public record EventLocation(EventModality modality, String city, String address) {

    public static final int CITY_MAX = 120;
    public static final int ADDRESS_MAX = 300;

    public EventLocation {
        if (modality == null) {
            throw new ValidationException("EVENT_MODALITY_REQUIRED",
                    "Informe a modalidade do evento (IN_PERSON, ONLINE ou NO_LOCATION).");
        }
        if (modality == EventModality.IN_PERSON) {
            city = clean(city, CITY_MAX, "cidade");
            address = clean(address, ADDRESS_MAX, "endereço");
        } else {
            city = null;
            address = null;
        }
    }

    private static String clean(String value, int max, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String stripped = value.strip();
        if (stripped.length() > max) {
            throw new ValidationException("INVALID_EVENT_LOCATION",
                    "O campo " + label + " aceita no máximo " + max + " caracteres.");
        }
        return stripped;
    }
}
