package com.example.lostandfoundsystem;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** لوحة موظف مركز المفقودات. */
public class CenterDashboardActivity extends BaseActivity {

    private SwipeRefreshLayout refresh;
    private View progress;
    private LinearLayout container;
    private boolean loadedOnce = false;
    private int generation = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_center_dashboard);

        refresh = findViewById(R.id.centerRefresh);
        refresh.setColorSchemeResources(R.color.ksu_blue);
        refresh.setOnRefreshListener(this::load);
        progress = findViewById(R.id.centerProgress);
        container = findViewById(R.id.centerOperationsContainer);

        View.OnClickListener all = v -> startActivity(new Intent(this, CenterReportsActivity.class));
        findViewById(R.id.btnCenterViewAll).setOnClickListener(all);
        findViewById(R.id.centerSeeAll).setOnClickListener(all);
        findViewById(R.id.tvCenterAvatar).setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        findViewById(R.id.btnCenterLogout).setOnClickListener(v ->
                new MaterialAlertDialogBuilder(this)
                        .setTitle("تسجيل الخروج")
                        .setMessage("هل تريد تسجيل الخروج؟")
                        .setNegativeButton("إلغاء", null)
                        .setPositiveButton("خروج", (d, w) -> SessionRouter.logout(this))
                        .show());

        findViewById(R.id.navCenterHome).setSelected(true);
        findViewById(R.id.navCenterSearch).setOnClickListener(v -> {
            Intent i = new Intent(this, SearchActivity.class);
            i.putExtra(SearchActivity.EXTRA_LOST_MODE, false);
            startActivity(i);
        });
        findViewById(R.id.navCenterReports).setOnClickListener(all);
        findViewById(R.id.navCenterNotifications).setOnClickListener(v ->
                startActivity(new Intent(this, NotificationsActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadName();
        load();
    }

    private void loadName() {
        String uid = uidOrEmpty();
        if (uid.isEmpty()) {
            SessionRouter.open(this, LoginActivity.class);
            return;
        }
        Db.get().collection(Db.USERS).document(uid).get().addOnSuccessListener(doc -> {
            if (!isAlive()) return;
            if (!Roles.isCenter(doc.getString("role"))) {
                SessionRouter.route(this, null);
                return;
            }
            String name = doc.getString("name");
            ((TextView) findViewById(R.id.tvCenterName)).setText(isBlank(name) ? "موظف المركز" : name);
            ((TextView) findViewById(R.id.tvCenterAvatar)).setText(initials(name));
        });
    }

    private void load() {
        final int gen = ++generation;
        if (!loadedOnce) progress.setVisibility(View.VISIBLE);
        Db.get().collection(Db.FOUND).orderBy("createdAt", Query.Direction.DESCENDING).limit(300).get()
                .addOnSuccessListener(snap -> {
                    if (gen != generation || !isAlive()) return;
                    List<Report> list = new ArrayList<>();
                    for (QueryDocumentSnapshot d : snap) list.add(Report.from(d));
                    render(list);
                })
                .addOnFailureListener(e -> {
                    if (gen != generation || !isAlive()) return;
                    refresh.setRefreshing(false);
                    progress.setVisibility(View.GONE);
                    toast(getString(R.string.no_connection));
                });
    }

    private void render(List<Report> list) {
        loadedOnce = true;
        refresh.setRefreshing(false);
        progress.setVisibility(View.GONE);

        int waiting = 0, atCenter = 0, returned = 0;
        List<Report> ops = new ArrayList<>();
        for (Report r : list) {
            if (Status.isMatched(r.status)) { waiting++; ops.add(r); }
            else if (Status.AT_CENTER.equals(r.status)) { atCenter++; ops.add(r); }
            else if (Status.RETURNED.equals(r.status)) returned++;
        }
        setStat(R.id.centerStatWaiting, String.valueOf(waiting), "بانتظار الاستلام", R.color.warning_text);
        setStat(R.id.centerStatCenter, String.valueOf(atCenter), "في المركز", R.color.orange);
        setStat(R.id.centerStatReturned, String.valueOf(returned), "تم تسليمها", R.color.found_green);

        // المطابقة التي لديها موعد أقرب تظهر أولًا، ثم الأحدث
        Collections.sort(ops, (a, b) -> Long.compare(b.lastActivity(), a.lastActivity()));
        container.removeAllViews();
        for (int i = 0; i < Math.min(6, ops.size()); i++) {
            Report r = ops.get(i);
            View card = ReportCard.inflate(container);
            ReportCard.bind(card, r, false);
            if (!isBlank(r.appointmentDate) && Status.isMatched(r.status)) {
                ((TextView) card.findViewById(R.id.reportDate)).setText("موعد التسليم: " + r.appointmentDate
                        + (isBlank(r.appointmentTime) ? "" : " - " + r.appointmentTime) + "  •  " + r.code());
            }
            if (Status.AT_CENTER.equals(r.status) && !r.hasActiveMatch()) {
                ((TextView) card.findViewById(R.id.reportDate)).setText("في المركز بانتظار مطابقة مع صاحبه  •  " + r.code());
            }
            card.setOnClickListener(v -> Report.openDetails(this, r));
            ReportAdapter.Action a = CenterOps.actionFor(this, r, this::load);
            if (a != null) ReportCard.setAction(card, a.label, a.colorRes, v -> a.onClick.onClick(r));
            container.addView(card);
        }
        findViewById(R.id.centerOperationsEmpty).setVisibility(ops.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
