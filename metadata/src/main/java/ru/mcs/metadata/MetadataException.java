package ru.mcs.metadata;

/** Ошибка формата, загрузки или перекрёстной валидации metadata. */
public final class MetadataException extends RuntimeException {

    public MetadataException(String message) {
        super(message);
    }

    public MetadataException(String message, Throwable cause) {
        super(message, cause);
    }
}
