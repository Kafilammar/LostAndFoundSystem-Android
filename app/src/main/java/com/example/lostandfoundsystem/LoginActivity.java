package com.example.lostandfoundsystem;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;

public class LoginActivity extends BaseActivity {

    private TextInputLayout tilId, tilPassword;
    private TextInputEditText etId, etPassword;
    private MaterialButton btnLogin;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        tilId = findViewById(R.id.tilUniversityId);
        tilPassword = findViewById(R.id.tilPassword);
        etId = findViewById(R.id.etUniversityId);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        progress = findViewById(R.id.loginProgress);
        progress.setVisibility(View.GONE);

        ((TextView) findViewById(R.id.tvVersion)).setText("الإصدار " + versionName());

        clearErrorOnType(etId, tilId);
        clearErrorOnType(etPassword, tilPassword);

        etPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO) {
                attemptLogin();
                return true;
            }
            return false;
        });

        btnLogin.setOnClickListener(v -> attemptLogin());
        findViewById(R.id.txtForgotPassword).setOnClickListener(v -> forgotPassword());
    }

    private void clearErrorOnType(TextInputEditText et, TextInputLayout til) {
        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { til.setError(null); }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private String emailFromInput() {
        String id = etId.getText() == null ? "" : etId.getText().toString().trim();
        if (id.isEmpty()) return "";
        return id.contains("@") ? id : id + Db.EMAIL_DOMAIN;
    }

    private void attemptLogin() {
        String id = etId.getText() == null ? "" : etId.getText().toString().trim();
        String password = etPassword.getText() == null ? "" : etPassword.getText().toString();

        boolean ok = true;
        if (id.isEmpty()) {
            tilId.setError("أدخل الرقم الجامعي");
            ok = false;
        }
        if (password.isEmpty()) {
            tilPassword.setError("أدخل كلمة المرور");
            ok = false;
        } else if (password.length() < 6) {
            tilPassword.setError("كلمة المرور 6 أحرف على الأقل");
            ok = false;
        }
        if (!ok) return;

        setLoading(true);
        FirebaseAuth.getInstance().signInWithEmailAndPassword(emailFromInput(), password)
                .addOnSuccessListener(r -> SessionRouter.route(this, () -> setLoading(false)))
                .addOnFailureListener(e -> {
                    setLoading(false);
                    if (e instanceof FirebaseAuthInvalidUserException
                            || e instanceof FirebaseAuthInvalidCredentialsException) {
                        tilPassword.setError("الرقم الجامعي أو كلمة المرور غير صحيحة");
                    } else if (e instanceof FirebaseNetworkException) {
                        toast(getString(R.string.no_connection));
                    } else if (e instanceof FirebaseTooManyRequestsException) {
                        toast("محاولات كثيرة. انتظر قليلًا ثم حاول مجددًا.");
                    } else {
                        toast("تعذر تسجيل الدخول. حاول مرة أخرى.");
                    }
                });
    }

    private void setLoading(boolean loading) {
        if (!isAlive()) return;
        btnLogin.setClickable(!loading);
        btnLogin.setText(loading ? "" : "تسجيل الدخول");
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        etId.setEnabled(!loading);
        etPassword.setEnabled(!loading);
    }

    private void forgotPassword() {
        String email = emailFromInput();
        if (email.isEmpty()) {
            tilId.setError("أدخل الرقم الجامعي أولًا لإرسال رابط إعادة التعيين");
            etId.requestFocus();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("إعادة تعيين كلمة المرور")
                .setMessage("سيتم إرسال رابط إعادة التعيين إلى:\n" + email)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("إرسال", (d, w) ->
                        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                                .addOnSuccessListener(x -> toast("تم إرسال الرابط إلى بريدك الجامعي"))
                                .addOnFailureListener(e -> toast("تعذر إرسال الرابط. تأكد من الرقم الجامعي.")))
                .show();
    }
}
