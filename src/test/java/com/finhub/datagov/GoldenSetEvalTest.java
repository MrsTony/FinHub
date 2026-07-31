package com.finhub.datagov;

import com.finhub.fundflow.domain.aggregate.Transaction;
import com.finhub.fundflow.domain.service.TransactionClassifier;
import com.finhub.fundflow.domain.vo.Category;
import com.finhub.fundflow.domain.vo.CategorySuggestion;
import com.finhub.fundflow.domain.vo.Direction;
import com.finhub.fundflow.domain.vo.EncryptedString;
import com.finhub.fundflow.domain.vo.Fingerprint;
import com.finhub.fundflow.domain.vo.Money;
import com.finhub.fundflow.infrastructure.service.TransactionClassifierImpl;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden Set 评测骨架（纯单测，不连库）。Day7 先对规则层 {@link TransactionClassifier}
 * 跑通分类通过率；NL 查询评测待 LLM 接通后启用。
 */
class GoldenSetEvalTest {

    private static final Path GOLDEN_SET = Path.of("golden-set", "classifier-cases.jsonl");

    private static final String DEFAULT_KEY = "12345678901234567890123456789012";

    private final TransactionClassifier classifier = new TransactionClassifierImpl();

    @Test
    @DisplayName("规则层分类在 Golden Set 上通过率应达标（>=90%）")
    void shouldMeetClassifierPassRate() throws Exception {
        List<String> lines = Files.readAllLines(GOLDEN_SET);
        int total = 0, pass = 0;
        for (String line : lines) {
            if (line.isBlank()) continue;
            total++;
            String merchant = extract(line, "merchant");
            Category expected = Category.valueOf(extract(line, "expectedCategory"));
            CategorySuggestion actual = classifier.classify(toTransaction(merchant, expected));
            if (actual != null && actual.category() == expected) pass++;
        }
        double rate = total == 0 ? 0 : (double) pass / total;
        System.out.printf("GoldenSet classifier pass rate: %d/%d = %.1f%%%n", pass, total, rate * 100);
        assertThat(total).isGreaterThanOrEqualTo(20);
        assertThat(rate).isGreaterThanOrEqualTo(0.9);
    }

    @Test
    @Disabled("TODO Day8+: LLM 接通后对 nl-queries.jsonl 跑 NL2SQL 评测")
    @DisplayName("NL 查询评测（待 LLM 接通）")
    void shouldEvaluateNlQueries() {
    }

    /** 按期望分类选择方向：INCOME 用 IN（真收入），其余用 OUT（消费）。 */
    private static Transaction toTransaction(String merchant, Category expected) {
        Direction direction = expected == Category.INCOME ? Direction.IN : Direction.OUT;
        return Transaction.createFrom(
                "eval-" + merchant.hashCode(),
                new Money(new BigDecimal("100.00"), "CNY"),
                direction,
                Category.UNCLASSIFIED,
                LocalDateTime.of(2026, 1, 1, 12, 0),
                EncryptedString.fromPlain(merchant, DEFAULT_KEY),
                EncryptedString.fromPlain("测试备注", DEFAULT_KEY),
                new Fingerprint("fingerprint-" + merchant.hashCode(), "salt"),
                "GOLDEN"
        );
    }

    /** 极简 JSON 取值（避免引依赖）：按 "key":"value" 提取。 */
    private static String extract(String jsonLine, String key) {
        String token = "\"" + key + "\":\"";
        int i = jsonLine.indexOf(token);
        if (i < 0) throw new IllegalArgumentException("缺少字段 " + key + ": " + jsonLine);
        int start = i + token.length();
        return jsonLine.substring(start, jsonLine.indexOf('"', start));
    }
}
