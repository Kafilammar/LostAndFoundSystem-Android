package com.example.lostandfoundsystem;

import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ProgressBar;

import androidx.activity.OnBackPressedCallback;

/** شاشة البداية: حركة بسيطة ثم توجيه المستخدم (دخول تلقائي إن كان مسجلاً). */
public class SplashActivity extends BaseActivity {

    private static final long DURATION = 1400;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean routed = false;

    @Override
    protected boolean useLightSystemBarIcons() {
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        View logo = findViewById(R.id.imgLogo);
        logo.setAlpha(0f);
        logo.setScaleX(0.85f);
        logo.setScaleY(0.85f);
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(600)
                .setInterpolator(new DecelerateInterpolator()).start();

        ProgressBar bar = findViewById(R.id.progressBar);
        ObjectAnimator anim = ObjectAnimator.ofInt(bar, "progress", 0, 100);
        anim.setDuration(DURATION);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.start();

        // لا نسمح بالرجوع أثناء التحميل
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { /* تجاهل */ }
        });

        handler.postDelayed(this::goNext, DURATION + 150);
    }

    private void goNext() {
        if (routed || !isAlive()) return;
        routed = true;
        if (currentUser() == null) {
            SessionRouter.open(this, LoginActivity.class);
        } else {
            SessionRouter.route(this, () -> {
                // عند فشل الاتصال نعيد المستخدم لشاشة الدخول حتى لا يعلق
                if (isAlive() && !isFinishing()) SessionRouter.open(this, LoginActivity.class);
            });
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
