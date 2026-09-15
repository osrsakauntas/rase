package com.kodari.raceborder.model;

import java.util.Locale;

public enum Profession {
    FARMER("Žemdirbys"),
    MERCHANT("Prekeivis"),
    BLACKSMITH("Amatininkas"),
    ARCHITECT("Mūrininkas"),
    FISHERMAN("Žūklys"),
    ENCHANTER("Žynys"),
    BAKER("Keporius");

    private final String displayName;

    Profession(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static Profession parse(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ZVEJYS", "FISHERMAN" -> FISHERMAN;
            case "ZUKLYS" -> FISHERMAN;
            case "UKININKAS", "ZEMDIRBYS", "FARMER" -> FARMER;
            case "PIRKLYS", "PREKEIVIS", "MERCHANT" -> MERCHANT;
            case "KALVIS", "AMATININKAS", "BLACKSMITH" -> BLACKSMITH;
            case "BUREJAS", "ZYNYS", "ENCHANTER" -> ENCHANTER;
            case "ARCHITEKTAS", "MURININKAS", "ARCHITECT" -> ARCHITECT;
            case "KEPEJAS", "KEPORIUS", "BAKER" -> BAKER;
            default -> null;
        };
    }
}