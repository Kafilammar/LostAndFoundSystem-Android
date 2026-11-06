package com.example.lostandfoundsystem;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.google.firebase.firestore.FieldValue;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** يحدد الشخص الذي وجد الغرض موعد تسليمه لمركز المفقودات. */
public class DeliveryConfirmationActivity extends BaseActivity {

    public static final String EXTRA_ID = "documentId";

    private static final int OPEN_HOUR = 8, CLOSE_HOUR = 16;

    private String docId;
    private Report report;
    private long pickedDayUtc = 0;
    private TextInputLayout tilDate, tilTime;
    private TextInputEditText etDate, etTime;
    private MaterialButton btnConfirm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_delivery_confirmation);

        docId = getIntent().getStringExtra(EXTRA_ID);
        if (isBlank(docId)) {
            finish();
            return;
        }
        bindBack(R.id.btnBackDelivery);
        tilDate = findViewById(R.id.tilAppointmentDate);
        tilTime = findViewById(R.id.tilAppointmentTime);
        etDate = findViewById(R.id.etAppointmentDate);
        etTime = findViewById(R.id.etAppointmentTime);
        btnConfirm = findViewById(R.id.btnDeliveryConfirm);

        setInfoRow(R.id.deliveryRowCenter, R.drawable.ic_center, "المركز", Db.CENTER_NAME);
        setInfoRow(R.id.deliveryRowLocation, R.drawable.ic_location, "الموقع", Db.CENTER_LOCATION);
        setInfoRow(R.id.deliveryRowHours, R.drawable.ic_clock, "ساعات العمل", Db.CENTER_HOURS);

        etDate.setOnClickListener(v -> pickDate());
        etTime.setOnClickListener(v -> pickTime());
        btnConfirm.setOnClickListener(v -> save());
        load();
    }

    private void load() {
        Db.get().collection(Db.FOUND).document(docId).get()
                .addOnSuccessListener(doc -> {
                    if (!isAlive()) return;
                    if (!doc.exists()) {
                        toast("البلاغ غير موجود");
                        finish();
                        return;
                    }
                    report = Report.from(doc);
                    if (!report.userId.equals(uidOrEmpty()) || !Status.isMatched(report.status)) {
                        toast("لا يمكن تحديد موعد لهذا البلاغ حاليًا");
                        finish();
                        return;
                    }
                    bind();
                })
                .addOnFailureListener(e -> {
                    if (!isAlive()) return;
                    toast(getString(R.string.no_connection));
                    finish();
                });
    }

    private void bind() {
        findViewById(R.id.deliveryProgress).setVisibility(View.GONE);
        findViewById(R.id.deliveryContent).setVisibility(View.VISIBLE);
        ImageBase64Utils.loadInto(findViewById(R.id.ivDeliveryItem), report.imageBase64, report.placeholder());
        ((TextView) findViewById(R.id.tvDeliveryItemName)).setText(report.displayName());
        ((TextView) findViewById(R.id.tvDeliveryCode)).setText("رمز البلاغ: " + report.code());
        Status.applyChip(findViewById(R.id.tvDeliveryStatus), report.status);
        if (!isBlank(report.appointmentDate)) {
            etDate.setText(report.appointmentDate);
            btnConfirm.setText("تحديث موعد التسليم");
        }
        if (!isBlank(report.appointmentTime)) etTime.setText(report.appointmentTime);
    }

    private void pickDate() {
        CalendarConstraints c = new CalendarConstraints.Builder()
                .setValidator(DateValidatorPointForward.from(MaterialDatePicker.todayInUtcMilliseconds()))
                .build();
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("تاريخ التسليم")
                .setCalendarConstraints(c)
                .build();
        picker.addOnPositiveButtonClickListener(sel -> {
            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            cal.setTimeInMillis(sel);
            int dow = cal.get(Calendar.DAY_OF_WEEK);
            if (dow == Calendar.FRIDAY || dow == Calendar.SATURDAY) {
                tilDate.setError("المركز مغلق يومي الجمعة والسبت");
                return;
            }
            pickedDayUtc = sel;
            SimpleDateFormat f = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            etDate.setText(f.format(new Date(sel)));
            tilDate.setError(null);
        });
        picker.show(getSupportFragmentManager(), "date");
    }

    private void pickTime() {
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_12H)
                .setHour(10)
                .setMinute(0)
                .setTitleText("وقت التسليم")
                .build();
        picker.addOnPositiveButtonClickListener(v -> {
            int h = picker.getHour(), m = picker.getMinute();
            if (h < OPEN_HOUR || h > CLOSE_HOUR || (h == CLOSE_HOUR && m > 0)) {
                tilTime.setError("اختر وقتًا بين 8:00 ص و 4:00 م");
                return;
            }
            int h12 = h % 12 == 0 ? 12 : h % 12;
            etTime.setText(String.format(Locale.US, "%02d:%02d %s", h12, m, h < 12 ? "ص" : "م"));
            tilTime.setError(null);
        });
        picker.show(getSupportFragmentManager(), "time");
    }

    private void save() {
        String date = etDate.getText() == null ? "" : etDate.getText().toString().trim();
        String time = etTime.getText() == null ? "" : etTime.getText().toString().trim();
        boolean ok = true;
        if (date.isEmpty()) { tilDate.setError("اختر تاريخ التسليم"); ok = false; }
        if (time.isEmpty()) { tilTime.setError("اختر وقت التسليم"); ok = false; }
        if (!ok || report == null) return;

        btnConfirm.setClickable(false);
        btnConfirm.setText("جارٍ الحفظ…");
        Map<String, Object> data = new HashMap<>();
        data.put("appointmentDate", date);
        data.put("appointmentTime", time);
        data.put("updatedAt", FieldValue.serverTimestamp());
        Db.get().collection(Db.FOUND).document(docId).update(data)
                .addOnSuccessListener(x -> {
                    if (!isAlive()) return;
                    toast("تم تحديد الموعد. شكرًا لك! سيؤكد المركز الاستلام عند وصولك.");
                    finish();
                })
                .addOnFailureListener(e -> {
                    if (!isAlive()) return;
                    btnConfirm.setClickable(true);
                    btnConfirm.setText("تأكيد موعد التسليم");
                    toast("تعذر حفظ الموعد. تحقق من الاتصال.");
                });
    }
}
