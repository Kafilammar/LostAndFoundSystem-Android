package com.example.lostandfoundsystem;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.ChipGroup;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** قائمة عمليات المركز مع التصفية حسب المرحلة. */
public class CenterReportsActivity extends BaseActivity {

    private final List<Report> all = new ArrayList<>();
    private ReportAdapter adapter;
    private View progress, empty;
    private int filterId = R.id.chipCenterWaiting;
    private int generation = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_center_reports);

        bindBack(R.id.btnBackCenterReports);
        progress = findViewById(R.id.centerReportsProgress);
        empty = findViewById(R.id.centerReportsEmpty);
        ((ImageView) empty.findViewById(R.id.emptyIcon)).setImageResource(R.drawable.ic_box);

        RecyclerView list = findViewById(R.id.centerReportsList);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ReportAdapter(false, r -> Report.openDetails(this, r));
        adapter.setActionProvider(r -> CenterOps.actionFor(this, r, this::load));
        list.setAdapter(adapter);

        ChipGroup chips = findViewById(R.id.centerChips);
        chips.setOnCheckedStateChangeListener((g, ids) -> {
            if (ids.isEmpty()) return;
            filterId = ids.get(0);
            applyFilter();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        final int gen = ++generation;
        if (all.isEmpty()) progress.setVisibility(View.VISIBLE);
        Db.get().collection(Db.FOUND).orderBy("createdAt", Query.Direction.DESCENDING).limit(300).get()
                .addOnSuccessListener(snap -> {
                    if (gen != generation || !isAlive()) return;
                    all.clear();
                    for (QueryDocumentSnapshot d : snap) {
                        Report r = Report.from(d);
                        if (Status.isMatched(r.status) || Status.isCompleted(r.status)) all.add(r);
                    }
                    Collections.sort(all, (a, b) -> Long.compare(b.lastActivity(), a.lastActivity()));
                    progress.setVisibility(View.GONE);
                    applyFilter();
                })
                .addOnFailureListener(e -> {
                    if (gen != generation || !isAlive()) return;
                    progress.setVisibility(View.GONE);
                    toast(getString(R.string.no_connection));
                    applyFilter();
                });
    }

    private void applyFilter() {
        List<Report> out = new ArrayList<>();
        for (Report r : all) {
            boolean keep;
            if (filterId == R.id.chipCenterWaiting) keep = Status.isMatched(r.status);
            else if (filterId == R.id.chipCenterAt) keep = Status.AT_CENTER.equals(r.status);
            else if (filterId == R.id.chipCenterReturned) keep = Status.RETURNED.equals(r.status);
            else keep = true;
            if (keep) out.add(r);
        }
        adapter.submit(out);
        boolean show = out.isEmpty() && progress.getVisibility() != View.VISIBLE;
        empty.setVisibility(show ? View.VISIBLE : View.GONE);
        ((TextView) empty.findViewById(R.id.emptyTitle)).setText("لا توجد عمليات هنا");
        ((TextView) empty.findViewById(R.id.emptySubtitle)).setText(
                filterId == R.id.chipCenterWaiting
                        ? "ستظهر هنا الأغراض التي تمت مطابقتها وبانتظار تسليمها للمركز."
                        : "لا توجد أغراض بهذه الحالة حاليًا.");
    }
}
