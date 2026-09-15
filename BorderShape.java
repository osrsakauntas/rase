package com.kodari.raceborder.model;

public enum BorderShape {
    SQUARE,
    CIRCLE;

    public static BorderShape parse(String value) {
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException exception) {
            return SQUARE;
        }
    }
}