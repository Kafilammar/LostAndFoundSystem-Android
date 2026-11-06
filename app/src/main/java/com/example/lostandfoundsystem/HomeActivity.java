package com.example.lostandfoundsystem;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** الشاشة الرئيسية للطالب. */
public class HomeActivity extends BaseActivity {

    public static final String EXTRA_LOST_MODE = "lostMode";

    private boolean lostMode = true;
    private final List<Report> lostReports = new ArrayList<>();
    private final List<Report> foundReports = new ArrayList<>();
    private int pendingLoads = 0;

    private TextView modeLost, modeFound, tvHeroTitle, tvHeroSubtitle, tvName, profileImage, bellBadge;
    private MaterialButton actionButton;
    private ImageView heroIcon;
    private View heroCard, latestEmpty;
    private LinearLayout latestContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        lostMode = savedInstanceState != null
                ? savedInstanceState.getBoolean(EXTRA_LOST_MODE, true)
                : getIntent().getBooleanExtra(EXTRA_LOST_MODE, true);

        modeLost = findViewById(R.id.modeLost);
        modeFound = findViewById(R.id.modeFound);
        tvHeroTitle = findViewById(R.id.tvHeroTitle);
        tvHeroSubtitle = findViewById(R.id.tvHeroSubtitle);
        tvName = findViewById(R.id.tvName);
        profileImage = findViewById(R.id.profileImage);
        bellBadge = findViewById(R.id.bellBadge);
        actionButton = findViewById(R.id.actionButton);
        heroIcon = findViewById(R.id.heroIcon);
        heroCard = findViewById(R.id.heroCard);
        latestEmpty = findViewById(R.id.latestEmpty);
        latestContainer = findViewById(R.id.latestContainer);

        modeLost.setOnClickListener(v -> setMode(true));
        modeFound.setOnClickListener(v -> setMode(false));
        actionButton.setOnClickListener(v -> openReport(lostMode));
        latestEmpty.setOnClickListener(v -> openReport(lostMode));
        findViewById(R.id.latestSeeAll).setOnClickListener(v -> startActivity(new Intent(this, MyReportsActivity.class)));
        findViewById(R.id.helpCard).setOnClickListener(v -> startActivity(new Intent(this, HelpActivity.class)));
        findViewById(R.id.btnNotifications).setOnClickListener(v -> startActivity(new Intent(this, NotificationsActivity.class)));
        profileImage.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));

        setupBottomNav();
        applyMode();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean(EXTRA_LOST_MODE, lostMode);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadUser();
        loadReports();
    }

    private void setupBottomNav() {
        findViewById(R.id.navHome).setSelected(true);
        findViewById(R.id.navSearch).setOnClickListener(v -> {
            Intent i = new Intent(this, SearchActivity.class);
            i.putExtra(SearchActivity.EXTRA_LOST_MODE, !lostMode); // من فقد شيئًا يبحث في الموجودات والعكس
            startActivity(i);
        });
        findViewById(R.id.navReport).setOnClickListener(v -> openReport(lostMode));
        findViewById(R.id.navReports).setOnClickListener(v -> startActivity(new Intent(this, MyReportsActivity.class)));
        findViewById(R.id.navProfile).setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
    }

    private void openReport(boolean lost) {
        Intent i = new Intent(this, ReportItemActivity.class);
        i.putExtra(ReportItemActivity.EXTRA_IS_LOST, lost);
        startActivity(i);
    }

    private void setMode(boolean lost) {
        if (lostMode == lost) return;
        lostMode = lost;
        applyMode();
        render();
    }

    private void applyMode() {
        styleSegment(modeLost, lostMode, R.drawable.bg_segment_red);
        styleSegment(modeFound, !lostMode, R.drawable.bg_segment_green);
        heroCard.setBackgroundResource(lostMode ? R.drawable.bg_hero_blue : R.drawable.bg_hero_green);
        tvHeroTitle.setText(lostMode ? "هل فقدت شيئًا؟" : "هل وجدت شيئًا؟");
        tvHeroSubtitle.setText(lostMode
                ? "أبلغ الآن وابدأ رحلة استرداد ممتلكاتك داخل الحرم الجامعي"
                : "ساعد زميلك في استعادة غرضه، وأبلغ عمّا وجدته في دقيقة");
        actionButton.setText(lostMode ? "إبلاغ عن مفقود" : "إبلاغ عن موجود");
        actionButton.setTextColor(getColor(lostMode ? R.color.ksu_blue : R.color.found_green));
        actionButton.setIconTint(android.content.res.ColorStateList.valueOf(
                getColor(lostMode ? R.color.ksu_blue : R.color.found_green)));
        heroIcon.setImageResource(lostMode ? R.drawable.ic_search : R.drawable.ic_hand_heart);
    }

    private void loadUser() {
        String uid = uidOrEmpty();
        if (uid.isEmpty()) {
            SessionRouter.open(this, LoginActivity.class);
            return;
        }
        Db.get().collection(Db.USERS).document(uid).get().addOnSuccessListener(doc -> {
            if (!isAlive()) return;
            String name = doc.getString("name");
            if (isBlank(name)) name = Db.universityIdFromEmail(currentUser() == null ? "" : currentUser().getEmail());
            tvName.setText(isBlank(name) ? "مستخدم" : name);
            profileImage.setText(initials(name));
        });
    }

    private void loadReports() {
        String uid = uidOrEmpty();
        if (uid.isEmpty()) return;
        pendingLoads = 2;
        load(Db.LOST, uid, lostReports);
        load(Db.FOUND, uid, foundReports);
    }

    private void load(String collection, String uid, List<Report> target) {
        Db.get().collection(collection).whereEqualTo("userId", uid).get()
                .addOnSuccessListener(snap -> {
                    target.clear();
                    for (QueryDocumentSnapshot d : snap) target.add(Report.from(d));
                    Collections.sort(target, (a, b) -> Long.compare(b.lastActivity(), a.lastActivity()));
                    onLoaded();
                })
                .addOnFailureListener(e -> onLoaded());
    }

    private void onLoaded() {
        pendingLoads--;
        if (pendingLoads <= 0 && isAlive()) {
            render();
            updateBadge();
        }
    }

    private void render() {
        List<Report> list = lostMode ? lostReports : foundReports;
        int matched = 0, completed = 0;
        for (Report r : list) {
            if (Status.isMatched(r.status)) matched++;
            if (Status.isCompleted(r.status)) completed++;
        }
        setStat(R.id.stat1, String.valueOf(list.size()), lostMode ? "بلاغات مفقودة" : "بلاغات موجودة", R.color.ksu_blue);
        setStat(R.id.stat2, String.valueOf(matched), "تمت مطابقتها", R.color.found_green);
        setStat(R.id.stat3, String.valueOf(completed), lostMode ? "جاهزة / مستلمة" : "سُلّمت للمركز", R.color.orange);

        latestContainer.removeAllViews();
        int count = Math.min(3, list.size());
        for (int i = 0; i < count; i++) {
            Report r = list.get(i);
            View card = ReportCard.inflate(latestContainer);
            ReportCard.bind(card, r, false);
            card.setOnClickListener(v -> Report.openDetails(this, r));
            latestContainer.addView(card);
        }
        latestEmpty.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
    }

    private void updateBadge() {
        long readAt = NotificationItem.readAt(this, uidOrEmpty());
        int unread = 0;
        for (Report r : lostReports) if (r.lastActivity() > readAt) unread++;
        for (Report r : foundReports) if (r.lastActivity() > readAt) unread++;
        if (unread > 0) {
            bellBadge.setVisibility(View.VISIBLE);
            bellBadge.setText(unread > 9 ? "9+" : String.valueOf(unread));
        } else {
            bellBadge.setVisibility(View.GONE);
        }
    }
}
