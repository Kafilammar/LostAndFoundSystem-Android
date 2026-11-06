package com.example.lostandfoundsystem;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.Map;

/**
 * كل التغييرات على المطابقة في مكان واحد:
 * الاعتماد، الإلغاء (مع إعادة البلاغين للبحث)، المطالبة بغرض، الإفادة بأن الغرض ليس لصاحب البلاغ.
 */
public final class MatchOps {
    private MatchOps() {}

    public interface Done { void onDone(boolean ok); }

    /**
     * اعتماد المطابقة: يُربط البلاغان، وتبقى بقية الاقتراحات محفوظة كبدائل.
     * إذا كان الغرض موجودًا في المركز أصلًا تصبح حالة البلاغين "في المركز" مباشرة.
     */
    public static void approve(Report lost, Report found, int score, Done done) {
        String status = Status.AT_CENTER.equals(found.status) ? Status.AT_CENTER : Status.MATCHED;

        Map<String, Object> l = new HashMap<>();
        l.put("status", status);
        l.put("matchedWithId", found.id);
        l.put("matchScore", score);
        l.put("matchedAt", FieldValue.serverTimestamp());
        l.put("updatedAt", FieldValue.serverTimestamp());
        l.put("disputeReason", FieldValue.delete());
        l.put("disputeAt", FieldValue.delete());

        Map<String, Object> f = new HashMap<>();
        f.put("status", status);
        f.put("matchedWithId", lost.id);
        f.put("matchScore", score);
        f.put("matchedAt", FieldValue.serverTimestamp());
        f.put("updatedAt", FieldValue.serverTimestamp());

        WriteBatch b = Db.get().batch();
        b.update(Db.get().collection(Db.LOST).document(lost.id), l);
        b.update(Db.get().collection(Db.FOUND).document(found.id), f);
        commit(b, done);
    }

    /**
     * إلغاء مطابقة معتمدة: البلاغ المفقود يعود "معتمد" ليظهر له الاقتراح التالي،
     * والغرض يعود متاحًا لغيره (ويبقى "في المركز" إذا كان قد سُلّم للمركز).
     * يُستبعد هذا الزوج نهائيًا حتى لا يُقترح مرة أخرى.
     *
     * @param verifyFailure true إذا كان الإلغاء بسبب فشل التحقق في المركز.
     */
    public static void unlink(String lostId, String foundId, boolean foundAtCenter,
                              String reason, boolean verifyFailure, Done done) {
        Map<String, Object> l = new HashMap<>();
        l.put("status", Status.APPROVED);
        l.put("matchedWithId", FieldValue.delete());
        l.put("rejectedMatchWithId", FieldValue.arrayUnion(foundId));
        l.put("claimFoundIds", FieldValue.arrayRemove(foundId));
        l.put("disputeReason", FieldValue.delete());
        l.put("disputeAt", FieldValue.delete());
        l.put("unmatchReason", reason);
        l.put("lastUnmatchedAt", FieldValue.serverTimestamp());
        l.put("updatedAt", FieldValue.serverTimestamp());
        if (verifyFailure) l.put("verifyFailCount", FieldValue.increment(1));

        Map<String, Object> f = new HashMap<>();
        f.put("status", foundAtCenter ? Status.AT_CENTER : Status.APPROVED);
        f.put("matchedWithId", FieldValue.delete());
        f.put("rejectedMatchWithId", FieldValue.arrayUnion(lostId));
        f.put("unmatchReason", reason);
        f.put("lastUnmatchedAt", FieldValue.serverTimestamp());
        f.put("updatedAt", FieldValue.serverTimestamp());
        if (!foundAtCenter) {
            f.put("appointmentDate", FieldValue.delete());
            f.put("appointmentTime", FieldValue.delete());
        }
        if (verifyFailure) f.put("verifyFailCount", FieldValue.increment(1));

        WriteBatch b = Db.get().batch();
        b.update(Db.get().collection(Db.LOST).document(lostId), l);
        b.update(Db.get().collection(Db.FOUND).document(foundId), f);
        commit(b, done);
    }

    /** استبعاد اقتراح لم يُعتمد بعد (المسؤول ضغط "غير مطابق"). */
    public static void rejectSuggestion(String lostId, String foundId, Done done) {
        WriteBatch b = Db.get().batch();
        Map<String, Object> l = new HashMap<>();
        l.put("rejectedMatchWithId", FieldValue.arrayUnion(foundId));
        l.put("claimFoundIds", FieldValue.arrayRemove(foundId));
        b.update(Db.get().collection(Db.LOST).document(lostId), l);
        b.update(Db.get().collection(Db.FOUND).document(foundId), "rejectedMatchWithId", FieldValue.arrayUnion(lostId));
        commit(b, done);
    }

    /** صاحب البلاغ المفقود يطالب بغرض من قائمة الاقتراحات (يصل طلبه للمسؤول). */
    public static void claim(Report lost, String foundId, boolean add, Done done) {
        Map<String, Object> l = new HashMap<>();
        l.put("claimFoundIds", add ? FieldValue.arrayUnion(foundId) : FieldValue.arrayRemove(foundId));
        l.put("updatedAt", FieldValue.serverTimestamp());
        Db.get().collection(Db.LOST).document(lost.id).update(l)
                .addOnSuccessListener(x -> { if (done != null) done.onDone(true); })
                .addOnFailureListener(e -> { if (done != null) done.onDone(false); });
    }

    /** صاحب البلاغ يفيد بأن الغرض المطابق ليس له (من الصورة أو الوصف)، فيراجع المسؤول ويعرض البديل. */
    public static void dispute(Report lost, String reason, Done done) {
        Map<String, Object> l = new HashMap<>();
        l.put("disputeReason", reason);
        l.put("disputeAt", FieldValue.serverTimestamp());
        l.put("updatedAt", FieldValue.serverTimestamp());
        Db.get().collection(Db.LOST).document(lost.id).update(l)
                .addOnSuccessListener(x -> { if (done != null) done.onDone(true); })
                .addOnFailureListener(e -> { if (done != null) done.onDone(false); });
    }

    private static void commit(WriteBatch b, Done done) {
        b.commit()
                .addOnSuccessListener(x -> { if (done != null) done.onDone(true); })
                .addOnFailureListener(e -> { if (done != null) done.onDone(false); });
    }
}
