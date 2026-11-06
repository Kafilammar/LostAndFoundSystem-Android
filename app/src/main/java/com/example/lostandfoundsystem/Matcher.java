package com.example.lostandfoundsystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * اقتراح المطابقات بين البلاغات المفقودة والموجودة.
 *
 * <p>القواعد:
 * <ul>
 *   <li>البلاغ المفقود "مفتوح" إذا كان معتمدًا وغير مربوط بمطابقة.</li>
 *   <li>البلاغ الموجود "مفتوح" إذا كان معتمدًا أو موجودًا في المركز، وغير مربوط بمطابقة.</li>
 *   <li>لا تُحذف الاقتراحات الأخرى عند اعتماد مطابقة؛ تبقى بدائل، وتعود للظهور تلقائيًا
 *       إذا أُلغيت المطابقة (فشل التحقق، أو أفاد صاحب البلاغ أن الغرض ليس له).</li>
 *   <li>الزوج الذي ثبت أنه غير مطابق يُستبعد نهائيًا (rejectedMatchWithId).</li>
 *   <li>ترتيب الاقتراحات: مطالبة صاحب البلاغ أولًا، ثم النسبة الأعلى، ثم الأقرب تاريخًا، ثم الأقدم إبلاغًا.</li>
 * </ul>
 */
public final class Matcher {
    private Matcher() {}

    /** أقصى عدد أغراض يمكن لصاحب البلاغ المطالبة بها في وقت واحد. */
    public static final int MAX_CLAIMS = 3;

    public static class Pair {
        public final Report lost, found;
        public final MatchScore.Result result;
        public final int score;
        public final boolean claimed;
        /** ترتيب هذا الاقتراح بين اقتراحات البلاغ المفقود نفسه (يبدأ من 1). */
        public int rank = 1;
        public int total = 1;
        /** عدد البلاغات المفقودة الأخرى المقترح لها هذا الغرض نفسه. */
        public int competing = 0;

        Pair(Report lost, Report found, MatchScore.Result result) {
            this.lost = lost;
            this.found = found;
            this.result = result;
            this.score = result.score;
            this.claimed = lost.claimFoundIds.contains(found.id);
        }

        public long dayGap() {
            long l = MatchScore.parse(lost.date), f = MatchScore.parse(found.date);
            return l < 0 || f < 0 ? Long.MAX_VALUE : Math.abs(f - l);
        }
    }

    public static MatchScore.Result evaluate(Report lost, Report found) {
        return MatchScore.evaluate(lost.matchInput(), found.matchInput());
    }

    public static int score(Report lost, Report found) {
        return evaluate(lost, found).score;
    }

    public static boolean lostOpen(Report l) {
        return l.lost && Status.APPROVED.equals(l.status) && !l.hasActiveMatch();
    }

    public static boolean foundOpen(Report f) {
        return !f.lost && (Status.APPROVED.equals(f.status) || Status.AT_CENTER.equals(f.status)) && !f.hasActiveMatch();
    }

    /** غرض موجود مرتبط مؤقتًا ببلاغ آخر (قيد التحقق)، ويعود متاحًا إذا لم يكن لصاحبه. */
    public static boolean foundLocked(Report f) {
        return !f.lost && f.hasActiveMatch() && !Status.RETURNED.equals(f.status);
    }

    public static boolean excluded(Report lost, Report found) {
        return lost.rejectedIds.contains(found.id) || found.rejectedIds.contains(lost.id);
    }

    public static final Comparator<Pair> ORDER = (a, b) -> {
        if (a.claimed != b.claimed) return a.claimed ? -1 : 1;
        if (a.score != b.score) return Integer.compare(b.score, a.score);
        int g = Long.compare(a.dayGap(), b.dayGap());
        if (g != 0) return g;
        return Long.compare(a.found.createdAt, b.found.createdAt);
    };

    /** كل الأغراض الموجودة المرشحة لبلاغ مفقود واحد (مرتبة). */
    public static List<Pair> candidatesForLost(Report lost, List<Report> founds, boolean includeLocked) {
        List<Pair> out = new ArrayList<>();
        for (Report f : founds) {
            boolean open = foundOpen(f);
            boolean locked = includeLocked && foundLocked(f) && !lost.id.equals(f.matchedWithId);
            if (!open && !locked) continue;
            if (excluded(lost, f)) continue;
            Pair p = new Pair(lost, f, evaluate(lost, f));
            if (p.result.level() == MatchScore.EXCLUDED) continue;
            if (p.result.shown() || p.claimed) out.add(p);
        }
        Collections.sort(out, ORDER);
        for (int i = 0; i < out.size(); i++) {
            out.get(i).rank = i + 1;
            out.get(i).total = out.size();
        }
        return out;
    }

    /** اقتراحات لوحة المسؤول: لكل بلاغ مفقود مفتوح كل مرشحيه، مع عدد المنافسين على كل غرض. */
    public static List<Pair> suggest(List<Report> losts, List<Report> founds) {
        List<List<Pair>> groups = new ArrayList<>();
        Map<String, Integer> competition = new HashMap<>();
        for (Report l : losts) {
            if (!lostOpen(l)) continue;
            List<Pair> c = candidatesForLost(l, founds, false);
            if (c.isEmpty()) continue;
            groups.add(c);
            for (Pair p : c) {
                Integer n = competition.get(p.found.id);
                competition.put(p.found.id, n == null ? 1 : n + 1);
            }
        }
        // المجموعات التي فيها مطالبة من صاحب البلاغ أولًا، ثم الأعلى نسبة
        Collections.sort(groups, (a, b) -> ORDER.compare(a.get(0), b.get(0)));
        List<Pair> out = new ArrayList<>();
        for (List<Pair> g : groups) {
            for (Pair p : g) {
                Integer n = competition.get(p.found.id);
                p.competing = n == null ? 0 : n - 1;
                out.add(p);
            }
        }
        return out;
    }
}
