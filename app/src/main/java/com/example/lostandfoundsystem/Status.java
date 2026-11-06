package com.example.lostandfoundsystem;

import android.widget.TextView;

import androidx.core.content.ContextCompat;

/** كل ما يخص حالات البلاغ: النص العربي والألوان ومجموعات الحالات. */
public final class Status {
    public static final String PENDING = "Pending";
    public static final String UNDER_REVIEW = "Under Review";
    public static final String APPROVED = "Approved";
    public static final String REJECTED = "Rejected";
    public static final String MATCHED = "Matched";
    public static final String VERIFIED = "Verified";
    public static final String AT_CENTER = "At Center";
    public static final String RETURNED = "Returned";

    private Status() {}

    public static String label(String s) {
        if (s == null || s.trim().isEmpty()) return "قيد المراجعة";
        switch (s) {
            case PENDING: return "قيد المراجعة";
            case UNDER_REVIEW: return "قيد التدقيق";
            case APPROVED: return "تم الاعتماد";
            case MATCHED: return "تمت المطابقة";
            case VERIFIED: return "تم التحقق";
            case AT_CENTER: return "في مركز المفقودات";
            case RETURNED: return "تم التسليم";
            case REJECTED: return "مرفوض";
            default: return s;
        }
    }

    public static boolean isInReview(String s) {
        return s == null || s.isEmpty() || PENDING.equals(s) || UNDER_REVIEW.equals(s);
    }

    public static boolean isMatched(String s) {
        return MATCHED.equals(s) || VERIFIED.equals(s);
    }

    public static boolean isCompleted(String s) {
        return AT_CENTER.equals(s) || RETURNED.equals(s);
    }

    public static int textColor(String s) {
        if (s == null || s.isEmpty() || PENDING.equals(s)) return R.color.warning_text;
        switch (s) {
            case UNDER_REVIEW:
            case APPROVED: return R.color.ksu_blue;
            case MATCHED:
            case VERIFIED:
            case RETURNED: return R.color.found_green;
            case AT_CENTER: return R.color.orange;
            case REJECTED: return R.color.danger_text;
            default: return R.color.text_secondary;
        }
    }

    public static int background(String s) {
        if (s == null || s.isEmpty() || PENDING.equals(s)) return R.drawable.bg_chip_yellow;
        switch (s) {
            case UNDER_REVIEW:
            case APPROVED: return R.drawable.bg_chip_blue;
            case MATCHED:
            case VERIFIED:
            case RETURNED: return R.drawable.bg_chip_green;
            case AT_CENTER: return R.drawable.bg_chip_orange;
            case REJECTED: return R.drawable.bg_chip_red;
            default: return R.drawable.bg_chip_gray;
        }
    }

    public static void applyChip(TextView chip, String status) {
        chip.setText(label(status));
        chip.setTextColor(ContextCompat.getColor(chip.getContext(), textColor(status)));
        chip.setBackgroundResource(background(status));
    }

    public static void applyTypeChip(TextView chip, boolean lost) {
        chip.setText(lost ? "مفقود" : "موجود");
        chip.setTextColor(ContextCompat.getColor(chip.getContext(), lost ? R.color.selection_red : R.color.found_green));
        chip.setBackgroundResource(lost ? R.drawable.bg_chip_red : R.drawable.bg_chip_green);
    }
}
