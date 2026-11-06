package com.example.lostandfoundsystem;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

/** ربط بيانات البلاغ ببطاقة item_report الموحدة (تستخدم في كل القوائم). */
public final class ReportCard {
    private ReportCard() {}

    public static View inflate(ViewGroup parent) {
        return LayoutInflater.from(parent.getContext()).inflate(R.layout.item_report, parent, false);
    }

    public static void bind(View v, Report r, boolean showType) {
        ImageView image = v.findViewById(R.id.reportImage);
        TextView type = v.findViewById(R.id.reportType);
        TextView status = v.findViewById(R.id.reportStatus);
        TextView title = v.findViewById(R.id.reportTitle);
        TextView location = v.findViewById(R.id.reportLocation);
        TextView date = v.findViewById(R.id.reportDate);

        ImageBase64Utils.loadInto(image, r.imageBase64, r.placeholder());
        type.setVisibility(showType ? View.VISIBLE : View.GONE);
        if (showType) Status.applyTypeChip(type, r.lost);
        Status.applyChip(status, r.status);
        title.setText(r.displayName());

        String loc = r.location == null ? "" : r.location.trim();
        location.setText(loc.isEmpty() ? (r.category.isEmpty() ? "الموقع غير محدد" : r.category) : loc);
        String d = r.date == null ? "" : r.date.trim();
        date.setText(d.isEmpty() ? r.code() : d + "  •  " + r.code());

        MaterialButton action = v.findViewById(R.id.reportAction);
        action.setVisibility(View.GONE);
        action.setOnClickListener(null);
    }

    public static void setAction(View v, String label, int styleColorRes, View.OnClickListener l) {
        MaterialButton action = v.findViewById(R.id.reportAction);
        action.setVisibility(View.VISIBLE);
        action.setText(label);
        action.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(v.getContext(), styleColorRes)));
        action.setOnClickListener(l);
    }
}
