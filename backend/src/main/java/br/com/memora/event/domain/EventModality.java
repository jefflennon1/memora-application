package br.com.memora.event.domain;

/** Como o evento acontece. Cidade e endereço só se aplicam a eventos presenciais. */
public enum EventModality {

    IN_PERSON,
    ONLINE,
    NO_LOCATION
}
