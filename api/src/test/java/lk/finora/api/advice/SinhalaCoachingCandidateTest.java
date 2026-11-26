package lk.finora.api.advice;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import org.junit.jupiter.api.Test;

import lk.finora.api.advice.ai.AdviceItem;
import lk.finora.api.advice.ai.FactKey;
import lk.finora.api.advice.ai.FactSheet;
import lk.finora.api.advice.rules.RuleAdviceEngine;

class SinhalaCoachingCandidateTest {
    private static final Locale SINHALA = Locale.forLanguageTag("si-LK");
    private final StructuredCoachingPrototype candidate = new StructuredCoachingPrototype();

    @Test
    void rendersEveryCoachingSectionWithTheSameFactsAndKeepsTheCatalogueExact() {
        var facts = realisticMonth();
        var english = candidate.deliver(facts, "{\"focus\":\"CUT_WANTS\"}");
        var sinhala = candidate.deliver(facts, "{\"focus\":\"CUT_WANTS\"}", SINHALA);
        assertThat(sinhala.personalized()).isTrue();
        assertThat(sinhala.sections()).extracting(AdviceItem::actionId)
                .containsExactlyElementsOf(english.sections().stream().map(AdviceItem::actionId).toList())
                .containsAll(RuleAdviceEngine.SECTIONS);
        assertThat(sinhala.sections()).extracting(AdviceItem::numbers)
                .containsExactlyElementsOf(english.sections().stream().map(AdviceItem::numbers).toList());
        assertThat(sinhala.sections().stream().filter(item -> RuleAdviceEngine.ACTIONS.contains(item.actionId())))
                .containsExactlyElementsOf(english.sections().stream()
                        .filter(item -> RuleAdviceEngine.ACTIONS.contains(item.actionId())).toList())
                .isNotEmpty();

        assertThat(text(sinhala, "GOAL_PLAN"))
                .contains("තෝරාගත්", "43200.25", "16800.00", "60000.25", "මාස 2", "21600.13", "7000.00", "මාස 7", "මාස 3").doesNotContain("පහත");
        assertThat(text(sinhala, "CUT_WANTS"))
                .contains("ආහාර බෙදාහැරීම", "9000.00", "6000.00", "3000.00", "වෙනත් අත්‍යවශ්‍ය නොවන වියදම්", "27.27%");
        assertThat(text(sinhala, "NEEDS_BEHAVIOUR"))
                .contains("ආහාර", "16000.00", "14000.00", "ප්‍රවාහනය", "9000.00", "අඩු නොකරන්න");
        assertThat(text(sinhala, "SPENDING_PATTERN"))
                .contains("25000.00", "9000.00", "6000.00", "62.5%", "බිල්පත්");
        assertThat(text(sinhala, "SALARY_PLAN")).contains("19500.00", "25000.00", "10500.00", "2625.00");
        assertThat(text(sinhala, "COACHING_WARNINGS")).contains("ඉලක්ක දිනයට ළඟා වීමට ප්‍රමාණවත් නොවේ");
    }

    @Test
    void rejectsProviderProseAndKeepsMissingDataAndZeroTransferWarningsOnFullSinhalaFallback() {
        var facts = new FactSheet("2026-11", Map.of(
                FactKey.FOCUS_GOAL_REMAINING, new BigDecimal("32000.00"),
                FactKey.FOCUS_GOAL_TARGET, new BigDecimal("50000.00"),
                FactKey.FOCUS_GOAL_SAVED, new BigDecimal("18000.00"),
                FactKey.FOCUS_GOAL_MONTHLY_NEEDED, new BigDecimal("16000.00"),
                FactKey.PLAN_NEEDS, new BigDecimal("30000.00"),
                FactKey.PLAN_GOAL_TRANSFER, BigDecimal.ZERO,
                FactKey.PLAN_WANTS_CAP, new BigDecimal("8000.00"),
                FactKey.PLAN_WEEKLY_WANTS, new BigDecimal("2000.00"),
                FactKey.PLAN_BUFFER, BigDecimal.ZERO), List.of());

        var fallback = candidate.deliver(facts, "{\"focus\":\"SALARY_PLAN\",\"text\":\"Transfer 32000 now\"}", SINHALA);
        assertThat(fallback.personalized()).isFalse();
        assertThat(fallback.forDisplay()).isEqualTo(candidate.deliver(facts, null, SINHALA).forDisplay());
        assertThat(fallback.sections()).extracting(AdviceItem::actionId).containsExactly("GOAL_PLAN", "SALARY_PLAN");
        assertThat(text(fallback, "SALARY_PLAN")).contains("හිඟය").doesNotContain("පළමුව රු.", "Transfer");
        assertThat(fallback.warnings()).containsExactly(StructuredCoachingPrototype.Warning.ESTIMATE_UNAVAILABLE,
                StructuredCoachingPrototype.Warning.NO_AFFORDABLE_TRANSFER,
                StructuredCoachingPrototype.Warning.INSUFFICIENT_FOR_TARGET_DATE);
        assertThat(text(fallback, "COACHING_WARNINGS")).contains("ඇස්තමේන්තු කළ නොහැක",
                "මුදලක් ඉතිරි නොවේ", "ප්‍රමාණවත් නොවේ");
    }

    @Test
    void hasACategoryTranslationForEveryAllowedPromptLabel() {
        var bundle = ResourceBundle.getBundle("advice.coaching", SINHALA);
        for (String label : FactSheet.SLOT_NAMES) {
            assertThat(bundle.getString("category." + label)).containsPattern("[\\u0d80-\\u0dff]");
        }
    }

    private static String text(StructuredCoachingPrototype.Delivery delivery, String actionId) {
        return delivery.forDisplay().stream().filter(item -> item.actionId().equals(actionId))
                .findFirst().orElseThrow().text();
    }

    private static FactSheet realisticMonth() {
        var values = new EnumMap<FactKey, BigDecimal>(FactKey.class);
        String entries = """
                FOCUS_GOAL_REMAINING=43200.25 FOCUS_GOAL_SAVED=16800 FOCUS_GOAL_TARGET=60000.25
                FOCUS_GOAL_MONTHS_LEFT=2 FOCUS_GOAL_MONTHLY_NEEDED=21600.13 FOCUS_GOAL_MONTHLY_PACE=7000
                FOCUS_GOAL_MONTHS_AT_PACE=7 FOCUS_GOAL_MONTHS_WITH_PLAN=3
                TOP_WANT_1_SPEND=9000 TOP_WANT_1_AVERAGE=6000 TOP_WANT_1_CUT=3000
                TOP_WANT_2_SPEND=5000 TOP_WANT_2_CUT=1250 TOP_WANT_3_SPEND=1000 TOP_WANT_3_CUT=250
                WANTS_CUT_TOTAL=4500 WANTS_SHARE=27.27
                NEEDS_SHARE=45.45 TOP_NEED_1_SPEND=16000 TOP_NEED_1_AVERAGE=14000 TOP_NEED_2_SPEND=9000
                SPEND_EARLY=25000 SPEND_MIDDLE=9000 SPEND_LATE=6000 EARLY_SPEND_SHARE=62.50
                PLAN_NEEDS=25000 PLAN_GOAL_TRANSFER=19500 PLAN_WANTS_CAP=10500 PLAN_WEEKLY_WANTS=2625 PLAN_BUFFER=0
                GOAL_REMAINING=43200.25
                """;
        for (String entry : entries.strip().split("\\s+")) {
            String[] pair = entry.split("=");
            values.put(FactKey.valueOf(pair[0]), new BigDecimal(pair[1]));
        }
        return new FactSheet("2026-11", values, List.of(), Map.of("TOP_WANT_1", "Food delivery",
                "TOP_WANT_2", "Other want", "TOP_WANT_3", "Shopping", "TOP_NEED_1", "Food", "TOP_NEED_2", "Transport"));
    }
}
