package ru.bestaford.bstorage.model;

public record File(String id, String fileName, Type type) {

    public enum Type {
        PHOTO,
        VIDEO,
        DOCUMENT,
        AUDIO,
        GIF,
        STICKER
    }
}