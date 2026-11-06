package com.example.lostandfoundsystem;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** كل بلاغات المستخدم (المفقودة والموجودة) مع تصفية حسب الحالة. */
public class MyReportsActivity extends BaseActivity {

    private final List<Report> all = new ArrayList<>();
    private ReportAdapter adapter;
    private View progress, empty;
    private int filterId = R.id.chipAll;
    private int generation = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_reports);

        bindBack(R.id.btnBackMyReports);
        progress = findViewById(R.id.myReportsProgress);
        empty = findViewById(R.id.myReportsEmpty);

        RecyclerView list = findViewById(R.id.myReportsList);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ReportAdapter(true, r -> Report.openDetails(this, r));
        adapter.setActionProvider(r -> {
            if (!r.lost && Status.isMatched(r.status)) {
                return new ReportAdapter.Action(
                        isBlank(r.appointmentDate) ? "تحديد موعد التسليم" : "تعديل موعد التسليم",
                        R.color.found_green, rep -> {
                    Intent i = new Intent(this, DeliveryConfirmationActivity.class);
                    i.putExtra(DeliveryConfirmationActivity.EXTRA_ID, rep.id);
                    startActivity(i);
                });
            }
            return null;
        });
        list.setAdapter(adapter);

        ChipGroup chips = findViewById(R.id.myReportsChips);
        chips.setOnCheckedStateChangeListener((g, ids) -> {
            if (ids.isEmpty()) return;
            filterId = ids.get(0);
            applyFilter();
        });

        findViewById(R.id.fabNewReport).setOnClickListener(v -> chooseReportType());
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView rv, int dx, int dy) {
                com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton fab = findViewById(R.id.fabNewReport);
                if (dy > 8) fab.shrink();
                else if (dy < -8) fab.extend();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void chooseReportType() {
        String[] options = {"فقدت شيئًا (بلاغ مفقود)", "وجدت شيئًا (بلاغ موجود)"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("نوع البلاغ")
                .setItems(options, (d, which) -> {
                    Intent i = new Intent(this, ReportItemActivity.class);
                    i.putExtra(ReportItemActivity.EXTRA_IS_LOST, which == 0);
                    startActivity(i);
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void load() {
        String uid = uidOrEmpty();
        if (uid.isEmpty()) {
            SessionRouter.open(this, LoginActivity.class);
            return;
        }
        if (all.isEmpty()) progress.setVisibility(View.VISIBLE);
        final int gen = ++generation;
        final List<Report> batch = new ArrayList<>();
        final int[] pending = {2};
        final boolean[] failed = {false};
        for (String c : new String[]{Db.LOST, Db.FOUND}) {
            Db.get().collection(c).whereEqualTo("userId", uid).get()
                    .addOnSuccessListener(snap -> {
                        for (QueryDocumentSnapshot d : snap) batch.add(Report.from(d));
                        if (--pending[0] == 0) done(gen, batch, failed[0]);
                    })
                    .addOnFailureListener(e -> {
                        failed[0] = true;
                        if (--pending[0] == 0) done(gen, batch, true);
                    });
        }
    }

    private void done(int gen, List<Report> batch, boolean failed) {
        if (gen != generation || !isAlive()) return;
        progress.setVisibility(View.GONE);
        if (failed && batch.isEmpty()) toast(getString(R.string.no_connection));
        all.clear();
        all.addAll(batch);
        Collections.sort(all, (a, b) -> Long.compare(b.lastActivity(), a.lastActivity()));
        ((TextView) findViewById(R.id.tvMyReportsTitle)).setText("تقاريري (" + all.size() + ")");
        applyFilter();
    }

    private void applyFilter() {
        List<Report> out = new ArrayList<>();
        for (Report r : all) {
            String s = r.status;
            boolean keep;
            if (filterId == R.id.chipReview) keep = Status.isInReview(s);
            else if (filterId == R.id.chipMatched) keep = Status.APPROVED.equals(s) || Status.isMatched(s);
            else if (filterId == R.id.chipDone) keep = Status.isCompleted(s);
            else if (filterId == R.id.chipRejected) keep = Status.REJECTED.equals(s);
            else keep = true;
            if (keep) out.add(r);
        }
        adapter.submit(out);

        boolean showEmpty = out.isEmpty() && progress.getVisibility() != View.VISIBLE;
        empty.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        if (showEmpty) {
            ((ImageView) empty.findViewById(R.id.emptyIcon)).setImageResource(R.drawable.ic_reports);
            ((TextView) empty.findViewById(R.id.emptyTitle)).setText(all.isEmpty() ? "لا توجد بلاغات بعد" : "لا توجد بلاغات بهذه الحالة");
            ((TextView) empty.findViewById(R.id.emptySubtitle)).setText(all.isEmpty()
                    ? "اضغط على \"بلاغ جديد\" للإبلاغ عن غرض مفقود أو موجود."
                    : "جرّب اختيار تصنيف آخر من الأعلى.");
        }
    }
}
