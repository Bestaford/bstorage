package ru.bestaford.bstorage;

public record TelegramFile(String id, Type type) {

    public enum Type {
        PHOTO,
        VIDEO
    }
}