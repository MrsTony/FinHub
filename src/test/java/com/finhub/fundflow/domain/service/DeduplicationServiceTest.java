package com.finhub.fundflow.domain.service;

import com.finhub.fundflow.domain.aggregate.Transaction;
import com.finhub.fundflow.domain.vo.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public abstract class DeduplicationServiceTest {

    protected abstract DeduplicationService createService();


    // 辅助方法：创建 Money
    private Money money(String amount) {
        return new Money(new BigDecimal(amount), "CNY");
    }

    // 辅助方法：创建加密字符串
    private EncryptedString encrypted(String plain) {
        return EncryptedString.fromPlain(plain, "12345678901234567890123456789012");
    }

    // 辅助方法：创建指纹
    private Fingerprint fingerprint(String hash) {
        return new Fingerprint(hash, "salt");
    }

    // 辅助方法：创建交易
    private Transaction createTx(String extId, String fpHash) {
        return Transaction.createFrom(
                extId,
                money("100.00"),
                Direction.OUT,
                Category.UNCLASSIFIED,
                LocalDateTime.now(),
                encrypted("测试商户"),
                encrypted("测试备注"),
                fingerprint(fpHash),
                "ALIPAY"
        );
    }

    @Test
    @DisplayName("应去除 external_id 重复的交易")
    void shouldRemoveDuplicatesByExternalId() {
        DeduplicationService service = createService();
        Transaction t1 = createTx("ext-001", "fp-001");
        Transaction t2 = createTx("ext-001", "fp-002"); // 相同 externalId

        List<Transaction> result = service.deduplicate(List.of(t1, t2));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getExternalId()).isEqualTo("ext-001");
    }

    @Test
    @DisplayName("应去除 fingerprint 重复的交易")
    void shouldRemoveDuplicatesByFingerprint() {
        DeduplicationService service = createService();
        Transaction t1 = createTx(null, "fp-same");
        Transaction t2 = createTx(null, "fp-same"); // 相同 fingerprint

        List<Transaction> result = service.deduplicate(List.of(t1, t2));

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("应保留 external_id 和 fingerprint 均唯一的交易")
    void shouldPreserveUniqueRecords() {
        DeduplicationService service = createService();
        Transaction t1 = createTx("ext-001", "fp-001");
        Transaction t2 = createTx("ext-002", "fp-002");

        List<Transaction> result = service.deduplicate(List.of(t1, t2));

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("输入 null 应抛异常")
    void shouldRejectNullInput() {
        DeduplicationService service = createService();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.deduplicate(null));
    }

    @Test
    @DisplayName("输入包含 null 元素应抛异常")
    void shouldRejectNullElements() {
        DeduplicationService service = createService();
        List<Transaction> list = new ArrayList<>();
        list.add(createTx("ext-001", "fp-001"));
        list.add(null);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.deduplicate(list));
    }

    @Test
    @DisplayName("不应修改输入列表")
    void shouldNotModifyInputList() {
        DeduplicationService service = createService();
        Transaction t1 = createTx("ext-001", "fp-001");
        Transaction t2 = createTx("ext-001", "fp-002");

        List<Transaction> input = List.of(t1, t2);
        List<Transaction> result = service.deduplicate(input);

        assertThat(input).hasSize(2); // 输入列表未被修改
        assertThat(result).hasSize(1); // 返回新列表
    }
}

