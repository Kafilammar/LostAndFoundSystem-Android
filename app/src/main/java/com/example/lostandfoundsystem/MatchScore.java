package com.example.lostandfoundsystem;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * حساب نسبة التطابق بين بلاغ مفقود وبلاغ موجود.
 *
 * <p>لكل حقل وزن ثابت، ونسبة تشابه بين 0 و 1:
 * <pre>
 *   الفئة        25   (1 إذا تساوت، 0 إذا اختلفت)
 *   اسم الغرض    25   (الكلمات المشتركة ÷ كلمات الاسم الأقصر)
 *   الموقع       20   (1 إذا تطابق، وإلا الكلمات المشتركة ÷ كلمات الموقع الأقصر)
 *   التاريخ      15   (حسب الفرق بالأيام بين تاريخ الفقدان وتاريخ العثور)
 *   الوصف        15   (الكلمات المشتركة ÷ كلمات الوصف الأقصر)
 * </pre>
 *
 * <p>نسبة التطابق = (مجموع النقاط المحققة ÷ مجموع أوزان الحقول المتوفرة في البلاغين) × 100
 *
 * <p>الحقل الذي لم يُذكر في أحد البلاغين (مثل وصف مختصر جدًا) لا يُحسب له ولا عليه،
 * حتى لا يخسر صاحب البلاغ المختصر أمام بلاغ مفصّل.
 *
 * <p>المستويات: عالٍ (75 فأكثر)، متوسط (50–74)، ضعيف (30–49)، وما دون 30 لا يُقترح.
 *
 * <p>هذا الكلاس لا يعتمد على أندرويد، لذلك يمكن اختباره مباشرة.
 */
public final class MatchScore {
    private MatchScore() {}

    public static final int W_CATEGORY = 25;
    public static final int W_NAME = 25;
    public static final int W_LOCATION = 20;
    public static final int W_DATE = 15;
    public static final int W_DESCRIPTION = 15;

    public static final int LEVEL_HIGH = 75;
    public static final int LEVEL_MEDIUM = 50;
    public static final int MIN_SHOWN = 30;

    /** إذا كانت أوزان الحقول المتوفرة أقل من هذا، لا يتجاوز المستوى "ضعيف". */
    public static final int MIN_AVAILABLE_WEIGHT = 50;

    /** أقل عدد كلمات مميِّزة ليُحسب الوصف. */
    public static final int MIN_DESCRIPTION_WORDS = 2;

    public static final String OTHER = "أخرى";

    public static final int HIGH = 3, MEDIUM = 2, LOW = 1, NONE = 0, EXCLUDED = -1;

    /** بيانات البلاغ اللازمة للحساب. التاريخ بصيغة dd/MM/yyyy. */
    public static final class Input {
        public final String itemName, category, location, date, description;

        public Input(String itemName, String category, String location, String date, String description) {
            this.itemName = itemName == null ? "" : itemName;
            this.category = category == null ? "" : category;
            this.location = location == null ? "" : location;
            this.date = date == null ? "" : date;
            this.description = description == null ? "" : description;
        }
    }

    /** نتيجة حقل واحد. */
    public static final class Field {
        public final String label;
        public final int weight;
        public final boolean available;
        public final double similarity;
        public final String note;

        Field(String label, int weight, boolean available, double similarity, String note) {
            this.label = label;
            this.weight = weight;
            this.available = available;
            this.similarity = available ? Math.max(0, Math.min(1, similarity)) : 0;
            this.note = note;
        }

        public double points() {
            return available ? weight * similarity : 0;
        }
    }

    /** النتيجة الكاملة. */
    public static final class Result {
        public final List<Field> fields = new ArrayList<>();
        public int availableWeight;
        public double earned;
        public int score;
        public boolean excluded;
        public String excludedReason = "";

        public int level() {
            if (excluded) return EXCLUDED;
            int lvl = score >= LEVEL_HIGH ? HIGH : score >= LEVEL_MEDIUM ? MEDIUM : score >= MIN_SHOWN ? LOW : NONE;
            if (availableWeight < MIN_AVAILABLE_WEIGHT && lvl > LOW) lvl = LOW;
            return lvl;
        }

        public String levelLabel() {
            return label(level());
        }

        public boolean shown() {
            return level() >= LOW;
        }

        /** شرح الحساب سطرًا سطرًا (يظهر للمسؤول في شاشة مراجعة المطابقة). */
        public String explain() {
            StringBuilder sb = new StringBuilder();
            if (excluded) return "مستبعد: " + excludedReason;
            for (Field f : fields) {
                sb.append("• ").append(f.label).append(": ");
                if (f.available) {
                    sb.append(fmt(f.points())).append(" من ").append(f.weight).append(" — ").append(f.note);
                } else {
                    sb.append("غير محسوب — ").append(f.note);
                }
                sb.append('\n');
            }
            sb.append("المجموع: ").append(fmt(earned)).append(" من ").append(availableWeight)
                    .append(" = ").append(score).append("% (").append(levelLabel()).append(")");
            if (availableWeight < 100) {
                sb.append("\nاكتمال البيانات: ").append(availableWeight)
                        .append("% (الحقول غير المذكورة لم تُحسب للبلاغ ولا عليه)");
            }
            return sb.toString();
        }
    }

    public static String label(int level) {
        switch (level) {
            case HIGH: return "تطابق عالٍ";
            case MEDIUM: return "تطابق متوسط";
            case LOW: return "تطابق ضعيف";
            case EXCLUDED: return "مستبعد";
            default: return "غير مقترح";
        }
    }

    public static Result evaluate(Input lost, Input found) {
        Result r = new Result();
        r.fields.add(category(lost.category, found.category));
        r.fields.add(text("اسم الغرض", W_NAME, lost.itemName, found.itemName, 1));
        r.fields.add(location(lost.location, found.location));
        Field date = date(lost.date, found.date, r);
        r.fields.add(date);
        r.fields.add(text("الوصف", W_DESCRIPTION, lost.description, found.description, MIN_DESCRIPTION_WORDS));

        for (Field f : r.fields) {
            if (!f.available) continue;
            r.availableWeight += f.weight;
            r.earned += f.points();
        }
        r.score = r.excluded || r.availableWeight == 0 ? 0
                : (int) Math.round(r.earned / r.availableWeight * 100.0);
        return r;
    }

    // ---------------- الحقول ----------------

    private static Field category(String a, String b) {
        String x = TextNorm.normalize(a), y = TextNorm.normalize(b);
        String other = TextNorm.normalize(OTHER);
        if (x.isEmpty() || y.isEmpty() || x.equals(other) || y.equals(other)) {
            return new Field("الفئة", W_CATEGORY, false, 0, "الفئة غير محددة في أحد البلاغين");
        }
        boolean same = x.equals(y);
        return new Field("الفئة", W_CATEGORY, true, same ? 1 : 0, same ? "نفس الفئة" : "فئة مختلفة");
    }

    private static Field location(String a, String b) {
        String x = TextNorm.normalize(a), y = TextNorm.normalize(b);
        if (x.isEmpty() || y.isEmpty() || x.equals(TextNorm.normalize(OTHER)) || y.equals(TextNorm.normalize(OTHER))) {
            return new Field("الموقع", W_LOCATION, false, 0, "الموقع غير محدد في أحد البلاغين");
        }
        if (x.equals(y)) return new Field("الموقع", W_LOCATION, true, 1, "نفس الموقع");
        return text("الموقع", W_LOCATION, a, b, 1);
    }

    /** مقارنة نصية: الكلمات المشتركة ÷ عدد كلمات النص الأقصر. */
    private static Field text(String label, int weight, String a, String b, int minWords) {
        List<String> x = TextNorm.keywords(a), y = TextNorm.keywords(b);
        if (x.size() < minWords || y.size() < minWords) {
            String why = minWords > 1
                    ? "مكتوب باختصار في أحد البلاغين (أقل من " + minWords + " كلمات مميِّزة)"
                    : "غير مذكور في أحد البلاغين";
            return new Field(label, weight, false, 0, why);
        }
        List<String> common = TextNorm.commonWords(x, y);
        int base = Math.min(x.size(), y.size());
        double sim = common.size() / (double) base;
        String note = common.size() + " من " + base + " كلمات متطابقة"
                + (common.isEmpty() ? "" : " (" + join(common) + ")");
        return new Field(label, weight, true, sim, note);
    }

    /**
     * التاريخ: الفرق = تاريخ العثور − تاريخ الفقدان (بالأيام).
     * من -1 إلى 2 يوم: 1.0 | من 3 إلى 7: 0.7 | من 8 إلى 14: 0.4 | من 15 إلى 30: 0.2 | أكثر من 30: صفر.
     * إذا كان العثور قبل الفقدان بأكثر من يوم فالمطابقة مستحيلة وتُستبعد.
     */
    private static Field date(String lostDate, String foundDate, Result r) {
        long l = parse(lostDate), f = parse(foundDate);
        if (l < 0 || f < 0) return new Field("التاريخ", W_DATE, false, 0, "التاريخ غير صالح في أحد البلاغين");
        long days = Math.round((f - l) / 86_400_000.0);
        if (days < -1) {
            r.excluded = true;
            r.excludedReason = "تاريخ العثور قبل تاريخ الفقدان بـ " + (-days) + " أيام";
            return new Field("التاريخ", W_DATE, true, 0, r.excludedReason);
        }
        double sim;
        if (days <= 2) sim = 1.0;
        else if (days <= 7) sim = 0.7;
        else if (days <= 14) sim = 0.4;
        else if (days <= 30) sim = 0.2;
        else sim = 0;
        String note = days == 0 ? "نفس اليوم"
                : days < 0 ? "العثور قبل الفقدان بيوم (يُقبل لاحتمال خطأ في الإدخال)"
                : "الفرق " + days + (days == 1 ? " يوم" : " أيام");
        return new Field("التاريخ", W_DATE, true, sim, note);
    }

    static long parse(String d) {
        if (d == null || d.trim().isEmpty()) return -1;
        try {
            SimpleDateFormat f = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            f.setLenient(false);
            return f.parse(TextNorm.normalize(d)).getTime();
        } catch (ParseException e) {
            return -1;
        }
    }

    private static String join(List<String> words) {
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (sb.length() > 0) sb.append("، ");
            sb.append(w);
        }
        return sb.toString();
    }

    static String fmt(double v) {
        double r = Math.round(v * 10) / 10.0;
        return r == Math.floor(r) ? String.valueOf((long) r) : String.valueOf(r);
    }
}
