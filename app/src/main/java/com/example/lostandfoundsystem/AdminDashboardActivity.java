package com.example.lostandfoundsystem;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.widget.NestedScrollView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** لوحة تحكم مسؤول النظام. */
public class AdminDashboardActivity extends BaseActivity {

    private final List<Report> lost = new ArrayList<>();
    private final List<Report> found = new ArrayList<>();
    private int generation = 0;

    private SwipeRefreshLayout refresh;
    private View progress;
    private LinearLayout newContainer, matchesContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        refresh = findViewById(R.id.adminRefresh);
        refresh.setColorSchemeResources(R.color.ksu_blue);
        refresh.setOnRefreshListener(this::load);
        progress = findViewById(R.id.adminProgress);
        newContainer = findViewById(R.id.adminNewReportsContainer);
        matchesContainer = findViewById(R.id.adminMatchesContainer);

        View.OnClickListener allReports = v -> openAllReports(true);
        findViewById(R.id.btnAdminViewAllReports).setOnClickListener(allReports);
        findViewById(R.id.adminNewSeeAll).setOnClickListener(allReports);
        findViewById(R.id.btnAdminProfile).setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        findViewById(R.id.tvAdminAvatar).setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        findViewById(R.id.btnAdminLogout).setOnClickListener(v -> confirmLogout());

        findViewById(R.id.navAdminHome).setSelected(true);
        findViewById(R.id.navAdminHome).setOnClickListener(v ->
                ((NestedScrollView) findViewById(R.id.adminScroll)).smoothScrollTo(0, 0));
        findViewById(R.id.navAdminReports).setOnClickListener(allReports);
        findViewById(R.id.navAdminMatches).setOnClickListener(v -> {
            NestedScrollView s = findViewById(R.id.adminScroll);
            s.smoothScrollTo(0, matchesContainer.getTop() - dp(80));
        });
        findViewById(R.id.navAdminNotifications).setOnClickListener(v ->
                startActivity(new Intent(this, NotificationsActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadName();
        load();
    }

    private void confirmLogout() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("تسجيل الخروج")
                .setMessage("هل تريد تسجيل الخروج من لوحة التحكم؟")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("خروج", (d, w) -> SessionRouter.logout(this))
                .show();
    }

    private void openAllReports(boolean lostMode) {
        Intent i = new Intent(this, SearchActivity.class);
        i.putExtra(SearchActivity.EXTRA_ADMIN_MODE, true);
        i.putExtra(SearchActivity.EXTRA_LOST_MODE, lostMode);
        startActivity(i);
    }

    private void loadName() {
        String uid = uidOrEmpty();
        if (uid.isEmpty()) {
            SessionRouter.open(this, LoginActivity.class);
            return;
        }
        Db.get().collection(Db.USERS).document(uid).get().addOnSuccessListener(doc -> {
            if (!isAlive()) return;
            String role = doc.getString("role");
            if (!Roles.isAdmin(role)) { // حماية: هذه الشاشة للمسؤول فقط
                SessionRouter.route(this, null);
                return;
            }
            String name = doc.getString("name");
            ((TextView) findViewById(R.id.tvAdminName)).setText(isBlank(name) ? "مسؤول النظام" : name);
            ((TextView) findViewById(R.id.tvAdminAvatar)).setText(initials(name));
        });
    }

    private void load() {
        final int gen = ++generation;
        final List<Report> l = new ArrayList<>(), f = new ArrayList<>();
        final int[] pending = {2};
        final boolean[] failed = {false};
        if (lost.isEmpty() && found.isEmpty()) progress.setVisibility(View.VISIBLE);

        fetch(Db.LOST, l, () -> { if (--pending[0] == 0) done(gen, l, f, failed[0]); }, failed);
        fetch(Db.FOUND, f, () -> { if (--pending[0] == 0) done(gen, l, f, failed[0]); }, failed);
    }

    private void fetch(String c, List<Report> into, Runnable after, boolean[] failed) {
        Db.get().collection(c).orderBy("createdAt", Query.Direction.DESCENDING).limit(300).get()
                .addOnSuccessListener(snap -> {
                    for (QueryDocumentSnapshot d : snap) into.add(Report.from(d));
                    after.run();
                })
                .addOnFailureListener(e -> {
                    failed[0] = true;
                    after.run();
                });
    }

    private void done(int gen, List<Report> l, List<Report> f, boolean failed) {
        if (gen != generation || !isAlive()) return;
        refresh.setRefreshing(false);
        progress.setVisibility(View.GONE);
        if (failed) toast(getString(R.string.no_connection));
        lost.clear(); lost.addAll(l);
        found.clear(); found.addAll(f);
        render();
    }

    private void render() {
        List<Report> all = new ArrayList<>(lost);
        all.addAll(found);

        int matched = 0, reviewed = 0, returned = 0, pending = 0;
        List<Report> waiting = new ArrayList<>();
        for (Report r : all) {
            if (Status.isMatched(r.status) || Status.isCompleted(r.status)) matched++;
            if (Status.RETURNED.equals(r.status)) returned++;
            if (Status.isInReview(r.status)) {
                pending++;
                waiting.add(r);
            } else {
                reviewed++;
            }
        }
        setStat(R.id.adminStatReports, String.valueOf(all.size()), "إجمالي البلاغات", R.color.ksu_blue);
        setStat(R.id.adminStatMatches, String.valueOf(matched), "مطابقات", R.color.found_green);
        setStat(R.id.adminStatReviewed, String.valueOf(reviewed), "تمت مراجعتها", R.color.dashboard_blue);
        setStat(R.id.adminStatReturned, String.valueOf(returned), "تم تسليمها", R.color.orange);

        ((TextView) findViewById(R.id.tvAdminHeroSubtitle)).setText(pending == 0
                ? "لا توجد بلاغات بانتظار المراجعة حاليًا"
                : pending + " بلاغ بانتظار مراجعتك");

        // البلاغات الجديدة (الأقدم أولًا حتى لا يتأخر أحد)
        Collections.sort(waiting, (a, b) -> Long.compare(a.createdAt, b.createdAt));
        newContainer.removeAllViews();
        for (int i = 0; i < Math.min(5, waiting.size()); i++) {
            Report r = waiting.get(i);
            View card = ReportCard.inflate(newContainer);
            ReportCard.bind(card, r, true);
            card.setOnClickListener(v -> Report.openAdminReview(this, r));
            ReportCard.setAction(card, "مراجعة البلاغ", R.color.ksu_blue, v -> Report.openAdminReview(this, r));
            newContainer.addView(card);
        }
        findViewById(R.id.adminNewReportsEmpty).setVisibility(waiting.isEmpty() ? View.VISIBLE : View.GONE);

        renderAttention();

        // المطابقات المقترحة (حتى 3 اقتراحات لكل بلاغ مفقود)
        List<Matcher.Pair> pairs = Matcher.suggest(lost, found);
        matchesContainer.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(this);
        java.util.Map<String, Integer> perLost = new java.util.HashMap<>();
        int shown = 0;
        for (Matcher.Pair p : pairs) {
            if (shown >= 12) break;
            Integer n = perLost.get(p.lost.id);
            if (n != null && n >= 3) continue;
            perLost.put(p.lost.id, n == null ? 1 : n + 1);
            shown++;

            View v = inf.inflate(R.layout.item_match, matchesContainer, false);
            int level = p.result.level();
            TextView chip = v.findViewById(R.id.matchScore);
            chip.setText(p.score + "% • " + MatchScore.label(level).replace("تطابق ", ""));
            chip.setTextColor(getColor(level == MatchScore.HIGH ? R.color.found_green
                    : level == MatchScore.MEDIUM ? R.color.orange : R.color.danger_text));
            chip.setBackgroundResource(level == MatchScore.HIGH ? R.drawable.bg_chip_green
                    : level == MatchScore.MEDIUM ? R.drawable.bg_chip_orange : R.drawable.bg_chip_red);
            String title = p.claimed ? "طلب من صاحب البلاغ"
                    : (p.total > 1 ? "الاقتراح " + p.rank + " من " + p.total : "مطابقة مقترحة");
            if (p.competing > 0) title += " • مقترح لـ " + (p.competing + 1) + " بلاغات";
            ((TextView) v.findViewById(R.id.matchTitle)).setText(title);
            ((ProgressBar) v.findViewById(R.id.matchScoreBar)).setProgress(p.score);
            ImageBase64Utils.loadInto(v.findViewById(R.id.matchLostImage), p.lost.imageBase64, p.lost.placeholder());
            ImageBase64Utils.loadInto(v.findViewById(R.id.matchFoundImage), p.found.imageBase64, p.found.placeholder());
            ((TextView) v.findViewById(R.id.matchLostName)).setText(p.lost.displayName());
            ((TextView) v.findViewById(R.id.matchFoundName)).setText(p.found.displayName());
            ((TextView) v.findViewById(R.id.matchLostMeta)).setText(meta(p.lost));
            ((TextView) v.findViewById(R.id.matchFoundMeta)).setText(meta(p.found));
            View.OnClickListener open = x -> openMatch(p);
            v.setOnClickListener(open);
            v.findViewById(R.id.btnReviewMatch).setOnClickListener(open);
            matchesContainer.addView(v);
        }
        findViewById(R.id.adminMatchesEmpty).setVisibility(pairs.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** بلاغات تحتاج تدخل المسؤول. */
    private void renderAttention() {
        LinearLayout box = findViewById(R.id.adminAttentionContainer);
        box.removeAllViews();
        for (Report r : lost) {
            String reason = null;
            if (!isBlank(r.disputeReason) && r.hasActiveMatch()) {
                reason = "أفاد صاحب البلاغ أن الغرض ليس له: " + r.disputeReason;
            } else if (Matcher.lostOpen(r) && r.verifyFailCount >= 2) {
                reason = "فشل التحقق " + r.verifyFailCount + " مرات — يُنصح بطلب تفاصيل إضافية من صاحب البلاغ";
            }
            if (reason == null) continue;
            View card = ReportCard.inflate(box);
            ReportCard.bind(card, r, true);
            ((TextView) card.findViewById(R.id.reportDate)).setText(reason);
            card.setOnClickListener(v -> Report.openAdminReview(this, r));
            ReportCard.setAction(card, "متابعة", R.color.orange, v -> Report.openAdminReview(this, r));
            box.addView(card);
        }
        findViewById(R.id.adminAttentionSection).setVisibility(box.getChildCount() > 0 ? View.VISIBLE : View.GONE);
    }

    private static String meta(Report r) {
        String loc = isBlank(r.location) ? "—" : r.location;
        return isBlank(r.date) ? loc : loc + "\n" + r.date;
    }

    private void openMatch(Matcher.Pair p) {
        Intent i = new Intent(this, MatchReviewActivity.class);
        i.putExtra(MatchReviewActivity.EXTRA_LOST_ID, p.lost.id);
        i.putExtra(MatchReviewActivity.EXTRA_FOUND_ID, p.found.id);
        i.putExtra(MatchReviewActivity.EXTRA_SCORE, p.score);
        i.putExtra(MatchReviewActivity.EXTRA_RANK, p.rank);
        i.putExtra(MatchReviewActivity.EXTRA_TOTAL, p.total);
        i.putExtra(MatchReviewActivity.EXTRA_COMPETING, p.competing);
        startActivity(i);
    }
}
