package com.example.lostandfoundsystem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * توحيد النص العربي للبحث والمطابقة.
 *
 * خطوات التوحيد:
 * 1) توحيد الهمزات (أ إ آ ← ا)، والألف المقصورة (ى ← ي)، والتاء المربوطة (ة ← ه).
 * 2) حذف التشكيل والتطويل، وتحويل الأرقام العربية (١٢٣) إلى (123).
 * 3) تقسيم النص إلى كلمات، وحذف "ال" وما يسبقها من حروف الجر (وال، بال، لل…).
 * 4) حذف كلمات الربط التي لا تميّز الغرض (في، من، على، لونه…).
 */
public final class TextNorm {
    private TextNorm() {}

    private static final String[] PREFIXES = {"وال", "بال", "كال", "فال", "لل", "ال"};

    private static final Set<String> STOP_WORDS = new HashSet<>();

    static {
        String[] words = {
                "في", "من", "على", "الى", "إلى", "عن", "مع", "او", "أو", "ثم", "به", "بها", "فيه", "فيها",
                "هو", "هي", "هذا", "هذه", "ذلك", "تلك", "التي", "الذي", "كان", "كانت", "يوجد", "توجد",
                "لون", "لونه", "لونها", "نوع", "نوعه", "شكل", "شكله", "تقريبا", "جدا", "شي", "شيء",
                "قد", "لقد", "تم", "عند", "بعد", "قبل", "داخل", "بين", "كل", "غير", "مثل", "حوالي",
                "لي", "لا", "ما", "انا", "كنت", "وكان", "عليه", "عليها", "له", "لها", "the", "and", "of"
        };
        for (String w : words) STOP_WORDS.add(normalize(w));
    }

    /** توحيد الكتابة دون تقسيم. */
    public static String normalize(String value) {
        if (value == null) return "";
        StringBuilder sb = new StringBuilder(value.length());
        for (char c : value.toLowerCase(Locale.ROOT).toCharArray()) {
            if (c >= 'ً' && c <= 'ْ') continue;       // التشكيل
            if (c == 'ٰ' || c == 'ـ') continue;       // الألف الخنجرية والتطويل
            if (c == 'أ' || c == 'إ' || c == 'آ' || c == 'ٱ') c = 'ا';
            else if (c == 'ى') c = 'ي';
            else if (c == 'ة') c = 'ه';
            else if (c >= '٠' && c <= '٩') c = (char) ('0' + (c - '٠'));
            else if (c >= '۰' && c <= '۹') c = (char) ('0' + (c - '۰'));
            sb.append(c);
        }
        return sb.toString().trim();
    }

    /** الكلمات المميِّزة في النص بعد التوحيد وحذف "ال" وكلمات الربط (بدون تكرار). */
    public static List<String> keywords(String value) {
        Set<String> out = new LinkedHashSet<>();
        if (value == null) return new ArrayList<>(out);
        String n = normalize(value).replaceAll("[^\\p{L}\\p{N}]+", " ");
        for (String part : n.split("\\s+")) {
            if (part.isEmpty()) continue;
            String w = stripArticle(part);
            if (w.length() < 2 || STOP_WORDS.contains(w) || STOP_WORDS.contains(part)) continue;
            out.add(w);
        }
        return new ArrayList<>(out);
    }

    private static String stripArticle(String w) {
        for (String p : PREFIXES) {
            if (w.startsWith(p) && w.length() - p.length() >= 2) return w.substring(p.length());
        }
        return w;
    }

    /** جذع خفيف للكلمة: حذف التاء المربوطة (ه) وجمع المؤنث (ات) وياء النسبة/الملكية (ي) من آخرها. */
    static String stem(String w) {
        String[] suffixes = {"ات", "ه", "ي"};
        for (String suf : suffixes) {
            if (w.endsWith(suf) && w.length() - suf.length() >= 3) return w.substring(0, w.length() - suf.length());
        }
        return w;
    }

    /**
     * هل الكلمتان متطابقتان؟
     * متطابقتان إذا تساوى جذعاهما (مثل: سماعه / سماعات، جوال / جوالي)،
     * أو إذا كان الجذع الأقصر بداية للأطول وطوله 3 أحرف فأكثر (مثل: ايفون / ايفونات).
     */
    public static boolean sameWord(String a, String b) {
        if (a.equals(b)) return true;
        String x = stem(a), y = stem(b);
        if (x.equals(y)) return true;
        String s = x.length() <= y.length() ? x : y;
        String l = x.length() <= y.length() ? y : x;
        return s.length() >= 3 && l.startsWith(s);
    }

    /** الكلمات من القائمة الأقصر التي وُجد لها مقابل في القائمة الأطول. */
    public static List<String> commonWords(List<String> a, List<String> b) {
        List<String> small = a.size() <= b.size() ? a : b;
        List<String> large = a.size() <= b.size() ? b : a;
        List<String> common = new ArrayList<>();
        for (String s : small) {
            for (String l : large) {
                if (sameWord(s, l)) { common.add(s); break; }
            }
        }
        return common;
    }

    /**
     * نسبة التشابه بين نصين = عدد الكلمات المشتركة ÷ عدد كلمات النص الأقصر.
     * ترجع -1 إذا كان أحد النصين بلا كلمات مميِّزة (أي أن الحقل غير متوفر).
     */
    public static double similarity(String a, String b) {
        List<String> x = keywords(a), y = keywords(b);
        if (x.isEmpty() || y.isEmpty()) return -1;
        return commonWords(x, y).size() / (double) Math.min(x.size(), y.size());
    }
}
