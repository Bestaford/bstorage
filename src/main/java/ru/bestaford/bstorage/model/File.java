package ru.bestaford.bstorage.model;

public record File(String id, Type type) {

    public enum Type {
        PHOTO,
        VIDEO
    }
}