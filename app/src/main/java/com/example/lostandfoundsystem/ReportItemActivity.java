package com.example.lostandfoundsystem;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointBackward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.firestore.FieldValue;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** نموذج الإبلاغ عن مفقود / موجود على 3 خطوات. */
public class ReportItemActivity extends BaseActivity {

    public static final String EXTRA_IS_LOST = "isLost";

    private boolean isLost = true;
    private int step = 1;
    private String imageBase64 = "";
    private Uri imageUri;
    private boolean encoding = false;
    private boolean submitting = false;

    private TextInputLayout tilItemName, tilCategory, tilDescription, tilLocation, tilOtherLocation, tilDate;
    private TextInputEditText etItemName, etDescription, etOtherLocation, etDate, etDetails;
    private MaterialAutoCompleteTextView etCategory, etLocation;
    private MaterialButton btnNext, btnPrevious;
    private ImageView imagePreview;
    private View imagePlaceholder, btnRemoveImage;

    private final ActivityResultLauncher<PickVisualMediaRequest> pickImage =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), this::onImagePicked);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_item);

        isLost = getIntent().getBooleanExtra(EXTRA_IS_LOST, true);

        tilItemName = findViewById(R.id.tilItemName);
        tilCategory = findViewById(R.id.tilCategory);
        tilDescription = findViewById(R.id.tilDescription);
        tilLocation = findViewById(R.id.tilLocation);
        tilOtherLocation = findViewById(R.id.tilOtherLocation);
        tilDate = findViewById(R.id.tilDate);
        etItemName = findViewById(R.id.etItemName);
        etCategory = findViewById(R.id.etCategory);
        etDescription = findViewById(R.id.etDescription);
        etLocation = findViewById(R.id.etLocation);
        etOtherLocation = findViewById(R.id.etOtherLocation);
        etDate = findViewById(R.id.etDate);
        etDetails = findViewById(R.id.etDetails);
        btnNext = findViewById(R.id.btnNext);
        btnPrevious = findViewById(R.id.btnPrevious);
        imagePreview = findViewById(R.id.imagePreview);
        imagePlaceholder = findViewById(R.id.imagePlaceholder);
        btnRemoveImage = findViewById(R.id.btnRemoveImage);

        ((TextView) findViewById(R.id.stepTitle)).setText(isLost ? "الإبلاغ عن مفقود" : "الإبلاغ عن موجود");
        ((TextView) findViewById(R.id.step2Title)).setText(isLost ? "أين ومتى فقدته؟" : "أين ومتى وجدته؟");
        tilLocation.setHint(isLost ? "مكان الفقدان *" : "مكان العثور *");
        tilDate.setHint(isLost ? "تاريخ الفقدان *" : "تاريخ العثور *");

        etCategory.setAdapter(new NoFilterAdapter(this, Db.CATEGORIES));
        etLocation.setAdapter(new NoFilterAdapter(this, Db.LOCATIONS));
        etCategory.setOnItemClickListener((p, v, pos, id) -> tilCategory.setError(null));
        etLocation.setOnItemClickListener((p, v, pos, id) -> {
            tilLocation.setError(null);
            updateOtherLocation();
        });

        etDate.setOnClickListener(v -> pickDate());

        findViewById(R.id.btnPickImage).setOnClickListener(v -> pickImage.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));
        btnRemoveImage.setOnClickListener(v -> removeImage());

        btnNext.setOnClickListener(v -> next());
        btnPrevious.setOnClickListener(v -> previous());
        findViewById(R.id.btnBackReport).setOnClickListener(v -> previous());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { previous(); }
        });

        if (savedInstanceState != null) {
            step = savedInstanceState.getInt("step", 1);
            imageBase64 = savedInstanceState.getString("image", "");
            if (!imageBase64.isEmpty()) {
                imagePlaceholder.setVisibility(View.GONE);
                imagePreview.setVisibility(View.VISIBLE);
                btnRemoveImage.setVisibility(View.VISIBLE);
                ImageBase64Utils.loadInto(imagePreview, imageBase64, R.drawable.ic_image);
            }
        }
        updateOtherLocation();
        showStep();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("step", step);
        // الصورة المضغوطة صغيرة (عادة أقل من 300KB) لكن نتجنب حفظ الكبير جدًا
        if (imageBase64.length() < 400_000) out.putString("image", imageBase64);
    }

    @Override
    protected void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        updateOtherLocation();
        if (step == 3) fillReview();
    }

    // ---------------- الخطوات ----------------

    private void next() {
        if (submitting) return;
        if (step == 1) {
            if (!validateStep1()) return;
            step = 2;
        } else if (step == 2) {
            if (!validateStep2()) return;
            fillReview();
            step = 3;
        } else {
            submit();
            return;
        }
        showStep();
    }

    private void previous() {
        if (submitting) return;
        if (step > 1) {
            step--;
            showStep();
            return;
        }
        if (hasData()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("إلغاء البلاغ")
                    .setMessage("سيتم فقدان البيانات التي أدخلتها. هل تريد الخروج؟")
                    .setNegativeButton("متابعة التعبئة", null)
                    .setPositiveButton("خروج", (d, w) -> finish())
                    .show();
        } else {
            finish();
        }
    }

    private boolean hasData() {
        return !text(etItemName).isEmpty() || !text(etDescription).isEmpty()
                || !text(etCategory).isEmpty() || !imageBase64.isEmpty();
    }

    private void showStep() {
        findViewById(R.id.formStep1).setVisibility(step == 1 ? View.VISIBLE : View.GONE);
        findViewById(R.id.formStep2).setVisibility(step == 2 ? View.VISIBLE : View.GONE);
        findViewById(R.id.formStep3).setVisibility(step == 3 ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.stepIndicator)).setText(step + " / 3");

        btnPrevious.setText(step == 1 ? "إلغاء" : "السابق");
        btnNext.setText(step == 3 ? "إرسال البلاغ" : "التالي");
        btnNext.setIconResource(step == 3 ? R.drawable.ic_check : 0);
        btnNext.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(this, step == 3 && !isLost ? R.color.found_green : R.color.ksu_blue)));

        int accent = isLost ? R.drawable.bg_circle_blue : R.drawable.bg_circle_green;
        int accentColor = ContextCompat.getColor(this, isLost ? R.color.ksu_blue : R.color.found_green);
        styleCircle(findViewById(R.id.stepCircle1), 1, accent);
        styleCircle(findViewById(R.id.stepCircle2), 2, accent);
        styleCircle(findViewById(R.id.stepCircle3), 3, accent);
        styleLabel(findViewById(R.id.stepLabel1), 1, accentColor);
        styleLabel(findViewById(R.id.stepLabel2), 2, accentColor);
        styleLabel(findViewById(R.id.stepLabel3), 3, accentColor);
        ColorStateList on = ColorStateList.valueOf(accentColor);
        ColorStateList off = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.border));
        findViewById(R.id.stepLine1).setBackgroundTintList(step > 1 ? on : off);
        findViewById(R.id.stepLine2).setBackgroundTintList(step > 2 ? on : off);

        NestedScrollView scroll = findViewById(R.id.reportScroll);
        scroll.post(() -> scroll.scrollTo(0, 0));
    }

    private void styleCircle(TextView c, int index, int accentBg) {
        if (index < step) {
            c.setBackgroundResource(accentBg);
            c.setText("✓");
            c.setTextColor(ContextCompat.getColor(this, R.color.white));
        } else if (index == step) {
            c.setBackgroundResource(accentBg);
            c.setText(String.valueOf(index));
            c.setTextColor(ContextCompat.getColor(this, R.color.white));
        } else {
            c.setBackgroundResource(R.drawable.bg_circle_border);
            c.setText(String.valueOf(index));
            c.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        }
    }

    private void styleLabel(TextView l, int index, int accentColor) {
        l.setTextColor(index <= step ? accentColor : ContextCompat.getColor(this, R.color.text_light));
    }

    // ---------------- التحقق ----------------

    private boolean validateStep1() {
        boolean ok = true;
        String name = text(etItemName);
        if (name.length() < 2) {
            tilItemName.setError("أدخل اسم الغرض (حرفان على الأقل)");
            ok = false;
        } else tilItemName.setError(null);

        if (text(etCategory).isEmpty()) {
            tilCategory.setError("اختر الفئة");
            ok = false;
        } else tilCategory.setError(null);

        if (text(etDescription).length() < 5) {
            tilDescription.setError("أضف وصفًا مختصرًا (اللون، العلامة، المحتوى…)");
            ok = false;
        } else tilDescription.setError(null);

        if (!ok) toast("يرجى إكمال الحقول المطلوبة");
        return ok;
    }

    private boolean validateStep2() {
        boolean ok = true;
        String loc = text(etLocation);
        if (loc.isEmpty()) {
            tilLocation.setError("اختر الموقع");
            ok = false;
        } else tilLocation.setError(null);

        if (Db.OTHER.equals(loc) && text(etOtherLocation).length() < 2) {
            tilOtherLocation.setError("اكتب اسم الموقع");
            ok = false;
        } else tilOtherLocation.setError(null);

        if (text(etDate).isEmpty()) {
            tilDate.setError("اختر التاريخ");
            ok = false;
        } else tilDate.setError(null);

        if (!ok) toast("يرجى إكمال الحقول المطلوبة");
        return ok;
    }

    private void updateOtherLocation() {
        boolean other = Db.OTHER.equals(text(etLocation));
        tilOtherLocation.setVisibility(other ? View.VISIBLE : View.GONE);
        if (!other) tilOtherLocation.setError(null);
    }

    private String finalLocation() {
        String loc = text(etLocation);
        return Db.OTHER.equals(loc) ? text(etOtherLocation) : loc;
    }

    // ---------------- التاريخ والصورة ----------------

    private void pickDate() {
        CalendarConstraints constraints = new CalendarConstraints.Builder()
                .setValidator(DateValidatorPointBackward.now())
                .build();
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(isLost ? "تاريخ الفقدان" : "تاريخ العثور")
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .setCalendarConstraints(constraints)
                .build();
        picker.addOnPositiveButtonClickListener(sel -> {
            SimpleDateFormat f = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            etDate.setText(f.format(new Date(sel)));
            tilDate.setError(null);
        });
        picker.show(getSupportFragmentManager(), "date");
    }

    private void onImagePicked(Uri uri) {
        if (uri == null) return;
        imageUri = uri;
        imagePlaceholder.setVisibility(View.GONE);
        imagePreview.setVisibility(View.VISIBLE);
        btnRemoveImage.setVisibility(View.VISIBLE);
        ImageBase64Utils.showLocal(imagePreview, uri);
        encoding = true;
        final Uri requested = uri;
        ImageBase64Utils.encodeAsync(this, uri, new ImageBase64Utils.EncodeCallback() {
            @Override public void onSuccess(String base64) {
                if (requested != imageUri) return; // اختار صورة أخرى أثناء الضغط
                encoding = false;
                imageBase64 = base64;
            }

            @Override public void onError(Exception error) {
                if (requested != imageUri) return;
                encoding = false;
                if (isAlive()) {
                    toast("تعذر قراءة الصورة، جرّب صورة أخرى");
                    removeImage();
                }
            }
        });
    }

    private void removeImage() {
        imageUri = null;
        imageBase64 = "";
        encoding = false;
        imagePreview.setImageDrawable(null);
        imagePreview.setVisibility(View.GONE);
        btnRemoveImage.setVisibility(View.GONE);
        imagePlaceholder.setVisibility(View.VISIBLE);
    }

    // ---------------- المراجعة والإرسال ----------------

    private void fillReview() {
        ImageView review = findViewById(R.id.reviewImage);
        if (imageBase64.isEmpty() && imageUri != null) ImageBase64Utils.showLocal(review, imageUri);
        else ImageBase64Utils.loadInto(review, imageBase64, isLost ? R.drawable.ic_icon_lost : R.drawable.ic_icon_found);
        Status.applyTypeChip(findViewById(R.id.reviewType), isLost);
        ((TextView) findViewById(R.id.reviewItemName)).setText(text(etItemName));
        ((TextView) findViewById(R.id.reviewCategory)).setText(text(etCategory));
        ((TextView) findViewById(R.id.reviewDescription)).setText(text(etDescription));
        setInfoRow(R.id.reviewLocation, R.drawable.ic_location, isLost ? "مكان الفقدان" : "مكان العثور", finalLocation());
        setInfoRow(R.id.reviewDate, R.drawable.ic_calendar, isLost ? "تاريخ الفقدان" : "تاريخ العثور", text(etDate));
        boolean hasDetails = !text(etDetails).isEmpty();
        findViewById(R.id.reviewDetails).setVisibility(hasDetails ? View.VISIBLE : View.GONE);
        findViewById(R.id.reviewDetailsDivider).setVisibility(hasDetails ? View.VISIBLE : View.GONE);
        if (hasDetails) setInfoRow(R.id.reviewDetails, R.drawable.ic_info, "تفاصيل إضافية", text(etDetails));
    }

    private void submit() {
        if (encoding) {
            toast("جارٍ تجهيز الصورة، لحظات…");
            return;
        }
        String uid = uidOrEmpty();
        if (uid.isEmpty()) {
            SessionRouter.open(this, LoginActivity.class);
            return;
        }
        setSubmitting(true);

        Map<String, Object> data = new HashMap<>();
        data.put("userId", uid);
        data.put("type", isLost ? "lost" : "found");
        data.put("itemName", text(etItemName));
        data.put("category", text(etCategory));
        data.put("description", text(etDescription));
        data.put("location", finalLocation());
        data.put("date", text(etDate));
        data.put("details", text(etDetails));
        data.put("status", Status.PENDING);
        data.put("imageBase64", imageBase64);
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("updatedAt", FieldValue.serverTimestamp());

        final String collection = isLost ? Db.LOST : Db.FOUND;
        Db.get().collection(collection).add(data)
                .addOnSuccessListener(ref -> {
                    if (!isAlive()) return;
                    Intent i = new Intent(this, SubmissionSuccessActivity.class);
                    i.putExtra(SubmissionSuccessActivity.EXTRA_ID, ref.getId());
                    i.putExtra(SubmissionSuccessActivity.EXTRA_COLLECTION, collection);
                    i.putExtra(SubmissionSuccessActivity.EXTRA_NAME, text(etItemName));
                    i.putExtra(SubmissionSuccessActivity.EXTRA_DATE, text(etDate));
                    startActivity(i);
                    finish();
                })
                .addOnFailureListener(e -> {
                    if (!isAlive()) return;
                    setSubmitting(false);
                    toast("تعذر إرسال البلاغ. تحقق من الاتصال وحاول مجددًا.");
                });
    }

    private void setSubmitting(boolean value) {
        submitting = value;
        btnNext.setClickable(!value);
        btnPrevious.setEnabled(!value);
        btnNext.setText(value ? "جارٍ الإرسال…" : "إرسال البلاغ");
    }

    private static String text(TextView t) {
        return t.getText() == null ? "" : t.getText().toString().trim();
    }
}
