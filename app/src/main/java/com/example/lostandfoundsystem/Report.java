package com.example.lostandfoundsystem;

import android.content.Context;
import android.content.Intent;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** نموذج موحد لبلاغ مفقود أو موجود. */
public class Report {
    public String id = "";
    public String collection = Db.LOST;
    public boolean lost = true;
    public String userId = "";
    public String itemName = "";
    public String category = "";
    public String description = "";
    public String location = "";
    public String date = "";
    public String details = "";
    public String status = Status.PENDING;
    public String imageBase64 = "";
    public String matchedWithId = "";
    /** بلاغات استُبعدت المطابقة معها (رفضها المسؤول، أو فشل التحقق في المركز، أو أفاد صاحب البلاغ أنها ليست له). */
    public Set<String> rejectedIds = new LinkedHashSet<>();
    /** أغراض موجودة طالب بها صاحب البلاغ المفقود بنفسه (زر "هذا غرضي"). */
    public Set<String> claimFoundIds = new LinkedHashSet<>();
    /** صاحب البلاغ أفاد أن الغرض المطابق ليس له (بانتظار قرار المسؤول). */
    public String disputeReason = "";
    /** سبب آخر إلغاء لمطابقة هذا البلاغ (فشل التحقق في المركز، أو لم يكن الغرض لصاحب البلاغ). */
    public String unmatchReason = "";
    public long verifyFailCount;
    public long lastUnmatchedAt;
    public String rejectReason = "";
    public String appointmentDate = "";
    public String appointmentTime = "";
    public long createdAt;
    public long updatedAt;
    public long centerReceivedAt;
    public long returnedAt;
    public long deliveredAt;

    public static Report from(DocumentSnapshot d) {
        Report r = new Report();
        r.id = d.getId();
        r.collection = d.getReference().getParent().getId();
        r.lost = Db.LOST.equals(r.collection);
        r.userId = str(d, "userId");
        r.itemName = str(d, "itemName");
        r.category = str(d, "category");
        r.description = str(d, "description");
        r.location = str(d, "location");
        r.date = str(d, "date");
        r.details = str(d, "details");
        r.status = str(d, "status");
        if (r.status.isEmpty()) r.status = Status.PENDING;
        r.imageBase64 = str(d, "imageBase64");
        r.matchedWithId = str(d, "matchedWithId");
        r.rejectedIds = set(d, "rejectedMatchWithId");
        r.claimFoundIds = set(d, "claimFoundIds");
        r.disputeReason = str(d, "disputeReason");
        r.unmatchReason = str(d, "unmatchReason");
        Object fails = d.get("verifyFailCount");
        r.verifyFailCount = fails instanceof Number ? ((Number) fails).longValue() : 0;
        r.lastUnmatchedAt = time(d, "lastUnmatchedAt");
        r.rejectReason = str(d, "rejectReason");
        r.appointmentDate = str(d, "appointmentDate");
        r.appointmentTime = str(d, "appointmentTime");
        r.createdAt = time(d, "createdAt");
        r.updatedAt = time(d, "updatedAt");
        r.centerReceivedAt = time(d, "centerReceivedAt");
        r.returnedAt = time(d, "returnedAt");
        r.deliveredAt = time(d, "deliveredAt");
        return r;
    }

    private static String str(DocumentSnapshot d, String field) {
        Object v = d.get(field);
        return v == null ? "" : String.valueOf(v);
    }

    private static Set<String> set(DocumentSnapshot d, String field) {
        Set<String> out = new LinkedHashSet<>();
        Object v = d.get(field);
        if (v instanceof List) {
            for (Object o : (List<?>) v) if (o != null) out.add(String.valueOf(o));
        } else if (v instanceof String && !((String) v).isEmpty()) {
            out.add((String) v);
        }
        return out;
    }

    private static long time(DocumentSnapshot d, String field) {
        Object v = d.get(field);
        if (v instanceof Timestamp) return ((Timestamp) v).toDate().getTime();
        if (v instanceof java.util.Date) return ((java.util.Date) v).getTime();
        if (v instanceof Number) return ((Number) v).longValue();
        return 0L;
    }

    /** آخر وقت حدث فيه تغيير على البلاغ. */
    public long lastActivity() {
        return Math.max(createdAt, updatedAt);
    }

    /** البلاغ مربوط حاليًا بمطابقة معتمدة. */
    public boolean hasActiveMatch() {
        return matchedWithId != null && !matchedWithId.trim().isEmpty();
    }

    /** بيانات المطابقة (الوصف يشمل التفاصيل الإضافية). */
    public MatchScore.Input matchInput() {
        String desc = (description == null ? "" : description) + " " + (details == null ? "" : details);
        return new MatchScore.Input(itemName, category, location, date, desc.trim());
    }

    public String displayName() {
        return itemName == null || itemName.trim().isEmpty() ? "بلاغ بدون اسم" : itemName;
    }

    public String code() {
        return codeFor(id);
    }

    public static String codeFor(String id) {
        String tail = id == null ? "" : id.replaceAll("[^A-Za-z0-9]", "");
        if (tail.isEmpty()) tail = "000000";
        if (tail.length() > 6) tail = tail.substring(tail.length() - 6);
        return "LF-" + tail.toUpperCase(Locale.ROOT);
    }

    public int placeholder() {
        return lost ? R.drawable.ic_icon_lost : R.drawable.ic_icon_found;
    }

    /** فتح شاشة التفاصيل (نمرر المعرّف فقط، والشاشة تحمّل أحدث بيانات من Firestore). */
    public static void openDetails(Context context, Report r) {
        Intent i = new Intent(context, ItemDetailsActivity.class);
        i.putExtra(ItemDetailsActivity.EXTRA_ID, r.id);
        i.putExtra(ItemDetailsActivity.EXTRA_COLLECTION, r.collection);
        context.startActivity(i);
    }

    public static void openAdminReview(Context context, Report r) {
        Intent i = new Intent(context, AdminReportReviewActivity.class);
        i.putExtra("documentId", r.id);
        i.putExtra("collection", r.collection);
        context.startActivity(i);
    }

    public boolean matches(String q) {
        return contains(itemName, q) || contains(category, q) || contains(description, q)
                || contains(location, q) || contains(details, q) || contains(code(), q);
    }

    private static boolean contains(String v, String q) {
        return v != null && TextNorm.normalize(v).contains(q);
    }
}
