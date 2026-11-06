package com.example.lostandfoundsystem;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** البحث في البلاغات (للطلاب والموظفين). */
public class SearchActivity extends BaseActivity {

    public static final String EXTRA_LOST_MODE = "lostMode";
    public static final String EXTRA_ADMIN_MODE = "adminMode";
    public static final String EXTRA_STATUS_FILTER = "statusFilter";

    private static final String ALL = "الكل";

    private boolean lostMode = true;
    private boolean adminMode = false;
    private String statusFilter = "";
    private String category = ALL;
    private String query = "";

    private final List<Report> all = new ArrayList<>();
    private ReportAdapter adapter;
    private TextView tabLost, tabFound, tvCount;
    private View progress, empty, btnClear;
    private int requestId = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        lostMode = getIntent().getBooleanExtra(EXTRA_LOST_MODE, true);
        adminMode = getIntent().getBooleanExtra(EXTRA_ADMIN_MODE, false);
        String sf = getIntent().getStringExtra(EXTRA_STATUS_FILTER);
        statusFilter = sf == null ? "" : sf;
        if (savedInstanceState != null) lostMode = savedInstanceState.getBoolean(EXTRA_LOST_MODE, lostMode);

        bindBack(R.id.btnBackSearch);
        ((TextView) findViewById(R.id.tvSearchTitle)).setText(adminMode ? "إدارة البلاغات" : "البحث عن الأغراض");

        tabLost = findViewById(R.id.tabLost);
        tabFound = findViewById(R.id.tabFound);
        tvCount = findViewById(R.id.tvResultCount);
        progress = findViewById(R.id.searchProgress);
        empty = findViewById(R.id.searchEmpty);
        btnClear = findViewById(R.id.btnClearSearch);

        RecyclerView list = findViewById(R.id.searchList);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ReportAdapter(false, r -> {
            if (adminMode) Report.openAdminReview(this, r);
            else Report.openDetails(this, r);
        });
        list.setAdapter(adapter);

        EditText et = findViewById(R.id.etSearchItems);
        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                query = TextNorm.normalize(s.toString());
                btnClear.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                applyFilter();
            }
        });
        btnClear.setVisibility(View.GONE);
        btnClear.setOnClickListener(v -> et.setText(""));

        tabLost.setOnClickListener(v -> setTab(true));
        tabFound.setOnClickListener(v -> setTab(false));

        buildCategoryChips();
        styleTabs();

        ((TextView) empty.findViewById(R.id.emptyTitle)).setText("لا توجد نتائج");
        ((TextView) empty.findViewById(R.id.emptySubtitle)).setText("جرّب كلمة أخرى أو غيّر الفئة المختارة.");
        ((android.widget.ImageView) empty.findViewById(R.id.emptyIcon)).setImageResource(R.drawable.ic_search);
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean(EXTRA_LOST_MODE, lostMode);
    }

    private void buildCategoryChips() {
        ChipGroup group = findViewById(R.id.chipGroupCategory);
        group.removeAllViews();
        List<String> cats = new ArrayList<>();
        cats.add(ALL);
        Collections.addAll(cats, Db.CATEGORIES);
        for (String c : cats) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.view_filter_chip, group, false);
            chip.setId(View.generateViewId());
            chip.setText(c);
            chip.setTag(c);
            group.addView(chip);
            if (c.equals(ALL)) group.check(chip.getId());
        }
        group.setOnCheckedStateChangeListener((g, ids) -> {
            if (ids.isEmpty()) return;
            Chip c = g.findViewById(ids.get(0));
            category = c == null ? ALL : String.valueOf(c.getTag());
            applyFilter();
        });
    }

    private void setTab(boolean lost) {
        if (lostMode == lost) return;
        lostMode = lost;
        styleTabs();
        load();
    }

    private void styleTabs() {
        styleSegment(tabLost, lostMode, R.drawable.bg_segment_red);
        styleSegment(tabFound, !lostMode, R.drawable.bg_segment_green);
    }

    private void load() {
        final int req = ++requestId;
        progress.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);
        Db.get().collection(lostMode ? Db.LOST : Db.FOUND)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(200)
                .get()
                .addOnSuccessListener(snap -> {
                    if (req != requestId || !isAlive()) return;
                    all.clear();
                    for (QueryDocumentSnapshot d : snap) {
                        Report r = Report.from(d);
                        // الطلاب لا يرون البلاغات المرفوضة أو غير المعتمدة لغيرهم
                        if (!adminMode && Status.REJECTED.equals(r.status)) continue;
                        if (!statusFilter.isEmpty() && !statusFilter.equals(r.status)) continue;
                        all.add(r);
                    }
                    progress.setVisibility(View.GONE);
                    applyFilter();
                })
                .addOnFailureListener(e -> {
                    if (req != requestId || !isAlive()) return;
                    progress.setVisibility(View.GONE);
                    all.clear();
                    applyFilter();
                    toast(getString(R.string.no_connection));
                });
    }

    private void applyFilter() {
        List<Report> out = new ArrayList<>();
        for (Report r : all) {
            if (!ALL.equals(category) && !category.equals(r.category)) continue;
            if (!query.isEmpty() && !r.matches(query)) continue;
            out.add(r);
        }
        adapter.submit(out);
        tvCount.setText(out.size() == 0 ? "" : "عدد النتائج: " + out.size());
        empty.setVisibility(out.isEmpty() && progress.getVisibility() != View.VISIBLE ? View.VISIBLE : View.GONE);
    }
}
