package lk.finora.api.advice.rules;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

import lk.finora.api.advice.ai.AdviceItem;
import lk.finora.api.advice.ai.AdviceItem.NumberFact;
import lk.finora.api.advice.ai.FactKey;
import lk.finora.api.advice.ai.FactSheet;

/** Approved T25 catalogue. Review actions never assert repayment verification or prescribe essential cuts. */
public final class RuleAdviceEngine {
    public static final Set<String> ACTIONS = Set.of("REVIEW_SPENDING_DEFICIT", "REVIEW_HIGH_WANTS",
            "REVIEW_UNMARKED_LOAN_BILLS", "BUILD_EMERGENCY_BUFFER", "REVIEW_GOAL_PROGRESS", "SAVE_WITHIN_AVAILABLE_HEADROOM");
    private RuleAdviceEngine() {}

    /** D58 goal-coaching sections, in display order. Their wording may be rewritten by the LLM; numbers may not. */
    public static final List<String> SECTIONS = List.of("GOAL_PLAN", "CUT_WANTS", "NEEDS_BEHAVIOUR",
            "SPENDING_PATTERN", "SALARY_PLAN");

    public static List<AdviceItem> generate(FactSheet f) {
        return generate(f, Locale.ENGLISH);
    }

    /** Localized rendering for the unregistered D58 candidate. T25 catalogue text stays exact. */
    public static List<AdviceItem> generate(FactSheet f, Locale locale) {
        List<AdviceItem> result = new ArrayList<>(coach(f, locale));
        if (positive(f, FactKey.SPENDING_DEFICIT)) add(result, f, "REVIEW_SPENDING_DEFICIT",
                "Recorded spending exceeds recorded income by LKR %s. Review the shortfall while protecting essential costs.", FactKey.SPENDING_DEFICIT);
        if (positive(f, FactKey.INCOME) && value(f, FactKey.WANTS).compareTo(value(f, FactKey.INCOME).multiply(new BigDecimal("0.40"))) > 0) {
            add(result, f, "REVIEW_HIGH_WANTS", "Recorded wants are LKR %s; the rounded policy reference is LKR %s. Review optional spending.",
                    FactKey.WANTS, FactKey.WANTS_REFERENCE);
        }
        if (positive(f, FactKey.UNMARKED_LOAN_BILLS)) add(result, f, "REVIEW_UNMARKED_LOAN_BILLS",
                "Loan bills totaling LKR %s are not marked paid. Review their recorded status; markers do not verify payment.", FactKey.UNMARKED_LOAN_BILLS);
        if (positive(f, FactKey.EMERGENCY_GAP)) add(result, f, "BUILD_EMERGENCY_BUFFER",
                "Declared emergency reserves are LKR %s; the next reserve milestone is LKR %s. Build gradually after essentials and due payments.",
                FactKey.EMERGENCY_RESERVE, FactKey.EMERGENCY_NEXT_TARGET);
        if (positive(f, FactKey.GOAL_REMAINING)) add(result, f, "REVIEW_GOAL_PROGRESS",
                "Recorded goal allocations remain LKR %s below current targets. Review affordability before allocating more.", FactKey.GOAL_REMAINING);
        if (positive(f, FactKey.AFFORDABLE_SAVINGS)) add(result, f, "SAVE_WITHIN_AVAILABLE_HEADROOM",
                "Recorded headroom supports up to LKR %s toward the initial savings target. Confirm all essential costs and obligations first.", FactKey.AFFORDABLE_SAVINGS);
        return List.copyOf(result);
    }

    private static boolean positive(FactSheet f, FactKey k) { return value(f, k).signum() > 0; }
    private static BigDecimal value(FactSheet f, FactKey k) { return f.facts().getOrDefault(k, BigDecimal.ZERO); }
    private static void add(List<AdviceItem> out, FactSheet f, String id, String template, FactKey... keys) {
        List<NumberFact> numbers = new ArrayList<>();
        List<String> formatted = new ArrayList<>();
        for (FactKey key : keys) {
            if (!f.facts().containsKey(key)) return;
            BigDecimal n = f.facts().get(key).setScale(2, java.math.RoundingMode.UNNECESSARY);
            formatted.add(n.toPlainString());
            numbers.add(new NumberFact(key, n, key.unit(), f.month()));
        }
        out.add(new AdviceItem(id, String.format(java.util.Locale.ROOT, template, formatted.toArray()), numbers));
    }

    /** Rule-based coaching sections for the facts that exist; a section is omitted when its core fact is missing. */
    public static List<AdviceItem> coach(FactSheet f) {
        return coach(f, Locale.ENGLISH);
    }

    private static List<AdviceItem> coach(FactSheet f, Locale locale) {
        ResourceBundle sinhala = "si".equals(locale.getLanguage())
                ? ResourceBundle.getBundle("advice.coaching", Locale.forLanguageTag("si"))
                : null;
        List<AdviceItem> out = new ArrayList<>();
        if (f.facts().containsKey(FactKey.FOCUS_GOAL_REMAINING)) {
            out.add(new Composer(f, "GOAL_PLAN", sinhala)
                    .say("goal.balance", "Your selected goal still needs LKR %s: LKR %s saved of LKR %s.",
                            FactKey.FOCUS_GOAL_REMAINING, FactKey.FOCUS_GOAL_SAVED, FactKey.FOCUS_GOAL_TARGET)
                    .say("goal.deadline", "To reach it by the target date in %s months, move LKR %s to it every month.",
                            FactKey.FOCUS_GOAL_MONTHS_LEFT, FactKey.FOCUS_GOAL_MONTHLY_NEEDED)
                    .say("goal.pace", "Over the last three months you added LKR %s a month on average.", FactKey.FOCUS_GOAL_MONTHLY_PACE)
                    .say("goal.atPace", "At that pace it takes about %s months.", FactKey.FOCUS_GOAL_MONTHS_AT_PACE)
                    .say("goal.withPlan", "With this month's salary plan below it takes about %s months.", FactKey.FOCUS_GOAL_MONTHS_WITH_PLAN)
                    .item());
        }
        if (f.facts().containsKey(FactKey.TOP_WANT_1_SPEND)) {
            Composer c = new Composer(f, "CUT_WANTS", sinhala);
            FactKey[][] keys = {
                    {FactKey.TOP_WANT_1_SPEND, FactKey.TOP_WANT_1_AVERAGE, FactKey.TOP_WANT_1_CUT},
                    {FactKey.TOP_WANT_2_SPEND, FactKey.TOP_WANT_2_AVERAGE, FactKey.TOP_WANT_2_CUT},
                    {FactKey.TOP_WANT_3_SPEND, FactKey.TOP_WANT_3_AVERAGE, FactKey.TOP_WANT_3_CUT}};
            for (int i = 0; i < keys.length; i++) {
                String label = f.slotLabels().get("TOP_WANT_" + (i + 1));
                if (label == null || !f.facts().containsKey(keys[i][0])) continue;
                if (f.facts().containsKey(keys[i][1])) {
                    c.sayCategory("wants.withAverage", "%s: LKR %s this month against a usual LKR %s. Cut LKR %s.", label, keys[i]);
                } else {
                    c.sayCategory("wants.withoutAverage", "%s: LKR %s this month. Cut LKR %s.", label, keys[i][0], keys[i][2]);
                }
            }
            out.add(c.say("wants.cutTotal", "Together these cuts free LKR %s a month for your goal.", FactKey.WANTS_CUT_TOTAL)
                    .say("wants.share", "Wants took %s%% of your income.", FactKey.WANTS_SHARE).item());
        }
        if (f.facts().containsKey(FactKey.NEEDS_SHARE) && f.facts().containsKey(FactKey.TOP_NEED_1_SPEND)) {
            Composer c = new Composer(f, "NEEDS_BEHAVIOUR", sinhala).say("needs.share", "Needs took %s%% of your income.", FactKey.NEEDS_SHARE);
            FactKey[][] keys = {{FactKey.TOP_NEED_1_SPEND, FactKey.TOP_NEED_1_AVERAGE},
                    {FactKey.TOP_NEED_2_SPEND, FactKey.TOP_NEED_2_AVERAGE}};
            for (int i = 0; i < keys.length; i++) {
                String label = f.slotLabels().get("TOP_NEED_" + (i + 1));
                if (label == null || !f.facts().containsKey(keys[i][0])) continue;
                if (f.facts().containsKey(keys[i][1])) {
                    c.sayCategory("needs.withAverage", "%s cost LKR %s against a usual LKR %s.", label, keys[i]);
                } else {
                    c.sayCategory("needs.withoutAverage", "%s cost LKR %s.", label, keys[i][0]);
                }
            }
            out.add(c.text("needs.protect", "Pay needs first and do not cut essentials to save faster.").item());
        }
        if (f.facts().containsKey(FactKey.SPEND_EARLY)) {
            Composer c = new Composer(f, "SPENDING_PATTERN", sinhala)
                    .say("spend.parts", "You spent LKR %s in the first ten days, LKR %s in the middle and LKR %s in the last part of the month.",
                            FactKey.SPEND_EARLY, FactKey.SPEND_MIDDLE, FactKey.SPEND_LATE)
                    .say("spend.share", "That puts %s%% of the month's spending in the first ten days.", FactKey.EARLY_SPEND_SHARE);
            if (f.facts().get(FactKey.EARLY_SPEND_SHARE).compareTo(new BigDecimal("50")) >= 0)
                c.text("spend.concentrated", "Spending is concentrated early in the month. Plan for bills before discretionary spending.");
            out.add(c.item());
        }
        if (f.facts().containsKey(FactKey.PLAN_NEEDS)) {
            Composer c = new Composer(f, "SALARY_PLAN", sinhala);
            if (f.facts().get(FactKey.PLAN_GOAL_TRANSFER).signum() > 0)
                c.say("salary.transfer", "On payday, move LKR %s to your goal or savings first.", FactKey.PLAN_GOAL_TRANSFER);
            else
                c.text("salary.shortfall", "Needs and the wants limit already use this month's income, so close that gap before saving more.");
            c.say("salary.needs", "Keep LKR %s for needs and bills.", FactKey.PLAN_NEEDS)
                    .say("salary.wants", "Limit wants to LKR %s, about LKR %s a week.", FactKey.PLAN_WANTS_CAP, FactKey.PLAN_WEEKLY_WANTS);
            if (f.facts().get(FactKey.PLAN_BUFFER).signum() > 0)
                c.say("salary.buffer", "Keep the remaining LKR %s as a buffer for surprises.", FactKey.PLAN_BUFFER);
            out.add(c.item());
        }
        return List.copyOf(out);
    }

    /** Builds one section from sentences; a sentence is skipped when one of its facts is missing. */
    private static final class Composer {
        private final FactSheet f;
        private final String id;
        private final StringBuilder text = new StringBuilder();
        private final List<NumberFact> numbers = new ArrayList<>();

        private final ResourceBundle sinhala;

        Composer(FactSheet f, String id, ResourceBundle sinhala) {
            this.f = f;
            this.id = id;
            this.sinhala = sinhala;
        }

        Composer say(String message, String english, FactKey... keys) {
            return say(message, english, new ArrayList<>(), keys);
        }

        Composer sayCategory(String message, String english, String label, FactKey... keys) {
            List<String> shown = new ArrayList<>();
            shown.add(localize("category." + label, label));
            return say(message, english, shown, keys);
        }

        private Composer say(String message, String english, List<String> shown, FactKey... keys) {
            for (int i = 0; i < keys.length; i++) {
                BigDecimal v = f.facts().get(keys[i]);
                if (v == null) return this;
                shown.add("LKR".equals(keys[i].unit()) ? v.setScale(2).toPlainString() : v.stripTrailingZeros().toPlainString());
            }
            for (FactKey key : keys) numbers.add(new NumberFact(key, f.facts().get(key).setScale(2), key.unit(), f.month()));
            return append(String.format(Locale.ROOT, localize(message, english), shown.toArray()));
        }

        Composer text(String message, String english) {
            return append(localize(message, english));
        }

        private String localize(String message, String english) {
            return sinhala == null ? english : sinhala.getString(message);
        }

        private Composer append(String sentence) {
            if (!text.isEmpty()) text.append(' ');
            text.append(sentence);
            return this;
        }

        AdviceItem item() { return new AdviceItem(id, text.toString(), numbers); }
    }
}
