package com.example.lostandfoundsystem;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.core.content.ContextCompat;

/** صفحة المساعدة: خطوات الاستخدام، معلومات المركز، والأسئلة الشائعة. */
public class HelpActivity extends BaseActivity {

    private static final String[][] FAQ = {
            {"كم يستغرق اعتماد البلاغ؟", "عادةً خلال يوم عمل واحد. ستصلك إشعارات عند تغيير حالة بلاغك."},
            {"هل يمكنني تعديل البلاغ بعد إرساله؟", "يمكنك حذف البلاغ طالما أنه قيد المراجعة ثم إنشاء بلاغ جديد بالبيانات الصحيحة."},
            {"وجدت غرضًا، ماذا أفعل؟", "أنشئ بلاغ \"وجدت شيئًا\" مع صورة ووصف دقيق. بعد المطابقة حدّد موعدًا لتسليمه لمركز المفقودات."},
            {"كيف أستلم غرضي؟", "عندما تصبح حالة بلاغك \"في مركز المفقودات\" توجّه للمركز خلال ساعات العمل مع بطاقتك الجامعية ورمز البلاغ."},
            {"كيف تُحسب نسبة التطابق؟", "يقارن النظام الفئة والاسم والموقع والتاريخ والوصف بين البلاغين، ولكل حقل وزن. الحقل غير المذكور في أحد البلاغين لا يُحسب له ولا عليه. 75% فأكثر تطابق عالٍ، و50% إلى 74% متوسط، و30% إلى 49% ضعيف."},
            {"ماذا لو لم يكن الغرض المطابق لي؟", "اضغط (هذا ليس غرضي) في تفاصيل بلاغك، أو أخبر موظف المركز عند التحقق. لن يُسلَّم لك الغرض، ويبقى في المركز لصاحبه، وتظهر لك الاقتراحات الأخرى فورًا."},
            {"لماذا تم رفض بلاغي؟", "قد يكون البلاغ غير مكتمل أو مكررًا. يظهر سبب الرفض في تفاصيل البلاغ وفي الإشعارات."}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);
        bindBack(R.id.btnBackHelp);

        step(R.id.helpStep1, "1", "أنشئ بلاغًا", "اختر \"فقدت شيئًا\" أو \"وجدت شيئًا\" وأدخل الوصف والمكان والتاريخ مع صورة إن وجدت.");
        step(R.id.helpStep2, "2", "المراجعة والاعتماد", "يراجع مسؤول النظام البلاغ ويعتمده ليبدأ البحث عن تطابق.");
        step(R.id.helpStep3, "3", "المطابقة", "يقارن النظام البلاغات المفقودة بالموجودة ويعتمد المسؤول المطابقة الصحيحة.");
        step(R.id.helpStep4, "4", "الاستلام من المركز", "يُسلَّم الغرض لمركز المفقودات ثم يستلمه صاحبه بالبطاقة الجامعية.");

        setInfoRow(R.id.helpCenterName, R.drawable.ic_center, "المركز", Db.CENTER_NAME);
        setInfoRow(R.id.helpCenterLocation, R.drawable.ic_location, "الموقع", Db.CENTER_LOCATION);
        setInfoRow(R.id.helpCenterHours, R.drawable.ic_clock, "ساعات العمل", Db.CENTER_HOURS);

        buildFaq();
    }

    private void step(@IdRes int id, String number, String title, String body) {
        View v = findViewById(id);
        ((TextView) v.findViewById(R.id.stepNumber)).setText(number);
        ((TextView) v.findViewById(R.id.stepTitle)).setText(title);
        ((TextView) v.findViewById(R.id.stepBody)).setText(body);
    }

    private void buildFaq() {
        LinearLayout container = findViewById(R.id.faqContainer);
        container.removeAllViews();
        for (int i = 0; i < FAQ.length; i++) {
            final String[] qa = FAQ[i];

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(0, dp(10), 0, dp(10));

            LinearLayout header = new LinearLayout(this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setMinimumHeight(dp(40));
            header.setBackgroundResource(resolveSelectable());
            header.setClickable(true);
            header.setFocusable(true);

            TextView q = new TextView(this);
            q.setText(qa[0]);
            q.setTextSize(15);
            q.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
            q.setTextColor(ContextCompat.getColor(this, R.color.text_dark));
            q.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            header.addView(q, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            ImageView arrow = new ImageView(this);
            arrow.setImageResource(R.drawable.ic_chevron);
            arrow.setRotation(-90f); // التطبيق RTL دائمًا: السهم المعكوس يشير للأسفل
            arrow.setColorFilter(ContextCompat.getColor(this, R.color.text_light));
            header.addView(arrow, new LinearLayout.LayoutParams(dp(20), dp(20)));

            TextView a = new TextView(this);
            a.setText(qa[1]);
            a.setTextSize(14);
            a.setLineSpacing(dp(3), 1f);
            a.setTypeface(android.graphics.Typeface.DEFAULT);
            a.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            a.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            a.setPadding(0, dp(6), 0, 0);
            a.setVisibility(View.GONE);

            header.setOnClickListener(v -> {
                boolean open = a.getVisibility() == View.VISIBLE;
                a.setVisibility(open ? View.GONE : View.VISIBLE);
                arrow.animate().rotation(open ? -90f : 90f).setDuration(150).start();
            });

            item.addView(header);
            item.addView(a);
            container.addView(item);

            if (i < FAQ.length - 1) {
                View divider = new View(this);
                divider.setBackgroundColor(ContextCompat.getColor(this, R.color.divider));
                container.addView(divider, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
            }
        }
    }

    private int resolveSelectable() {
        android.util.TypedValue tv = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        return tv.resourceId;
    }
}
