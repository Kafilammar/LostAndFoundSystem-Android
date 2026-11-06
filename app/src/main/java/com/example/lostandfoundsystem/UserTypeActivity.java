package com.example.lostandfoundsystem;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** اختيار نوع الاستخدام: فقدت شيئًا / وجدت شيئًا. */
public class UserTypeActivity extends BaseActivity {

    private boolean lostSelected = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_type);

        if (savedInstanceState != null) lostSelected = savedInstanceState.getBoolean("lost", true);

        findViewById(R.id.layoutLost).setOnClickListener(v -> select(true));
        findViewById(R.id.layoutFound).setOnClickListener(v -> select(false));
        findViewById(R.id.btnContinue).setOnClickListener(v -> {
            Intent i = new Intent(this, HomeActivity.class);
            i.putExtra(HomeActivity.EXTRA_LOST_MODE, lostSelected);
            startActivity(i);
        });
        findViewById(R.id.btnUserTypeLogout).setOnClickListener(v ->
                new MaterialAlertDialogBuilder(this)
                        .setTitle("تسجيل الخروج")
                        .setMessage("هل تريد تسجيل الخروج من حسابك؟")
                        .setNegativeButton("إلغاء", null)
                        .setPositiveButton("خروج", (d, w) -> SessionRouter.logout(this))
                        .show());

        select(lostSelected);
        loadGreeting();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean("lost", lostSelected);
    }

    private void loadGreeting() {
        TextView greeting = findViewById(R.id.tvUserTypeGreeting);
        String uid = uidOrEmpty();
        if (uid.isEmpty()) return;
        Db.get().collection(Db.USERS).document(uid).get().addOnSuccessListener(doc -> {
            if (!isAlive()) return;
            String name = doc.getString("name");
            if (!isBlank(name)) greeting.setText("أهلًا " + name.trim() + " 👋");
        });
    }

    private void select(boolean lost) {
        lostSelected = lost;
        findViewById(R.id.layoutLost).setBackgroundResource(lost ? R.drawable.bg_option_lost_selected : R.drawable.bg_option);
        findViewById(R.id.layoutFound).setBackgroundResource(lost ? R.drawable.bg_option : R.drawable.bg_option_found_selected);

        findViewById(R.id.iconLost).setBackgroundResource(lost ? R.drawable.bg_circle_white : R.drawable.bg_circle_red_soft);
        findViewById(R.id.iconFound).setBackgroundResource(lost ? R.drawable.bg_circle_green_soft : R.drawable.bg_circle_white);

        styleRadio(findViewById(R.id.radioLost), lost, R.color.selection_red);
        styleRadio(findViewById(R.id.radioFound), !lost, R.color.found_green);
    }

    private void styleRadio(ImageView radio, boolean checked, int colorRes) {
        if (checked) {
            radio.setBackground(null);
            radio.setImageResource(R.drawable.ic_check_circle);
            ImageViewCompat.setImageTintList(radio, ColorStateList.valueOf(ContextCompat.getColor(this, colorRes)));
        } else {
            radio.setImageDrawable(null);
            radio.setBackgroundResource(R.drawable.bg_circle_border);
        }
    }
}
