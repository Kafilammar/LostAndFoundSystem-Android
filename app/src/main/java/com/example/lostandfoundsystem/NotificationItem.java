package com.example.lostandfoundsystem;

import android.content.Context;
import android.content.SharedPreferences;

/** إشعار مبني على حالة البلاغ. */
public class NotificationItem {
    public String title;
    public String body;
    public long time;
    public int icon;
    public int tint;
    public int circle;
    public Report report;

    private static NotificationItem make(Report r, String title, String body, int icon, int tint, int circle) {
        NotificationItem n = new NotificationItem();
        n.report = r; n.title = title; n.body = body; n.icon = icon; n.tint = tint; n.circle = circle;
        n.time = r == null ? 0 : r.lastActivity();
        return n;
    }

    /** إشعارات صاحب البلاغ. */
    public static NotificationItem forOwner(Report r) {
        String label = "\"" + r.displayName() + "\"";
        String s = r.status;
        if (r.lost && r.hasActiveMatch() && !Status.RETURNED.equals(s) && r.disputeReason != null && !r.disputeReason.isEmpty()) {
            return make(r, "وصلت ملاحظتك للإدارة",
                    "أفدت أن الغرض المطابق لبلاغ " + label + " ليس لك. ستُعرض عليك البدائل بعد المراجعة.",
                    R.drawable.ic_info, R.color.ksu_blue, R.drawable.bg_icon_circle);
        }
        if (Status.APPROVED.equals(s) && r.unmatchReason != null && !r.unmatchReason.isEmpty()) {
            return make(r, r.lost ? "لم تتأكد المطابقة" : "أُلغيت المطابقة",
                    r.lost ? "الغرض السابق لم يكن لك (" + r.unmatchReason + "). بلاغ " + label + " ما زال نشطًا، واطّلع على الاقتراحات الأخرى."
                           : "الغرض " + label + " لم يكن لصاحب ذلك البلاغ، ويبقى بلاغك نشطًا حتى يظهر صاحبه.",
                    R.drawable.ic_refresh, R.color.warning_text, R.drawable.bg_circle_yellow_soft);
        }
        if (Status.isMatched(s)) {
            return make(r, "تم العثور على تطابق",
                    r.lost ? "تمت مطابقة بلاغك " + label + " مع غرض موجود. سيصلك إشعار عند وصوله للمركز."
                           : "تمت مطابقة " + label + " مع صاحبه. يرجى تسليمه لمركز المفقودات.",
                    R.drawable.ic_compare, R.color.found_green, R.drawable.bg_circle_green_soft);
        }
        if (Status.AT_CENTER.equals(s)) {
            return make(r, r.lost ? "غرضك متوفر في المركز" : "تم استلام الغرض في المركز",
                    r.lost ? label + " بانتظارك في " + Db.CENTER_NAME + ". أحضر بطاقتك الجامعية."
                           : "شكرًا لك! تم تسجيل وصول " + label + " إلى مركز المفقودات.",
                    R.drawable.ic_center, R.color.orange, R.drawable.bg_circle_orange_soft);
        }
        if (Status.RETURNED.equals(s)) {
            return make(r, "تم التسليم بنجاح", "اكتملت عملية " + label + " وتم تسليمه لصاحبه.",
                    R.drawable.ic_check_circle, R.color.found_green, R.drawable.bg_circle_green_soft);
        }
        if (Status.REJECTED.equals(s)) {
            String reason = r.rejectReason == null || r.rejectReason.isEmpty() ? "" : " السبب: " + r.rejectReason;
            return make(r, "تم رفض البلاغ", "لم يتم اعتماد بلاغ " + label + "." + reason,
                    R.drawable.ic_warning, R.color.danger_text, R.drawable.bg_circle_red_soft);
        }
        if (Status.APPROVED.equals(s)) {
            return make(r, "تم اعتماد البلاغ", "اعتمدت الإدارة بلاغ " + label + " وجاري البحث عن تطابق.",
                    R.drawable.ic_check, R.color.ksu_blue, R.drawable.bg_icon_circle);
        }
        if (Status.UNDER_REVIEW.equals(s)) {
            return make(r, "بلاغك قيد التدقيق", "بدأت الإدارة مراجعة بلاغ " + label + ".",
                    R.drawable.ic_refresh, R.color.ksu_blue, R.drawable.bg_icon_circle);
        }
        return make(r, "تم استلام بلاغك", "تم استلام بلاغ " + label + " وسيتم مراجعته من الإدارة.",
                R.drawable.ic_reports, R.color.warning_text, R.drawable.bg_circle_yellow_soft);
    }

    /** إشعارات الموظفين (الإدارة والمركز). */
    public static NotificationItem forStaff(Report r) {
        String label = "\"" + r.displayName() + "\"";
        String kind = r.lost ? "مفقود" : "موجود";
        String s = r.status;
        if (r.lost && r.hasActiveMatch() && r.disputeReason != null && !r.disputeReason.isEmpty()) {
            return make(r, "إفادة بعدم المطابقة",
                    "صاحب بلاغ " + label + " أفاد أن الغرض المطابق ليس له: " + r.disputeReason,
                    R.drawable.ic_warning, R.color.orange, R.drawable.bg_circle_orange_soft);
        }
        if (r.lost && r.claimFoundIds != null && !r.claimFoundIds.isEmpty() && Status.APPROVED.equals(s)) {
            return make(r, "طلب مطابقة من صاحب البلاغ",
                    "صاحب بلاغ " + label + " يطالب بغرض من قائمة الاقتراحات.",
                    R.drawable.ic_hand_heart, R.color.found_green, R.drawable.bg_circle_green_soft);
        }
        if (Status.PENDING.equals(s)) {
            return make(r, "بلاغ " + kind + " جديد", "بلاغ جديد بانتظار المراجعة: " + label,
                    R.drawable.ic_reports, R.color.warning_text, R.drawable.bg_circle_yellow_soft);
        }
        if (Status.UNDER_REVIEW.equals(s)) {
            return make(r, "بلاغ قيد التدقيق", label + " ما زال في مرحلة التدقيق.",
                    R.drawable.ic_refresh, R.color.ksu_blue, R.drawable.bg_icon_circle);
        }
        if (Status.isMatched(s) || Status.APPROVED.equals(s)) {
            return make(r, "بلاغ جاهز للمتابعة", "تم تحديث حالة " + label + " إلى: " + Status.label(s),
                    R.drawable.ic_compare, R.color.found_green, R.drawable.bg_circle_green_soft);
        }
        if (Status.AT_CENTER.equals(s)) {
            return make(r, "غرض في المركز", label + " مسجل حاليًا في مركز المفقودات.",
                    R.drawable.ic_center, R.color.orange, R.drawable.bg_circle_orange_soft);
        }
        if (Status.RETURNED.equals(s)) {
            return make(r, "تم تسجيل التسليم", "أُغلقت عملية " + label + " بنجاح.",
                    R.drawable.ic_check_circle, R.color.found_green, R.drawable.bg_circle_green_soft);
        }
        return make(r, "تحديث على بلاغ", "تم تحديث حالة " + label + ".",
                R.drawable.ic_info, R.color.ksu_blue, R.drawable.bg_icon_circle);
    }

    // ---------- حالة القراءة (محفوظة لكل مستخدم على الجهاز) ----------
    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("notifications", Context.MODE_PRIVATE);
    }

    public static long readAt(Context c, String uid) {
        return prefs(c).getLong("readAt_" + uid, 0L);
    }

    public static void markAllRead(Context c, String uid) {
        prefs(c).edit().putLong("readAt_" + uid, System.currentTimeMillis()).apply();
    }
}
