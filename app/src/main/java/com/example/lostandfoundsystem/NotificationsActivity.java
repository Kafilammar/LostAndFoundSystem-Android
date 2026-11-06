package com.example.lostandfoundsystem;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** الإشعارات: مبنية على تحديثات حالات البلاغات. */
public class NotificationsActivity extends BaseActivity {

    private LinearLayout container;
    private View progress, empty;
    private String role = "";
    private long readAt;
    private int generation = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        bindBack(R.id.btnBackNotifications);
        container = findViewById(R.id.notificationsContainer);
        progress = findViewById(R.id.notificationsProgress);
        empty = findViewById(R.id.notificationsEmpty);

        ((ImageView) empty.findViewById(R.id.emptyIcon)).setImageResource(R.drawable.ic_bell);
        ((TextView) empty.findViewById(R.id.emptyTitle)).setText("لا توجد إشعارات");
        ((TextView) empty.findViewById(R.id.emptySubtitle)).setText("ستظهر هنا تحديثات بلاغاتك مثل الاعتماد والمطابقة والتسليم.");

        readAt = NotificationItem.readAt(this, uidOrEmpty());

        findViewById(R.id.btnMarkAllNotifications).setOnClickListener(v -> {
            NotificationItem.markAllRead(this, uidOrEmpty());
            readAt = System.currentTimeMillis();
            for (int i = 0; i < container.getChildCount(); i++) {
                View dot = container.getChildAt(i).findViewById(R.id.notifDot);
                if (dot != null) dot.setVisibility(View.GONE);
            }
            toast("تم تعليم الكل كمقروء");
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        String uid = uidOrEmpty();
        if (uid.isEmpty()) {
            SessionRouter.open(this, LoginActivity.class);
            return;
        }
        Db.get().collection(Db.USERS).document(uid).get()
                .addOnSuccessListener(d -> {
                    role = d.getString("role") == null ? "" : d.getString("role");
                    load(uid);
                })
                .addOnFailureListener(e -> load(uid));
    }

    @Override
    protected void onStop() {
        super.onStop();
        // بعد مشاهدة الإشعارات تُعتبر مقروءة
        if (container.getChildCount() > 0) NotificationItem.markAllRead(this, uidOrEmpty());
    }

    private void load(String uid) {
        if (!isAlive()) return;
        if (container.getChildCount() == 0) progress.setVisibility(View.VISIBLE);
        final boolean staff = Roles.isStaff(role);
        final int gen = ++generation;
        final List<Report> batch = new ArrayList<>();
        final int[] pending = {2};

        for (String c : new String[]{Db.LOST, Db.FOUND}) {
            Query q = staff
                    ? Db.get().collection(c).orderBy("createdAt", Query.Direction.DESCENDING).limit(60)
                    : Db.get().collection(c).whereEqualTo("userId", uid);
            q.get().addOnSuccessListener(snap -> {
                for (QueryDocumentSnapshot d : snap) batch.add(Report.from(d));
                if (--pending[0] == 0) render(gen, batch, staff);
            }).addOnFailureListener(e -> {
                if (--pending[0] == 0) render(gen, batch, staff);
            });
        }
    }

    private void render(int gen, List<Report> reports, boolean staff) {
        if (gen != generation || !isAlive()) return;
        progress.setVisibility(View.GONE);

        List<NotificationItem> items = new ArrayList<>();
        for (Report r : reports) items.add(staff ? NotificationItem.forStaff(r) : NotificationItem.forOwner(r));
        Collections.sort(items, (a, b) -> Long.compare(b.time, a.time));

        container.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(this);
        long now = System.currentTimeMillis();
        for (NotificationItem n : items) {
            View v = inf.inflate(R.layout.item_notification, container, false);
            ImageView icon = v.findViewById(R.id.notifIcon);
            icon.setImageResource(n.icon);
            icon.setBackgroundResource(n.circle);
            ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(ContextCompat.getColor(this, n.tint)));
            ((TextView) v.findViewById(R.id.notifTitle)).setText(n.title);
            ((TextView) v.findViewById(R.id.notifBody)).setText(n.body);
            ((TextView) v.findViewById(R.id.notifTime)).setText(n.time > 0
                    ? DateUtils.getRelativeTimeSpanString(n.time, now, DateUtils.MINUTE_IN_MILLIS)
                    : "");
            v.findViewById(R.id.notifDot).setVisibility(n.time > readAt ? View.VISIBLE : View.GONE);
            v.setOnClickListener(x -> {
                if (Roles.isAdmin(role)) Report.openAdminReview(this, n.report);
                else Report.openDetails(this, n.report);
            });
            container.addView(v);
        }
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
