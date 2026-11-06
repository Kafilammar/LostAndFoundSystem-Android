package com.example.lostandfoundsystem;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** تفاصيل بلاغ واحد (يحمّل أحدث نسخة من Firestore في كل مرة). */
public class ItemDetailsActivity extends BaseActivity {

    public static final String EXTRA_ID = "documentId";
    public static final String EXTRA_COLLECTION = "collection";

    private String docId, collection;
    private Report report;
    private String role = "";

    private View progress, content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_item_details);

        docId = getIntent().getStringExtra(EXTRA_ID);
        collection = getIntent().getStringExtra(EXTRA_COLLECTION);
        if (isBlank(docId)) {
            toast("البلاغ غير موجود");
            finish();
            return;
        }
        if (isBlank(collection)) collection = Db.LOST;

        bindBack(R.id.btnBackDetails);
        progress = findViewById(R.id.detailsProgress);
        content = findViewById(R.id.detailsContent);

        findViewById(R.id.btnCopyCode).setOnClickListener(v -> {
            if (report != null) copyToClipboard(report.code());
        });

        // دور المستخدم يحدد الأزرار الظاهرة
        String uid = uidOrEmpty();
        if (!uid.isEmpty()) {
            Db.get().collection(Db.USERS).document(uid).get().addOnSuccessListener(d -> {
                role = d.getString("role") == null ? "" : d.getString("role");
                if (report != null && isAlive()) bindActions();
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isBlank(docId)) load();
    }

    private void load() {
        if (report == null) {
            progress.setVisibility(View.VISIBLE);
            content.setVisibility(View.GONE);
        }
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
                })
                .addOnFailureListener(e -> {
                    if (!isAlive()) return;
                    progress.setVisibility(View.GONE);
                    toast(getString(R.string.no_connection));
                    if (report == null) finish();
                });
    }

    private boolean isOwner() {
        return report != null && report.userId.equals(uidOrEmpty());
    }

    private void bind() {
        Report r = report;
        progress.setVisibility(View.GONE);
        content.setVisibility(View.VISIBLE);

        ((TextView) findViewById(R.id.tvDetailsTitle)).setText(r.lost ? "تفاصيل بلاغ مفقود" : "تفاصيل بلاغ موجود");
        ImageBase64Utils.loadInto(findViewById(R.id.ivItemDetails), r.imageBase64, r.placeholder());
        Status.applyTypeChip(findViewById(R.id.tvDetailsType), r.lost);
        Status.applyChip(findViewById(R.id.tvStatus), r.status);
        ((TextView) findViewById(R.id.tvItemName)).setText(r.displayName());
        ((TextView) findViewById(R.id.tvReportCode)).setText("رمز البلاغ: " + r.code());

        setInfoRow(R.id.rowCategory, R.drawable.ic_category, "الفئة", r.category);
        setInfoRow(R.id.rowLocation, R.drawable.ic_location, r.lost ? "مكان الفقدان" : "مكان العثور", r.location);
        setInfoRow(R.id.rowDate, R.drawable.ic_calendar, r.lost ? "تاريخ الفقدان" : "تاريخ العثور", r.date);
        boolean hasDetails = !isBlank(r.details);
        findViewById(R.id.rowDetails).setVisibility(hasDetails ? View.VISIBLE : View.GONE);
        findViewById(R.id.rowDetailsDivider).setVisibility(hasDetails ? View.VISIBLE : View.GONE);
        if (hasDetails) setInfoRow(R.id.rowDetails, R.drawable.ic_info, "تفاصيل إضافية", r.details);

        ((TextView) findViewById(R.id.tvDescription)).setText(isBlank(r.description) ? "لا يوجد وصف" : r.description);

        bindNotice();
        bindActions();
    }

    private void bindNotice() {
        Report r = report;
        boolean owner = isOwner();
        String s = r.status;
        String text;
        int kind; // 0 أزرق، 1 أصفر، 2 أخضر، 3 أحمر
        int icon;

        if (Status.REJECTED.equals(s)) {
            text = "تم رفض هذا البلاغ." + (isBlank(r.rejectReason) ? "" : "\nالسبب: " + r.rejectReason);
            kind = 3; icon = R.drawable.ic_warning;
        } else if (Status.RETURNED.equals(s)) {
            text = r.lost ? "تم تسليم الغرض لصاحبه بنجاح. نتمنى لك يومًا سعيدًا!" : "اكتملت العملية وتم تسليم الغرض لصاحبه. شكرًا لأمانتك!";
            kind = 2; icon = R.drawable.ic_check_circle;
        } else if (Status.AT_CENTER.equals(s)) {
            if (owner && r.lost && !isBlank(r.disputeReason)) {
                text = "أرسلت للإدارة أن الغرض المطابق ليس لك. ستُراجع الإدارة طلبك وتعرض لك البدائل.";
            } else if (owner && r.lost) {
                text = "الغرض المطابق لبلاغك متوفر الآن في " + Db.CENTER_NAME + "\n" + Db.CENTER_LOCATION + "\n" + Db.CENTER_HOURS
                        + "\nأحضر بطاقتك الجامعية ورمز البلاغ، ودليلًا على الملكية (مثل فتح الجهاز بكلمة المرور أو ذكر علامة مميزة)."
                        + "\nإذا لم يكن الغرض لك فلن يُسلَّم، وستظهر لك الاقتراحات الأخرى.";
            } else if (owner && !r.hasActiveMatch()) {
                text = "الغرض في مركز المفقودات بانتظار صاحبه. شكرًا لأمانتك!";
            } else {
                text = "الغرض موجود حاليًا في مركز المفقودات.";
            }
            kind = 1; icon = R.drawable.ic_center;
        } else if (Status.isMatched(s)) {
            if (owner && r.lost && !isBlank(r.disputeReason)) {
                text = "أرسلت للإدارة أن الغرض المطابق ليس لك. ستُراجع الإدارة طلبك وتعرض لك البدائل.";
            } else if (owner && r.lost) {
                text = "أخبار سارة! تمت مطابقة بلاغك مع غرض موجود. سيصلك إشعار عند وصوله إلى مركز المفقودات.";
            } else if (owner) {
                text = isBlank(r.appointmentDate)
                        ? "تمت مطابقة الغرض مع صاحبه. يرجى تحديد موعد لتسليمه إلى مركز المفقودات."
                        : "موعد التسليم للمركز: " + r.appointmentDate + (isBlank(r.appointmentTime) ? "" : " - " + r.appointmentTime);
            } else {
                text = "تمت مطابقة هذا البلاغ.";
            }
            kind = 2; icon = R.drawable.ic_compare;
        } else if (Status.APPROVED.equals(s)) {
            if (owner && !isBlank(r.unmatchReason)) {
                text = r.lost
                        ? "لم تتأكد المطابقة السابقة (" + r.unmatchReason + "). بلاغك ما زال نشطًا، واطّلع على الأغراض المقترحة بالأسفل."
                        : "أُلغيت المطابقة السابقة لأن الغرض لم يكن لصاحب ذلك البلاغ. يبقى بلاغك نشطًا حتى يظهر صاحبه.";
                kind = 1; icon = R.drawable.ic_info;
            } else {
                text = owner
                        ? (r.lost ? "تم اعتماد بلاغك. راجع الأغراض المقترحة بالأسفل، وإن وجدت غرضك اضغط (هذا غرضي)." : "تم اعتماد بلاغك من الإدارة، وجاري البحث عن صاحب الغرض.")
                        : "بلاغ معتمد من الإدارة.";
                kind = 0; icon = R.drawable.ic_check;
            }
        } else if (Status.UNDER_REVIEW.equals(s)) {
            text = "البلاغ قيد التدقيق من الإدارة.";
            kind = 0; icon = R.drawable.ic_refresh;
        } else {
            text = owner ? "تم استلام بلاغك وهو بانتظار مراجعة الإدارة. يمكنك حذفه قبل بدء المراجعة."
                         : "البلاغ بانتظار مراجعة الإدارة.";
            kind = 1; icon = R.drawable.ic_clock;
        }

        // طالب يشاهد بلاغ غيره
        if (!owner && !Roles.isStaff(role) && !Status.isCompleted(s) && !Status.REJECTED.equals(s)) {
            text = r.lost
                    ? "إذا وجدت هذا الغرض، أبلغ عنه من خيار \"وجدت شيئًا\" أو سلّمه مباشرة لمركز المفقودات."
                    : "هل هذا غرضك؟ أنشئ بلاغ مفقود بنفس المواصفات، أو راجع مركز المفقودات مع رمز البلاغ " + r.code() + ".";
            kind = 0; icon = R.drawable.ic_info;
        }

        int bg, color;
        switch (kind) {
            case 1: bg = R.drawable.bg_notice_yellow; color = R.color.warning_text; break;
            case 2: bg = R.drawable.bg_notice_green; color = R.color.found_green; break;
            case 3: bg = R.drawable.bg_notice_red; color = R.color.danger_text; break;
            default: bg = R.drawable.bg_notice_blue; color = R.color.ksu_blue; break;
        }
        View box = findViewById(R.id.noticeBox);
        box.setVisibility(View.VISIBLE);
        box.setBackgroundResource(bg);
        ImageView iv = findViewById(R.id.noticeIcon);
        iv.setImageResource(icon);
        ImageViewCompat.setImageTintList(iv, ColorStateList.valueOf(ContextCompat.getColor(this, color)));
        TextView tv = findViewById(R.id.tvNotice);
        tv.setText(text);
        tv.setTextColor(ContextCompat.getColor(this, color));
    }

    private void bindActions() {
        Report r = report;
        boolean owner = isOwner();

        MaterialButton delivery = findViewById(R.id.btnDelivery);
        boolean canDeliver = owner && !r.lost && Status.isMatched(r.status);
        delivery.setVisibility(canDeliver ? View.VISIBLE : View.GONE);
        delivery.setText(isBlank(r.appointmentDate) ? "تحديد موعد التسليم للمركز" : "تعديل موعد التسليم");
        delivery.setOnClickListener(v -> {
            Intent i = new Intent(this, DeliveryConfirmationActivity.class);
            i.putExtra(DeliveryConfirmationActivity.EXTRA_ID, r.id);
            startActivity(i);
        });

        MaterialButton admin = findViewById(R.id.btnAdminReview);
        admin.setVisibility(Roles.isAdmin(role) ? View.VISIBLE : View.GONE);
        admin.setOnClickListener(v -> Report.openAdminReview(this, r));

        MaterialButton notMine = findViewById(R.id.btnNotMine);
        boolean canDispute = owner && r.lost && r.hasActiveMatch() && isBlank(r.disputeReason)
                && (Status.isMatched(r.status) || Status.AT_CENTER.equals(r.status));
        notMine.setVisibility(canDispute ? View.VISIBLE : View.GONE);
        notMine.setOnClickListener(v -> askDispute());

        boolean showCandidates = owner && Matcher.lostOpen(r);
        findViewById(R.id.candidatesSection).setVisibility(showCandidates ? View.VISIBLE : View.GONE);
        if (showCandidates) loadCandidates();

        MaterialButton delete = findViewById(R.id.btnDeleteReport);
        boolean canDelete = owner && Status.PENDING.equals(r.status);
        delete.setVisibility(canDelete ? View.VISIBLE : View.GONE);
        delete.setOnClickListener(v -> confirmDelete());
    }

    // ---------------- اقتراحات صاحب البلاغ المفقود ----------------

    private int candidatesRequest = 0;

    private void loadCandidates() {
        final int req = ++candidatesRequest;
        final Report lost = report;
        findViewById(R.id.candidatesProgress).setVisibility(View.VISIBLE);
        Db.get().collection(Db.FOUND)
                .whereIn("status", java.util.Arrays.asList(Status.APPROVED, Status.MATCHED, Status.AT_CENTER))
                .limit(300)
                .get()
                .addOnSuccessListener(snap -> {
                    if (req != candidatesRequest || !isAlive()) return;
                    java.util.List<Report> founds = new java.util.ArrayList<>();
                    for (com.google.firebase.firestore.QueryDocumentSnapshot d : snap) founds.add(Report.from(d));
                    renderCandidates(lost, Matcher.candidatesForLost(lost, founds, true));
                })
                .addOnFailureListener(e -> {
                    if (req != candidatesRequest || !isAlive()) return;
                    findViewById(R.id.candidatesProgress).setVisibility(View.GONE);
                    toast(getString(R.string.no_connection));
                });
    }

    private void renderCandidates(Report lost, java.util.List<Matcher.Pair> pairs) {
        findViewById(R.id.candidatesProgress).setVisibility(View.GONE);
        android.widget.LinearLayout box = findViewById(R.id.candidatesContainer);
        box.removeAllViews();
        int claims = 0;
        for (Matcher.Pair p : pairs) if (p.claimed && Matcher.foundOpen(p.found)) claims++;
        final int claimCount = claims;

        for (int i = 0; i < Math.min(5, pairs.size()); i++) {
            Matcher.Pair p = pairs.get(i);
            View card = ReportCard.inflate(box);
            ReportCard.bind(card, p.found, false);
            boolean locked = Matcher.foundLocked(p.found);
            String line = p.score + "% — " + p.result.levelLabel() + "  •  " + p.found.code();
            if (locked) line = "قيد التحقق مع صاحب بلاغ آخر، ويعود لك إن لم يكن له  •  " + p.score + "%";
            ((TextView) card.findViewById(R.id.reportDate)).setText(line);
            card.setOnClickListener(v -> Report.openDetails(this, p.found));
            if (!locked) {
                if (p.claimed) {
                    ReportCard.setAction(card, "تم إرسال طلبك للإدارة (إلغاء)", R.color.text_secondary,
                            v -> MatchOps.claim(lost, p.found.id, false, ok -> afterClaim(ok, "تم إلغاء الطلب")));
                } else {
                    ReportCard.setAction(card, "هذا غرضي", R.color.found_green, v -> {
                        if (claimCount >= Matcher.MAX_CLAIMS) {
                            toast("يمكنك المطالبة بـ " + Matcher.MAX_CLAIMS + " أغراض كحد أقصى في الوقت نفسه");
                            return;
                        }
                        confirmClaim(lost, p);
                    });
                }
            }
            box.addView(card);
        }
        findViewById(R.id.candidatesEmpty).setVisibility(pairs.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void confirmClaim(Report lost, Matcher.Pair p) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("هذا غرضي")
                .setMessage("سيصل طلبك للإدارة لمراجعته مع بلاغك. عند الموافقة يصلك إشعار للتوجه إلى مركز المفقودات.\n\n"
                        + "عند الاستلام يُطلب منك دليل على الملكية (مثل فتح الجهاز بكلمة المرور، أو ذكر علامة مميزة، أو الرقم التسلسلي).")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("إرسال الطلب", (d, w) ->
                        MatchOps.claim(lost, p.found.id, true, ok -> afterClaim(ok, "تم إرسال طلبك للإدارة")))
                .show();
    }

    private void afterClaim(boolean ok, String message) {
        if (!isAlive()) return;
        toast(ok ? message : "تعذر إرسال الطلب. تحقق من الاتصال.");
        if (ok) load();
    }

    private void askDispute() {
        com.google.android.material.textfield.TextInputLayout til = new com.google.android.material.textfield.TextInputLayout(this);
        til.setHint("لماذا ليس غرضك؟ (مثال: اللون مختلف)");
        com.google.android.material.textfield.TextInputEditText et = new com.google.android.material.textfield.TextInputEditText(til.getContext());
        til.addView(et);
        android.widget.FrameLayout wrap = new android.widget.FrameLayout(this);
        wrap.setPadding(dp(22), dp(8), dp(22), 0);
        wrap.addView(til);
        new MaterialAlertDialogBuilder(this)
                .setTitle("هذا ليس غرضي")
                .setMessage("سيصل بلاغك للإدارة لإلغاء هذه المطابقة وعرض البدائل الأخرى لك.")
                .setView(wrap)
                .setNegativeButton("تراجع", null)
                .setPositiveButton("إرسال", (d, w) -> {
                    String reason = et.getText() == null ? "" : et.getText().toString().trim();
                    if (reason.isEmpty()) reason = "الغرض لا يطابق غرضي";
                    MatchOps.dispute(report, reason, ok -> {
                        if (!isAlive()) return;
                        toast(ok ? "تم إرسال ملاحظتك للإدارة" : "تعذر الإرسال. تحقق من الاتصال.");
                        if (ok) load();
                    });
                })
                .show();
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("حذف البلاغ")
                .setMessage("هل أنت متأكد من حذف هذا البلاغ؟ لا يمكن التراجع عن هذا الإجراء.")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حذف", (d, w) ->
                        Db.get().collection(collection).document(docId).delete()
                                .addOnSuccessListener(x -> {
                                    toast("تم حذف البلاغ");
                                    finish();
                                })
                                .addOnFailureListener(e -> toast("تعذر حذف البلاغ. قد تكون المراجعة بدأت.")))
                .show();
    }
}
