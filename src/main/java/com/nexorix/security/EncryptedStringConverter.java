package com.nexorix.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Cifra el campo al guardarlo y lo descifra al leerlo. Se usa con @Convert en la entidad. */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return FieldCipher.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return FieldCipher.decrypt(dbData);
    }
}
