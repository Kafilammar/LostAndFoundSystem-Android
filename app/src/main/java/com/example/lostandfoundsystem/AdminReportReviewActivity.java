package com.example.lostandfoundsystem;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.firestore.FieldValue;

import java.util.HashMap;
import java.util.Map;

/** مراجعة المسؤول لبلاغ: بدء التدقيق، الاعتماد، الرفض مع السبب، أو الحذف. */
public class AdminReportReviewActivity extends BaseActivity {

    private String docId, collection;
    private Report report;
    private Report matched;
    private View progress, content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_report_review);

        docId = getIntent().getStringExtra("documentId");
        collection = getIntent().getStringExtra("collection");
        if (isBlank(docId)) {
            toast("البلاغ غير موجود");
            finish();
            return;
        }
        if (isBlank(collection)) collection = Db.LOST;

        bindBack(R.id.btnBackAdminReview);
        progress = findViewById(R.id.adminReviewProgress);
        content = findViewById(R.id.adminReviewContent);

        findViewById(R.id.btnStartReview).setOnClickListener(v -> updateStatus(Status.UNDER_REVIEW, null, "بدأ التدقيق في البلاغ"));
        findViewById(R.id.btnApproveReport).setOnClickListener(v ->
                new MaterialAlertDialogBuilder(this)
                        .setTitle("اعتماد البلاغ")
                        .setMessage("سيظهر البلاغ في البحث ويدخل ضمن المطابقات المقترحة.")
                        .setNegativeButton("إلغاء", null)
                        .setPositiveButton("اعتماد", (d, w) -> updateStatus(Status.APPROVED, null, "تم اعتماد البلاغ"))
                        .show());
        findViewById(R.id.btnRejectReport).setOnClickListener(v -> askRejectReason());
        findViewById(R.id.btnAdminDelete).setOnClickListener(v -> confirmDelete());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isBlank(docId)) load();
    }

    private void load() {
        if (report == null) progress.setVisibility(View.VISIBLE);
        Db.get().collection(collection).document(docId).get()
                .addOnSuccessListener(doc -> {
                    if (!isAlive()) return;
                    if (!doc.exists()) {
                        toast("هذا البلاغ لم يعد موجودًا");
                        finish();
                        return;
                    }
                    report = Report.from(doc);
                    bind();
                    loadReporter();
                })
                .addOnFailureListener(e -> {
                    if (!isAlive()) return;
                    progress.setVisibility(View.GONE);
                    toast(getString(R.string.no_connection));
                    if (report == null) finish();
                });
    }

    private void bind() {
        Report r = report;
        progress.setVisibility(View.GONE);
        content.setVisibility(View.VISIBLE);

        ImageBase64Utils.loadInto(findViewById(R.id.ivAdminReview), r.imageBase64, r.placeholder());
        Status.applyTypeChip(findViewById(R.id.tvAdminReviewType), r.lost);
        Status.applyChip(findViewById(R.id.tvAdminReviewStatus), r.status);
        ((TextView) findViewById(R.id.tvAdminReviewName)).setText(r.displayName());
        ((TextView) findViewById(R.id.tvAdminReviewCode)).setText("رمز البلاغ: " + r.code());

        setInfoRow(R.id.reviewRowCategory, R.drawable.ic_category, "الفئة", r.category);
        setInfoRow(R.id.reviewRowLocation, R.drawable.ic_location, r.lost ? "مكان الفقدان" : "مكان العثور", r.location);
        setInfoRow(R.id.reviewRowDate, R.drawable.ic_calendar, r.lost ? "تاريخ الفقدان" : "تاريخ العثور", r.date);
        boolean hasDetails = !isBlank(r.details);
        findViewById(R.id.reviewRowDetails).setVisibility(hasDetails ? View.VISIBLE : View.GONE);
        findViewById(R.id.reviewRowDetailsDivider).setVisibility(hasDetails ? View.VISIBLE : View.GONE);
        if (hasDetails) setInfoRow(R.id.reviewRowDetails, R.drawable.ic_info, "تفاصيل إضافية", r.details);
        ((TextView) findViewById(R.id.tvAdminReviewDescription)).setText(isBlank(r.description) ? "لا يوجد وصف" : r.description);

        boolean rejected = Status.REJECTED.equals(r.status);
        findViewById(R.id.rejectNoticeBox).setVisibility(rejected ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.tvRejectReason)).setText(
                "تم رفض البلاغ." + (isBlank(r.rejectReason) ? "" : "\nالسبب: " + r.rejectReason));

        boolean inReview = Status.isInReview(r.status);
        boolean decided = Status.APPROVED.equals(r.status);
        findViewById(R.id.btnStartReview).setVisibility(Status.PENDING.equals(r.status) ? View.VISIBLE : View.GONE);
        findViewById(R.id.btnApproveReport).setVisibility(inReview || rejected ? View.VISIBLE : View.GONE);
        findViewById(R.id.btnRejectReport).setVisibility(inReview || decided ? View.VISIBLE : View.GONE);
        findViewById(R.id.adminReviewActions).setVisibility(inReview || decided || rejected ? View.VISIBLE : View.GONE);
        bindMatchInfo();
    }

    /** المطابقة الحالية للبلاغ، أو آخر مطابقة أُلغيت. */
    private void bindMatchInfo() {
        Report r = report;
        View box = findViewById(R.id.matchInfoBox);
        TextView info = findViewById(R.id.tvMatchInfo);
        View buttons = (View) findViewById(R.id.btnUnlinkMatch).getParent();
        matched = null;

        if (r.hasActiveMatch() && !Status.RETURNED.equals(r.status)) {
            box.setVisibility(View.VISIBLE);
            buttons.setVisibility(View.VISIBLE);
            info.setText("البلاغ مرتبط حاليًا بمطابقة معتمدة…");
            String other = r.lost ? Db.FOUND : Db.LOST;
            Db.get().collection(other).document(r.matchedWithId).get().addOnSuccessListener(d -> {
                if (!isAlive() || !d.exists()) return;
                matched = Report.from(d);
                Report lostR = r.lost ? r : matched, foundR = r.lost ? matched : r;
                MatchScore.Result res = Matcher.evaluate(lostR, foundR);
                StringBuilder sb = new StringBuilder();
                sb.append("مرتبط بمطابقة مع: ").append(matched.displayName()).append(" (").append(matched.code()).append(")")
                        .append("\nنسبة التطابق: ").append(res.score).append("% — ").append(res.levelLabel())
                        .append("\nالحالة: ").append(Status.label(foundR.status));
                if (!isBlank(lostR.disputeReason)) {
                    sb.append("\n\nأفاد صاحب البلاغ أن الغرض ليس له: ").append(lostR.disputeReason)
                            .append("\nعند إلغاء المطابقة يعود البلاغ للبحث ويظهر له الاقتراح التالي.");
                }
                info.setText(sb);
            });
            findViewById(R.id.btnOpenMatchedItem).setOnClickListener(v -> {
                if (matched != null) Report.openDetails(this, matched);
            });
            findViewById(R.id.btnUnlinkMatch).setOnClickListener(v -> askUnlink());
        } else if (!isBlank(r.unmatchReason)) {
            box.setVisibility(View.VISIBLE);
            buttons.setVisibility(View.GONE);
            String txt = "أُلغيت مطابقة سابقة لهذا البلاغ: " + r.unmatchReason;
            if (r.verifyFailCount > 0) txt += "\nعدد مرات فشل التحقق في المركز: " + r.verifyFailCount;
            info.setText(txt);
        } else {
            box.setVisibility(View.GONE);
        }
    }

    private void askUnlink() {
        if (matched == null) return;
        final Report lostR = report.lost ? report : matched;
        final Report foundR = report.lost ? matched : report;
        TextInputLayout til = new TextInputLayout(this);
        til.setHint("سبب إلغاء المطابقة");
        TextInputEditText et = new TextInputEditText(til.getContext());
        et.setText(isBlank(lostR.disputeReason) ? "" : lostR.disputeReason);
        til.addView(et);
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(dp(22), dp(8), dp(22), 0);
        wrap.addView(til);
        new MaterialAlertDialogBuilder(this)
                .setTitle("إلغاء المطابقة")
                .setMessage("سيعود البلاغ المفقود للبحث ويظهر له الاقتراح التالي، ويعود الغرض متاحًا لغيره. لن يُقترح هذا الزوج مرة أخرى.")
                .setView(wrap)
                .setNegativeButton("تراجع", null)
                .setPositiveButton("إلغاء المطابقة", (d, w) -> {
                    String reason = et.getText() == null ? "" : et.getText().toString().trim();
                    if (reason.isEmpty()) reason = "الغرض ليس لصاحب البلاغ";
                    boolean atCenter = Status.AT_CENTER.equals(foundR.status);
                    MatchOps.unlink(lostR.id, foundR.id, atCenter, reason, false, ok -> {
                        if (!isAlive()) return;
                        toast(ok ? "تم إلغاء المطابقة، وأُعيد البلاغ لقائمة الاقتراحات" : "تعذر إلغاء المطابقة");
                        if (ok) load();
                    });
                })
                .show();
    }

    private void loadReporter() {
        if (isBlank(report.userId)) return;
        Db.get().collection(Db.USERS).document(report.userId).get().addOnSuccessListener(u -> {
            if (!isAlive()) return;
            String name = u.getString("name");
            String uni = u.getString("universityId");
            String phone = u.getString("phone");
            if (isBlank(uni)) uni = Db.universityIdFromEmail(u.getString("email"));
            ((TextView) findViewById(R.id.tvReporterName)).setText(isBlank(name) ? "مستخدم بدون اسم" : name);
            ((TextView) findViewById(R.id.tvReporterAvatar)).setText(initials(isBlank(name) ? uni : name));
            String line = isBlank(uni) ? "" : "الرقم الجامعي: " + uni;
            if (!isBlank(phone)) line += (line.isEmpty() ? "" : "\n") + "الجوال: " + phone;
            ((TextView) findViewById(R.id.tvReporterId)).setText(line);
            View call = findViewById(R.id.btnCallReporter);
            call.setVisibility(isBlank(phone) ? View.GONE : View.VISIBLE);
            call.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone)));
                } catch (Exception e) {
                    copyToClipboard(phone);
                }
            });
        });
    }

    private void askRejectReason() {
        TextInputLayout til = new TextInputLayout(this);
        til.setHint("سبب الرفض (يظهر لصاحب البلاغ)");
        TextInputEditText et = new TextInputEditText(til.getContext());
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        et.setMinLines(2);
        til.addView(et);
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(dp(22), dp(8), dp(22), 0);
        wrap.addView(til);

        new MaterialAlertDialogBuilder(this)
                .setTitle("رفض البلاغ")
                .setView(wrap)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("رفض", (d, w) -> {
                    String reason = et.getText() == null ? "" : et.getText().toString().trim();
                    if (reason.isEmpty()) reason = "البيانات غير مكتملة أو غير واضحة";
                    updateStatus(Status.REJECTED, reason, "تم رفض البلاغ");
                })
                .show();
    }

    private void updateStatus(String status, String rejectReason, String okMessage) {
        Map<String, Object> data = new HashMap<>();
        data.put("status", status);
        data.put("updatedAt", FieldValue.serverTimestamp());
        data.put("reviewedBy", uidOrEmpty());
        data.put("rejectReason", rejectReason == null ? FieldValue.delete() : rejectReason);
        Db.get().collection(collection).document(docId).update(data)
                .addOnSuccessListener(x -> {
                    if (!isAlive()) return;
                    toast(okMessage);
                    load();
                })
                .addOnFailureListener(e -> toast("تعذر تحديث الحالة. تحقق من الاتصال والصلاحيات."));
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("حذف البلاغ نهائيًا")
                .setMessage("سيتم حذف البلاغ من النظام بالكامل ولا يمكن استرجاعه.")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حذف", (d, w) ->
                        Db.get().collection(collection).document(docId).delete()
                                .addOnSuccessListener(x -> {
                                    toast("تم حذف البلاغ");
                                    finish();
                                })
                                .addOnFailureListener(e -> toast("تعذر الحذف")))
                .show();
    }
}
