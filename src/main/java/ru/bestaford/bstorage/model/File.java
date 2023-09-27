package ru.bestaford.bstorage.model;

public record File(String id, String title, Type type) {

    public enum Type {
        PHOTO,
        VIDEO,
        DOCUMENT,
        AUDIO
    }
}