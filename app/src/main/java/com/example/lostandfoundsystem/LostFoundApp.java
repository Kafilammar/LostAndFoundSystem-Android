package com.example.lostandfoundsystem;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

/**
 * يثبّت لغة التطبيق على العربية حتى تظهر الواجهات والنوافذ المنبثقة ومنتقي التاريخ
 * من اليمين إلى اليسار حتى لو كانت لغة الجهاز الإنجليزية.
 */
public class LostFoundApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        LocaleListCompat current = AppCompatDelegate.getApplicationLocales();
        if (current.isEmpty() || !"ar".equals(current.get(0).getLanguage())) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ar"));
        }
    }
}
