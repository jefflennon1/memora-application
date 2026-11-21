package br.com.memora.shared.storage;

/** Imagem privada já autorizada, pronta para entrega HTTP. */
public record StoredImage(String storageKey, String contentType, ObjectStorage.StoredObject object) {
}
