package com.example.lostandfoundsystem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** اختبارات حساب التطابق وسيناريوهات المطابقة المتعددة. */
public class MatchScenarioTest {

    private static Report lost(String id, String name, String cat, String loc, String date, String desc) {
        Report r = base(id, name, cat, loc, date, desc);
        r.lost = true;
        r.collection = Db.LOST;
        return r;
    }

    private static Report found(String id, String name, String cat, String loc, String date, String desc) {
        Report r = base(id, name, cat, loc, date, desc);
        r.lost = false;
        r.collection = Db.FOUND;
        return r;
    }

    private static Report base(String id, String name, String cat, String loc, String date, String desc) {
        Report r = new Report();
        r.id = id; r.itemName = name; r.category = cat; r.location = loc; r.date = date; r.description = desc;
        r.status = Status.APPROVED;
        return r;
    }

    @Test
    public void partialNameMatchUsesShorterText() {
        // 3 كلمات مقابل 3 كلمات، المشترك كلمة واحدة ← 1/3 من وزن الاسم (25) = 8.3
        MatchScore.Result r = MatchScore.evaluate(
                new MatchScore.Input("سماعة ابل بيضاء", "إلكترونيات", "المدينة الجامعية", "01/11/2026", "سماعات ايربودز بيضاء في علبة"),
                new MatchScore.Input("سماعة سامسونج سوداء", "إلكترونيات", "سكن المدينة الجامعية", "06/11/2026", "سماعات سوداء داخل علبة"));
        assertEquals(8.3, r.fields.get(1).points(), 0.05);
        assertEquals(74, r.score);
        assertEquals(MatchScore.MEDIUM, r.level());
    }

    @Test
    public void briefReportIsNotPenalised() {
        // بلاغ مختصر (بدون وصف) لا يخسر أمام بلاغ مفصّل: الوصف لا يُحسب له ولا عليه
        Report brief = lost("L1", "جوالي", "إلكترونيات", "المكتبة المركزية", "03/11/2026", "جوال");
        Report phone = found("F1", "جوال ايفون", "إلكترونيات", "المكتبة المركزية", "04/11/2026", "ايفون 13 لونه ازرق بغطاء شفاف");
        MatchScore.Result r = Matcher.evaluate(brief, phone);
        assertEquals(85, r.availableWeight);
        assertEquals(100, r.score);
    }

    @Test
    public void impossibleDateIsExcluded() {
        Report l = lost("L1", "محفظة", "مقتنيات شخصية", "البوابة الرئيسية", "10/11/2026", "محفظة بنية جلد");
        Report f = found("F1", "محفظة", "مقتنيات شخصية", "البوابة الرئيسية", "01/11/2026", "محفظة بنية جلد");
        assertEquals(MatchScore.EXCLUDED, Matcher.evaluate(l, f).level());
        assertTrue(Matcher.candidatesForLost(l, Arrays.asList(f), false).isEmpty());
    }

    @Test
    public void allCandidatesAreKeptAndRanked() {
        // بلاغ مفقود واحد ← عدة أغراض (One-to-Many): لا يُحذف أي مرشح
        Report l = lost("L1", "جوال ايفون", "إلكترونيات", "المكتبة المركزية", "03/11/2026", "ايفون ازرق بغطاء شفاف");
        Report f1 = found("F1", "جوال ايفون", "إلكترونيات", "المكتبة المركزية", "03/11/2026", "ايفون ازرق غطاء شفاف");
        Report f2 = found("F2", "جوال", "إلكترونيات", "المكتبة المركزية", "05/11/2026", "جوال اسود");
        Report f3 = found("F3", "جوال سامسونج", "إلكترونيات", "المدينة الجامعية", "20/11/2026", "سامسونج رمادي");
        List<Matcher.Pair> c = Matcher.candidatesForLost(l, Arrays.asList(f3, f2, f1), false);
        assertEquals("F1", c.get(0).found.id);
        assertEquals(1, c.get(0).rank);
        assertEquals(c.size(), c.get(0).total);
        assertTrue(c.size() >= 2);
    }

    @Test
    public void claimComesFirst() {
        Report l = lost("L1", "جوال", "إلكترونيات", "المكتبة المركزية", "03/11/2026", "");
        Report f1 = found("F1", "جوال ايفون", "إلكترونيات", "المكتبة المركزية", "03/11/2026", "ايفون ازرق");
        Report f2 = found("F2", "جوال سامسونج", "إلكترونيات", "المكتبة المركزية", "04/11/2026", "سامسونج اسود");
        l.claimFoundIds.add("F2");
        List<Matcher.Pair> c = Matcher.candidatesForLost(l, Arrays.asList(f1, f2), false);
        assertEquals("F2", c.get(0).found.id);
        assertTrue(c.get(0).claimed);
    }

    @Test
    public void failedVerificationShowsNextCandidate() {
        Report l = lost("L1", "جوال ايفون", "إلكترونيات", "المكتبة المركزية", "03/11/2026", "ايفون ازرق");
        Report f1 = found("F1", "جوال ايفون", "إلكترونيات", "المكتبة المركزية", "03/11/2026", "ايفون ازرق");
        Report f2 = found("F2", "جوال ايفون", "إلكترونيات", "المكتبة المركزية", "04/11/2026", "ايفون ازرق مكسور");

        // اعتماد المطابقة الأولى: الغرض الأول يُحجز، والثاني يبقى بديلًا
        l.status = Status.AT_CENTER; l.matchedWithId = "F1";
        f1.status = Status.AT_CENTER; f1.matchedWithId = "L1";
        assertFalse(Matcher.lostOpen(l));

        // فشل التحقق في المركز: يُلغى الربط ويُستبعد الزوج
        l.status = Status.APPROVED; l.matchedWithId = ""; l.rejectedIds.add("F1");
        f1.matchedWithId = ""; f1.rejectedIds.add("L1");          // يبقى في المركز متاحًا لغيره
        assertTrue(Matcher.lostOpen(l));
        assertTrue(Matcher.foundOpen(f1));

        List<Matcher.Pair> c = Matcher.candidatesForLost(l, Arrays.asList(f1, f2), false);
        assertEquals(1, c.size());
        assertEquals("F2", c.get(0).found.id);
    }

    @Test
    public void sameItemSuggestedToSeveralOwners() {
        // عدة بلاغات مفقودة ← غرض واحد (Many-to-One)
        Report l1 = lost("L1", "جوال ايفون", "إلكترونيات", "المكتبة المركزية", "03/11/2026", "ايفون ازرق");
        Report l2 = lost("L2", "جوال ايفون", "إلكترونيات", "المكتبة المركزية", "03/11/2026", "ايفون ازرق بغطاء");
        Report f = found("F1", "جوال ايفون", "إلكترونيات", "المكتبة المركزية", "04/11/2026", "ايفون ازرق بغطاء");
        List<Matcher.Pair> s = Matcher.suggest(Arrays.asList(l1, l2), Arrays.asList(f));
        assertEquals(2, s.size());
        assertEquals(1, s.get(0).competing);

        // بعد اعتماد المطابقة مع الأول لا يُقترح الغرض للثاني...
        l1.matchedWithId = "F1"; l1.status = Status.MATCHED;
        f.matchedWithId = "L1"; f.status = Status.MATCHED;
        assertTrue(Matcher.suggest(Arrays.asList(l1, l2), Arrays.asList(f)).isEmpty());
        // ...لكنه يظهر له كـ "قيد التحقق مع بلاغ آخر"
        assertEquals(1, Matcher.candidatesForLost(l2, Arrays.asList(f), true).size());

        // إذا لم يكن للأول، يعود مقترحًا للثاني تلقائيًا
        l1.matchedWithId = ""; l1.status = Status.APPROVED; l1.rejectedIds.add("F1");
        f.matchedWithId = ""; f.status = Status.AT_CENTER; f.rejectedIds.add("L1");
        List<Matcher.Pair> again = Matcher.suggest(Arrays.asList(l1, l2), Arrays.asList(f));
        assertEquals(1, again.size());
        assertEquals("L2", again.get(0).lost.id);
    }
}
