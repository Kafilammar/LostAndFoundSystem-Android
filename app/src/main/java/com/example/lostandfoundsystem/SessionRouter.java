package com.example.lostandfoundsystem;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;

import java.util.HashMap;
import java.util.Map;

/** يحدد الشاشة الرئيسية المناسبة حسب دور المستخدم (طالب / مسؤول / مركز). */
public final class SessionRouter {
    private SessionRouter() {}

    public interface Done { void onDone(); }

    public static void route(Activity a, Done onFinished) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            open(a, LoginActivity.class);
            return;
        }
        String uid = user.getUid();
        String email = user.getEmail() == null ? "" : user.getEmail();
        Db.get().collection(Db.USERS).document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        Map<String, Object> data = new HashMap<>();
                        data.put("name", "");
                        data.put("universityId", Db.universityIdFromEmail(email));
                        data.put("email", email);
                        data.put("role", "student");
                        data.put("createdAt", FieldValue.serverTimestamp());
                        Db.get().collection(Db.USERS).document(uid).set(data);
                        open(a, UserTypeActivity.class);
                    } else {
                        String role = doc.getString("role");
                        if (Roles.isAdmin(role)) open(a, AdminDashboardActivity.class);
                        else if (Roles.isCenter(role)) open(a, CenterDashboardActivity.class);
                        else open(a, UserTypeActivity.class);
                    }
                    if (onFinished != null) onFinished.onDone();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(a, a.getString(R.string.no_connection), Toast.LENGTH_LONG).show();
                    if (onFinished != null) onFinished.onDone();
                });
    }

    public static void open(Activity a, Class<?> target) {
        Intent i = new Intent(a, target);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        a.startActivity(i);
        a.finish();
    }

    public static void logout(Activity a) {
        FirebaseAuth.getInstance().signOut();
        open(a, LoginActivity.class);
    }
}
