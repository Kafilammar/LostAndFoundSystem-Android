package com.example.lostandfoundsystem;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

/** تعديل الاسم ورقم الجوال (الرقم الجامعي غير قابل للتعديل). */
public class EditProfileActivity extends BaseActivity {

    private TextInputLayout tilName, tilPhone;
    private TextInputEditText etName, etPhone;
    private MaterialButton btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        bindBack(R.id.btnBackEditProfile);
        findViewById(R.id.btnCancelEditProfile).setOnClickListener(v -> finish());

        tilName = findViewById(R.id.tilEditName);
        tilPhone = findViewById(R.id.tilEditPhone);
        etName = findViewById(R.id.etEditName);
        etPhone = findViewById(R.id.etEditPhone);
        btnSave = findViewById(R.id.btnSaveProfile);
        TextView avatar = findViewById(R.id.tvEditAvatar);

        etName.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                tilName.setError(null);
                avatar.setText(initials(s.toString()));
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        FirebaseUser user = currentUser();
        if (user == null) {
            SessionRouter.open(this, LoginActivity.class);
            return;
        }
        ((TextView) findViewById(R.id.tvEditUniversityId)).setText(Db.universityIdFromEmail(user.getEmail()));

        if (savedInstanceState == null) {
            Db.get().collection(Db.USERS).document(user.getUid()).get().addOnSuccessListener(doc -> {
                if (!isAlive()) return;
                String name = doc.getString("name");
                String phone = doc.getString("phone");
                String uni = doc.getString("universityId");
                if (!isBlank(name)) etName.setText(name);
                if (!isBlank(phone)) etPhone.setText(phone);
                if (!isBlank(uni)) ((TextView) findViewById(R.id.tvEditUniversityId)).setText(uni);
            });
        }

        btnSave.setOnClickListener(v -> save(user.getUid()));
    }

    private void save(String uid) {
        String name = etName.getText() == null ? "" : etName.getText().toString().trim();
        String phone = etPhone.getText() == null ? "" : etPhone.getText().toString().trim().replace(" ", "");

        boolean ok = true;
        if (name.length() < 3) {
            tilName.setError("أدخل اسمك الكامل (3 أحرف على الأقل)");
            ok = false;
        }
        if (!phone.isEmpty() && !phone.matches("^(\\+?966|0)?5\\d{8}$")) {
            tilPhone.setError("رقم جوال سعودي غير صحيح (مثال: 05XXXXXXXX)");
            ok = false;
        } else {
            tilPhone.setError(null);
        }
        if (!ok) return;

        btnSave.setClickable(false);
        btnSave.setText("جارٍ الحفظ…");
        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        data.put("phone", phone);
        data.put("updatedAt", FieldValue.serverTimestamp());
        Db.get().collection(Db.USERS).document(uid).set(data, SetOptions.merge())
                .addOnSuccessListener(x -> {
                    if (!isAlive()) return;
                    toast("تم حفظ التعديلات");
                    finish();
                })
                .addOnFailureListener(e -> {
                    if (!isAlive()) return;
                    btnSave.setClickable(true);
                    btnSave.setText("حفظ التغييرات");
                    toast("تعذر الحفظ. تحقق من الاتصال.");
                });
    }
}
