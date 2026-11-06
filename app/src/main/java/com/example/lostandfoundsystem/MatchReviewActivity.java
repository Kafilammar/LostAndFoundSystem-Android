package com.example.lostandfoundsystem;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.DocumentSnapshot;

/** مقارنة بلاغ مفقود مع بلاغ موجود واعتماد المطابقة أو رفضها. */
public class MatchReviewActivity extends BaseActivity {

    public static final String EXTRA_LOST_ID = "lostId";
    public static final String EXTRA_FOUND_ID = "foundId";
    public static final String EXTRA_SCORE = "score";
    public static final String EXTRA_RANK = "rank";
    public static final String EXTRA_TOTAL = "total";
    public static final String EXTRA_COMPETING = "competing";

    private String lostId, foundId;
    private Report lost, found;
    private boolean busy = false;
    private int score = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_match_review);

        lostId = getIntent().getStringExtra(EXTRA_LOST_ID);
        foundId = getIntent().getStringExtra(EXTRA_FOUND_ID);
        if (isBlank(lostId) || isBlank(foundId)) {
            finish();
            return;
        }
        bindBack(R.id.btnBackMatch);
        findViewById(R.id.btnApproveMatch).setOnClickListener(v -> confirmApprove());
        findViewById(R.id.btnRejectMatch).setOnClickListener(v -> confirmReject());
        findViewById(R.id.matchActions).setVisibility(View.GONE);
        load();
    }

    private void load() {
        Db.get().collection(Db.LOST).document(lostId).get()
                .continueWithTask(t -> {
                    DocumentSnapshot d = t.getResult();
                    if (d != null && d.exists()) lost = Report.from(d);
                    return Db.get().collection(Db.FOUND).document(foundId).get();
                })
                .addOnSuccessListener(d -> {
                    if (!isAlive()) return;
                    if (d.exists()) found = Report.from(d);
                    if (lost == null || found == null) {
                        toast("أحد البلاغين لم يعد موجودًا");
                        finish();
                        return;
                    }
                    bind();
                })
                .addOnFailureListener(e -> {
                    if (!isAlive()) return;
                    toast(getString(R.string.no_connection));
                    finish();
                });
    }

    private void bind() {
        findViewById(R.id.matchProgress).setVisibility(View.GONE);
        findViewById(R.id.matchContent).setVisibility(View.VISIBLE);

        // نعيد الحساب دائمًا من أحدث بيانات البلاغين
        MatchScore.Result result = Matcher.evaluate(lost, found);
        score = result.score;
        int level = result.level();
        int color = level == MatchScore.HIGH ? R.color.found_green
                : level == MatchScore.MEDIUM ? R.color.orange : R.color.danger_text;

        TextView tvScore = findViewById(R.id.tvMatchScore);
        tvScore.setText(score + "%");
        tvScore.setTextColor(getColor(color));
        ((ProgressBar) findViewById(R.id.matchScoreBar)).setProgress(score);
        ((TextView) findViewById(R.id.tvMatchStatus)).setText(result.levelLabel());
        ((TextView) findViewById(R.id.tvMatchBreakdown)).setText(result.explain());

        int rank = getIntent().getIntExtra(EXTRA_RANK, 1);
        int total = getIntent().getIntExtra(EXTRA_TOTAL, 1);
        int competing = getIntent().getIntExtra(EXTRA_COMPETING, 0);
        StringBuilder info = new StringBuilder();
        info.append("هذا الاقتراح رقم ").append(rank).append(" من ").append(total)
                .append(" لهذا البلاغ المفقود.");
        if (total > 1) info.append(" الاقتراحات الأخرى تبقى بدائل إذا لم يتطابق هذا الغرض عند التحقق.");
        if (competing > 0) {
            info.append("\nهذا الغرض مقترح أيضًا لـ ").append(competing)
                    .append(competing == 1 ? " بلاغ مفقود آخر" : " بلاغات مفقودة أخرى")
                    .append("، ويُحسم صاحبه بالتحقق في المركز.");
        }
        if (lost.verifyFailCount > 0) {
            info.append("\nسبق أن فشل التحقق لهذا البلاغ ").append(lost.verifyFailCount).append(" مرة.");
        }
        ((TextView) findViewById(R.id.tvMatchRank)).setText(info);
        findViewById(R.id.tvMatchClaimed).setVisibility(lost.claimFoundIds.contains(found.id) ? View.VISIBLE : View.GONE);

        side(lost, R.id.ivMatchLost, R.id.tvMatchLostName, R.id.tvMatchLostCategory, R.id.tvMatchLostLocation,
                R.id.tvMatchLostDate, R.id.tvMatchLostDescription, R.id.tvMatchLostCode);
        side(found, R.id.ivMatchFound, R.id.tvMatchFoundName, R.id.tvMatchFoundCategory, R.id.tvMatchFoundLocation,
                R.id.tvMatchFoundDate, R.id.tvMatchFoundDescription, R.id.tvMatchFoundCode);

        boolean available = Matcher.lostOpen(lost) && Matcher.foundOpen(found) && !Matcher.excluded(lost, found);
        findViewById(R.id.matchActions).setVisibility(available ? View.VISIBLE : View.GONE);
        if (!available) toast("لا يمكن تعديل هذه المطابقة: أحد البلاغين لم يعد متاحًا");
    }

    private void side(Report r, int img, int name, int cat, int loc, int date, int desc, int code) {
        ImageBase64Utils.loadInto(findViewById(img), r.imageBase64, r.placeholder());
        ((TextView) findViewById(name)).setText(r.displayName());
        ((TextView) findViewById(cat)).setText(dash(r.category));
        ((TextView) findViewById(loc)).setText(dash(r.location));
        ((TextView) findViewById(date)).setText(dash(r.date));
        ((TextView) findViewById(desc)).setText(dash(r.description));
        ((TextView) findViewById(code)).setText(r.code());
        View card = (View) findViewById(img).getParent().getParent();
        card.setOnClickListener(v -> Report.openDetails(this, r));
    }

    private static String dash(String s) {
        return isBlank(s) ? "—" : s;
    }

    private void confirmApprove() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("اعتماد المطابقة")
                .setMessage("سيتم ربط البلاغين وإشعار صاحب الغرض ومن وجده. تبقى الاقتراحات الأخرى محفوظة كبدائل. هل أنت متأكد؟")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("اعتماد", (d, w) -> approve())
                .show();
    }

    private void confirmReject() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("غير مطابق")
                .setMessage("لن يُقترح هذان البلاغان معًا مرة أخرى.")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("تأكيد", (d, w) -> reject())
                .show();
    }

    private void approve() {
        if (busy) return;
        busy = true;
        MatchOps.approve(lost, found, score, ok -> {
            if (ok) {
                toast("تم اعتماد المطابقة بنجاح");
                finish();
            } else {
                busy = false;
                toast("تعذر اعتماد المطابقة. تحقق من الاتصال والصلاحيات.");
            }
        });
    }

    private void reject() {
        if (busy) return;
        busy = true;
        MatchOps.rejectSuggestion(lostId, foundId, ok -> {
            if (ok) {
                toast("تم استبعاد هذه المطابقة");
                finish();
            } else {
                busy = false;
                toast("تعذر الحفظ. تحقق من الاتصال.");
            }
        });
    }
}
