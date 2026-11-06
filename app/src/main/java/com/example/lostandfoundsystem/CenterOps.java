package com.example.lostandfoundsystem;

import android.app.Activity;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.Map;

/** عمليات موظف مركز المفقودات (مع تحديث البلاغ المفقود المطابق في نفس الوقت). */
public final class CenterOps {
    private CenterOps() {}

    public interface Done { void onDone(); }

    /** الإجراء المتاح لبلاغ موجود حسب حالته، أو null. */
    public static ReportAdapter.Action actionFor(Activity a, Report r, Done done) {
        if (r.lost) return null;
        if (Status.isMatched(r.status) || Status.APPROVED.equals(r.status)) {
            return new ReportAdapter.Action("تأكيد استلامه في المركز", R.color.orange,
                    rep -> markAtCenter(a, rep, done));
        }
        if (Status.AT_CENTER.equals(r.status) && r.hasActiveMatch()) {
            return new ReportAdapter.Action("التحقق والتسليم لصاحبه", R.color.found_green,
                    rep -> verifyAndDeliver(a, rep, done));
        }
        // غرض في المركز بلا مطابقة: ينتظر صاحبه ولا يحتاج إجراء
        return null;
    }

    /**
     * التحقق من الملكية عند حضور صاحب البلاغ:
     * مطابق ← يُسلَّم ويُغلق البلاغان.
     * غير مطابق ← لا يُسلَّم، يبقى الغرض في المركز متاحًا لغيره، ويعود البلاغ المفقود للاقتراح التالي.
     */
    public static void verifyAndDeliver(Activity a, Report r, Done done) {
        new MaterialAlertDialogBuilder(a)
                .setTitle("التحقق من ملكية الغرض")
                .setMessage("الغرض: " + r.displayName() + " (" + r.code() + ")\n\n"
                        + "قبل التسليم تأكد من:\n"
                        + "1. البطاقة الجامعية لصاحب البلاغ ورمز البلاغ.\n"
                        + "2. دليل الملكية: فتح الجهاز بكلمة المرور، أو ذكر علامة مميزة لا تظهر في الصورة، أو الرقم التسلسلي.\n\n"
                        + "هل ثبت أن الغرض لصاحب البلاغ؟")
                .setPositiveButton("نعم، تسليم", (d, w) -> markReturned(a, r, done))
                .setNeutralButton("ليس له", (d, w) -> askFailReason(a, r, done))
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private static void askFailReason(Activity a, Report r, Done done) {
        final String[] reasons = {
                "لم يستطع فتح الجهاز أو إثبات ملكيته",
                "الوصف والعلامات المميزة لا تنطبق",
                "صاحب البلاغ أفاد أن الغرض ليس له"
        };
        new MaterialAlertDialogBuilder(a)
                .setTitle("سبب عدم المطابقة")
                .setItems(reasons, (d, which) -> MatchOps.unlink(r.matchedWithId, r.id, true, reasons[which], true, ok -> {
                    Toast.makeText(a, ok
                            ? "لم يُسلَّم الغرض. بقي في المركز متاحًا لغيره، وأُعيد البلاغ المفقود للاقتراح التالي."
                            : "تعذر حفظ النتيجة. تحقق من الاتصال والصلاحيات.", Toast.LENGTH_LONG).show();
                    if (ok && done != null) done.onDone();
                }))
                .setNegativeButton("رجوع", null)
                .show();
    }

    public static void markAtCenter(Activity a, Report r, Done done) {
        new MaterialAlertDialogBuilder(a)
                .setTitle("تأكيد الاستلام")
                .setMessage("هل استلم المركز \"" + r.displayName() + "\" (" + r.code() + ")؟\nسيتم إشعار صاحب الغرض بأنه جاهز للاستلام.")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("تأكيد", (d, w) -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("status", Status.AT_CENTER);
                    data.put("centerReceivedAt", FieldValue.serverTimestamp());
                    data.put("updatedAt", FieldValue.serverTimestamp());
                    commit(a, r, data, "تم تسجيل استلام الغرض في المركز", done);
                })
                .show();
    }

    public static void markReturned(Activity a, Report r, Done done) {
        new MaterialAlertDialogBuilder(a)
                .setTitle("تأكيد التسليم")
                .setMessage("هل تم تسليم \"" + r.displayName() + "\" لصاحبه بعد التحقق؟ سيتم إغلاق البلاغين.")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("تم التسليم", (d, w) -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("status", Status.RETURNED);
                    data.put("returnedAt", FieldValue.serverTimestamp());
                    data.put("updatedAt", FieldValue.serverTimestamp());
                    commit(a, r, data, "تم إغلاق العملية بنجاح", done);
                })
                .show();
    }

    private static void commit(Activity a, Report r, Map<String, Object> data, String ok, Done done) {
        WriteBatch batch = Db.get().batch();
        batch.update(Db.get().collection(r.collection).document(r.id), data);
        if (!BaseActivity.isBlank(r.matchedWithId)) {
            String other = r.lost ? Db.FOUND : Db.LOST;
            batch.update(Db.get().collection(other).document(r.matchedWithId), data);
        }
        batch.commit()
                .addOnSuccessListener(x -> {
                    Toast.makeText(a, ok, Toast.LENGTH_SHORT).show();
                    if (done != null) done.onDone();
                })
                .addOnFailureListener(e -> Toast.makeText(a,
                        "تعذر تحديث الحالة. تحقق من الاتصال والصلاحيات.", Toast.LENGTH_LONG).show());
    }
}
