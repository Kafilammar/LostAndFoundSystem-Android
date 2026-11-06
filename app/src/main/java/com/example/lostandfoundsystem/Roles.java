package com.example.lostandfoundsystem;

import java.util.Locale;

public final class Roles {
    private Roles() {}

    private static String norm(String role) {
        return role == null ? "" : role.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isAdmin(String role) {
        String r = norm(role);
        return r.equals("admin") || r.equals("administrator") || r.equals("مسؤول النظام");
    }

    public static boolean isCenter(String role) {
        String r = norm(role);
        return r.equals("center") || r.equals("lost_found_center") || r.equals("مركز المفقودات");
    }

    public static boolean isStaff(String role) {
        return isAdmin(role) || isCenter(role);
    }

    public static String label(String role) {
        if (isAdmin(role)) return "مسؤول النظام";
        if (isCenter(role)) return "موظف مركز المفقودات";
        return "طالب / منسوب";
    }
}
