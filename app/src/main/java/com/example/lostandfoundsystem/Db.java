package com.example.lostandfoundsystem;

import com.google.firebase.firestore.FirebaseFirestore;

/** أسماء مجموعات Firestore وبيانات المركز الثابتة. */
public final class Db {
    public static final String LOST = "lost_items";
    public static final String FOUND = "found_items";
    public static final String USERS = "users";

    public static final String EMAIL_DOMAIN = "@ksu.edu.sa";

    public static final String CENTER_NAME = "مركز الأمان للمفقودات - المبنى الإداري";
    public static final String CENTER_LOCATION = "الدور الأرضي، المكتب رقم 94، بجوار البوابة الشرقية";
    public static final String CENTER_HOURS = "من الأحد إلى الخميس: 08:00 ص - 04:00 م";

    public static final String OTHER = "أخرى";

    public static final String[] CATEGORIES = {
            "إلكترونيات", "وثائق وبطاقات", "مقتنيات شخصية", "مفاتيح", "حقائب", "ملابس", OTHER
    };

    public static final String[] LOCATIONS = {
            "المبنى الرئيسي - القاعة الكبرى", "المكتبة المركزية", "المدينة الجامعية",
            "البوابة الرئيسية", "مواقف السيارات", OTHER
    };

    private Db() {}

    public static FirebaseFirestore get() {
        return FirebaseFirestore.getInstance();
    }

    public static String universityIdFromEmail(String email) {
        if (email == null) return "";
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }
}
