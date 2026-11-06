package com.example.lostandfoundsystem;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;

/** شاشة نجاح إرسال البلاغ. */
public class SubmissionSuccessActivity extends BaseActivity {

    public static final String EXTRA_ID = "documentId";
    public static final String EXTRA_COLLECTION = "collection";
    public static final String EXTRA_NAME = "itemName";
    public static final String EXTRA_DATE = "date";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_submission_success);

        final String id = getIntent().getStringExtra(EXTRA_ID);
        final String collection = getIntent().getStringExtra(EXTRA_COLLECTION);
        final boolean lost = !Db.FOUND.equals(collection);
        String name = getIntent().getStringExtra(EXTRA_NAME);
        String date = getIntent().getStringExtra(EXTRA_DATE);
        final String code = Report.codeFor(id);

        View icon = findViewById(R.id.successIcon);
        icon.setScaleX(0f);
        icon.setScaleY(0f);
        icon.animate().scaleX(1f).scaleY(1f).setStartDelay(150).setDuration(500)
                .setInterpolator(new OvershootInterpolator()).start();

        ((TextView) findViewById(R.id.tvSuccessTitle)).setText("تم إرسال البلاغ بنجاح");
        ((TextView) findViewById(R.id.tvSuccessSubtitle)).setText(lost
                ? "سنبحث عن غرضك ونبلغك فور العثور على تطابق."
                : "شكرًا لأمانتك! سنبلغك عند العثور على صاحب الغرض.");
        ((TextView) findViewById(R.id.tvReportCode)).setText(code);
        findViewById(R.id.btnCopyCode).setOnClickListener(v -> copyToClipboard(code));

        setInfoRow(R.id.rowSuccessItem, R.drawable.ic_box, "الغرض", name);
        setInfoRow(R.id.rowSuccessDate, R.drawable.ic_calendar, lost ? "تاريخ الفقدان" : "تاريخ العثور", date);
        Status.applyChip(findViewById(R.id.tvSuccessStatus), Status.PENDING);

        ((TextView) findViewById(R.id.tvNextSteps)).setText(lost
                ? "1. يراجع مسؤول النظام بلاغك ويعتمده.\n2. يتم البحث عن تطابق مع الأغراض الموجودة.\n3. تصلك إشعارات عند المطابقة ووصول الغرض للمركز.\n4. تستلم غرضك من مركز المفقودات بالبطاقة الجامعية."
                : "1. يراجع مسؤول النظام البلاغ ويعتمده.\n2. عند مطابقته مع صاحبه يصلك إشعار.\n3. تحدد موعدًا لتسليم الغرض إلى مركز المفقودات.\n4. يستلمه صاحبه من المركز.");

        findViewById(R.id.btnSuccessDetails).setOnClickListener(v -> {
            if (BaseActivity.isBlank(id)) return;
            Intent i = new Intent(this, ItemDetailsActivity.class);
            i.putExtra(ItemDetailsActivity.EXTRA_ID, id);
            i.putExtra(ItemDetailsActivity.EXTRA_COLLECTION, lost ? Db.LOST : Db.FOUND);
            startActivity(i);
            finish();
        });
        findViewById(R.id.btnSuccessHome).setOnClickListener(v -> finish());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { finish(); }
        });
    }
}
