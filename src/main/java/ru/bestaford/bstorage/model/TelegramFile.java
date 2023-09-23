package ru.bestaford.bstorage.model;

public record TelegramFile(String id, Type type) {

    public enum Type {
        PHOTO,
        VIDEO
    }
}