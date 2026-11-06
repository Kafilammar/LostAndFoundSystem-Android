package com.example.lostandfoundsystem;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** الملف الشخصي: البيانات، الإحصائيات، آخر البلاغات والإعدادات. */
public class ProfileActivity extends BaseActivity {

    private String role = "";
    private int generation = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        bindBack(R.id.btnBackProfile);
        findViewById(R.id.btnEditProfile).setOnClickListener(v -> openEdit());

        menu(R.id.menuEdit, R.drawable.ic_edit, "تعديل البيانات الشخصية", v -> openEdit());
        menu(R.id.menuReports, R.drawable.ic_reports, "تقاريري", v -> startActivity(new Intent(this, MyReportsActivity.class)));
        menu(R.id.menuNotifications, R.drawable.ic_bell, "الإشعارات", v -> startActivity(new Intent(this, NotificationsActivity.class)));
        menu(R.id.menuPassword, R.drawable.ic_lock, "تغيير كلمة المرور", v -> resetPassword());
        menu(R.id.menuHelp, R.drawable.ic_help, "المساعدة ومعلومات المركز", v -> startActivity(new Intent(this, HelpActivity.class)));

        findViewById(R.id.btnProfileReports).setOnClickListener(v -> startActivity(new Intent(this, MyReportsActivity.class)));
        findViewById(R.id.btnProfileLogout).setOnClickListener(v ->
                new MaterialAlertDialogBuilder(this)
                        .setTitle("تسجيل الخروج")
                        .setMessage("هل تريد تسجيل الخروج من حسابك؟")
                        .setNegativeButton("إلغاء", null)
                        .setPositiveButton("خروج", (d, w) -> SessionRouter.logout(this))
                        .show());

        ((TextView) findViewById(R.id.tvProfileVersion)).setText("نظام المفقودات - الإصدار " + versionName());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProfile();
    }

    private void menu(@IdRes int id, @DrawableRes int icon, String title, View.OnClickListener l) {
        View row = findViewById(id);
        ((ImageView) row.findViewById(R.id.menuIcon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.menuTitle)).setText(title);
        row.setOnClickListener(l);
    }

    private void openEdit() {
        startActivity(new Intent(this, EditProfileActivity.class));
    }

    private void loadProfile() {
        FirebaseUser user = currentUser();
        if (user == null) {
            SessionRouter.open(this, LoginActivity.class);
            return;
        }
        final String fallbackId = Db.universityIdFromEmail(user.getEmail());
        Db.get().collection(Db.USERS).document(user.getUid()).get().addOnSuccessListener(doc -> {
            if (!isAlive()) return;
            String name = doc.getString("name");
            String uniId = doc.getString("universityId");
            role = doc.getString("role") == null ? "" : doc.getString("role");
            if (isBlank(uniId)) uniId = fallbackId;
            String shown = isBlank(name) ? "أكمل بياناتك" : name;

            ((TextView) findViewById(R.id.tvAvatar)).setText(initials(isBlank(name) ? uniId : name));
            ((TextView) findViewById(R.id.tvProfileName)).setText(shown);
            ((TextView) findViewById(R.id.tvProfileUniversityId)).setText("الرقم الجامعي: " + uniId);
            ((TextView) findViewById(R.id.tvProfileRole)).setText(Roles.label(role));

            boolean staff = Roles.isStaff(role);
            int vis = staff ? View.GONE : View.VISIBLE;
            findViewById(R.id.profileStatsRow).setVisibility(vis);
            findViewById(R.id.profileRecentHeader).setVisibility(vis);
            findViewById(R.id.profileRecentContainer).setVisibility(vis);
            findViewById(R.id.menuReports).setVisibility(vis);
            if (staff) findViewById(R.id.profileRecentEmpty).setVisibility(View.GONE);
            else loadStats(user.getUid());
        }).addOnFailureListener(e -> {
            if (!isAlive()) return;
            ((TextView) findViewById(R.id.tvProfileUniversityId)).setText("الرقم الجامعي: " + fallbackId);
            toast(getString(R.string.no_connection));
        });
    }

    private void loadStats(String uid) {
        final int gen = ++generation;
        final List<Report> batch = new ArrayList<>();
        final int[] pending = {2};
        for (String c : new String[]{Db.LOST, Db.FOUND}) {
            Db.get().collection(c).whereEqualTo("userId", uid).get()
                    .addOnSuccessListener(snap -> {
                        for (QueryDocumentSnapshot d : snap) batch.add(Report.from(d));
                        if (--pending[0] == 0) renderStats(gen, batch);
                    })
                    .addOnFailureListener(e -> {
                        if (--pending[0] == 0) renderStats(gen, batch);
                    });
        }
    }

    private void renderStats(int gen, List<Report> reports) {
        if (gen != generation || !isAlive()) return;
        int matched = 0, delivered = 0;
        for (Report r : reports) {
            if (Status.isMatched(r.status) || Status.isCompleted(r.status)) matched++;
            if (Status.RETURNED.equals(r.status)) delivered++;
        }
        setStat(R.id.profileStatReports, String.valueOf(reports.size()), "إجمالي البلاغات", R.color.ksu_blue);
        setStat(R.id.profileStatMatched, String.valueOf(matched), "تمت مطابقتها", R.color.found_green);
        setStat(R.id.profileStatDelivered, String.valueOf(delivered), "تم تسليمها", R.color.orange);

        Collections.sort(reports, (a, b) -> Long.compare(b.lastActivity(), a.lastActivity()));
        LinearLayout container = findViewById(R.id.profileRecentContainer);
        container.removeAllViews();
        int count = Math.min(2, reports.size());
        for (int i = 0; i < count; i++) {
            Report r = reports.get(i);
            View card = ReportCard.inflate(container);
            ReportCard.bind(card, r, true);
            card.setOnClickListener(v -> Report.openDetails(this, r));
            container.addView(card);
        }
        findViewById(R.id.profileRecentEmpty).setVisibility(count == 0 ? View.VISIBLE : View.GONE);
    }

    private void resetPassword() {
        FirebaseUser user = currentUser();
        if (user == null || isBlank(user.getEmail())) return;
        String email = user.getEmail();
        new MaterialAlertDialogBuilder(this)
                .setTitle("تغيير كلمة المرور")
                .setMessage("سنرسل رابط تعيين كلمة مرور جديدة إلى بريدك:\n" + email)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("إرسال الرابط", (d, w) ->
                        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                                .addOnSuccessListener(x -> toast("تم إرسال الرابط، تحقق من بريدك الجامعي"))
                                .addOnFailureListener(e -> toast("تعذر إرسال الرابط، حاول لاحقًا")))
                .show();
    }
}
