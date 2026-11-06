package com.example.lostandfoundsystem;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * الأساس المشترك لكل الشاشات:
 * - عرض من الحافة للحافة مع حساب مساحة شريط الحالة وشريط التنقل (مطلوب في Android 15+)
 * - اتجاه من اليمين لليسار
 * - أدوات مساعدة صغيرة
 */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SystemBarStyle style = useLightSystemBarIcons()
                ? SystemBarStyle.dark(Color.TRANSPARENT)
                : SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT);
        EdgeToEdge.enable(this, style, style);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    }

    /** الشاشات ذات الخلفية الداكنة (مثل شاشة البداية) ترجع true. */
    protected boolean useLightSystemBarIcons() {
        return false;
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        ViewGroup content = findViewById(android.R.id.content);
        if (content != null && content.getChildCount() > 0) {
            applySystemBarInsets(content.getChildAt(0));
        }
    }

    private void applySystemBarInsets(View root) {
        final int left = root.getPaddingLeft();
        final int top = root.getPaddingTop();
        final int right = root.getPaddingRight();
        final int bottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(left + bars.left, top + bars.top, right + bars.right,
                    bottom + Math.max(bars.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(root);
    }

    protected void bindBack(@IdRes int id) {
        View v = findViewById(id);
        if (v != null) v.setOnClickListener(x -> getOnBackPressedDispatcher().onBackPressed());
    }

    @Nullable
    protected FirebaseUser currentUser() {
        return FirebaseAuth.getInstance().getCurrentUser();
    }

    protected String uidOrEmpty() {
        FirebaseUser u = currentUser();
        return u == null ? "" : u.getUid();
    }

    protected void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    protected int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    protected boolean isAlive() {
        return !isFinishing() && !isDestroyed();
    }

    // ---------- مساعدات العناصر المشتركة ----------

    /** تعبئة بطاقة إحصائية (view_stat) مضمّنة بمعرف include. */
    protected void setStat(@IdRes int includeId, String value, String label, int colorRes) {
        View root = findViewById(includeId);
        if (root == null) return;
        TextView v = root.findViewById(R.id.statValue);
        TextView l = root.findViewById(R.id.statLabel);
        v.setText(value);
        v.setTextColor(ContextCompat.getColor(this, colorRes));
        l.setText(label);
    }

    /** تعبئة صف معلومات (view_info_row). */
    protected void setInfoRow(@IdRes int includeId, @DrawableRes int icon, String label, String value) {
        View root = findViewById(includeId);
        if (root == null) return;
        ((ImageView) root.findViewById(R.id.infoIcon)).setImageResource(icon);
        ((TextView) root.findViewById(R.id.infoLabel)).setText(label);
        ((TextView) root.findViewById(R.id.infoValue)).setText(
                value == null || value.trim().isEmpty() ? "غير محدد" : value.trim());
    }

    /** تبويب مقسّم (segment): تمييز العنصر المختار. */
    protected void styleSegment(TextView tab, boolean selected, @DrawableRes int selectedBg) {
        if (selected) {
            tab.setBackgroundResource(selectedBg);
            tab.setTextColor(ContextCompat.getColor(this, R.color.white));
        } else {
            tab.setBackground(null);
            tab.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        }
    }

    protected void copyToClipboard(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("code", text));
            // Android 13+ يعرض تأكيدًا تلقائيًا
            if (android.os.Build.VERSION.SDK_INT < 33) toast("تم نسخ الرمز: " + text);
        }
    }

    /** الحرف الأول من الاسم لعرضه داخل الصورة الرمزية. */
    public static String initials(String name) {
        if (name == null) return "م";
        String t = name.trim();
        return t.isEmpty() ? "م" : t.substring(0, t.offsetByCodePoints(0, 1));
    }

    public static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    protected String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "1.0";
        }
    }
}
