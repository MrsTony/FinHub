package com.finhub.fundflow.infrastructure.adapter;

import com.finhub.fundflow.acl.DataSourceAdapter;
import com.finhub.fundflow.acl.RawRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 支付宝 CSV 防腐层适配器（2024 真实导出格式）。
 * 核心域不认识 CSV，只认识 RawRecord。
 *
 * <h3>真实导出格式特征</h3>
 * <ul>
 *   <li>编码：GBK（适配器自动识别 UTF-8/GBK）</li>
 *   <li>前置约 24 行元信息（分隔符、导出信息、统计等），表头在元信息之后</li>
 *   <li>表头 13 列含尾随逗号：交易时间,交易分类,交易对方,对方账号,商品说明,收/支,金额,收/付款方式,交易状态,交易订单号,商家订单号,备注,</li>
 *   <li>交易订单号/商家订单号字段常含 Tab</li>
 *   <li>方向值含"不计收支"（非收入/支出，按业务规则跳过）</li>
 *   <li>金额通常无 ¥ 符号（纯数字）</li>
 * </ul>
 */
@Slf4j
@Service
public class AlipayCSVAdapter implements DataSourceAdapter {

    /** 真实表头（13 列，末尾尾随逗号） */
    private static final String EXPECTED_HEADER =
            "交易时间,交易分类,交易对方,对方账号,商品说明,收/支,金额,收/付款方式,交易状态,交易订单号,商家订单号,备注,";

    /** 真实字段索引 */
    private static final int IDX_TRANS_TIME = 0;
    private static final int IDX_COUNTERPARTY = 2;
    private static final int IDX_PRODUCT = 4;
    private static final int IDX_DIRECTION = 5;
    private static final int IDX_AMOUNT = 6;
    private static final int IDX_EXTERNAL_ID = 9;
    private static final int IDX_REMARK = 11;

    /** 期望最小列数（含尾随逗号产生的空尾列） */
    private static final int MIN_FIELD_COUNT = 12;

    /** 时间格式 */
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 默认币种 */
    private static final String DEFAULT_CURRENCY = "CNY";

    /** 来源系统标识 */
    private static final String SOURCE_SYSTEM = "ALIPAY";

    /** 合法方向值（原始文本透传；"不计收支"等非此集合的值跳过） */
    private static final Set<String> VALID_DIRECTIONS = Set.of("收入", "支出", "IN", "OUT");

    /** 金额提取正则：匹配可选符号 + 整数 + 可选小数 */
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("[-+]?\\d+(?:\\.\\d+)?");

    @Override
    public List<RawRecord> adapt(InputStream inputStream, String filename) {
        Objects.requireNonNull(inputStream, "输入流不能为空");
        Objects.requireNonNull(filename, "文件名不能为空");

        // 1. 读取字节并识别编码（UTF-8 优先，失败试 GBK）
        byte[] bytes = readAllBytes(inputStream);
        String content = decodeContent(bytes, filename);

        // 2. 按行拆分并定位表头行
        String[] lines = content.split("\\r?\\n", -1);
        int headerIndex = findHeaderLine(lines);
        if (headerIndex < 0) {
            throw new UnsupportedOperationException("暂不支持该 CSV 格式: " + filename);
        }

        // 3. 从表头下一行起解析数据
        List<RawRecord> records = new ArrayList<>();
        for (int i = headerIndex + 1; i < lines.length; i++) {
            String line = lines[i];
            if (!line.isBlank()) {
                parseLine(line, i, records);
            }
        }

        log.info("支付宝 CSV 解析完成: 文件 {}, 共 {} 条记录", filename, records.size());
        return records;
    }

    /** 读取输入流全部字节 */
    private byte[] readAllBytes(InputStream inputStream) {
        try {
            return inputStream.readAllBytes();
        } catch (Exception e) {
            throw new IllegalArgumentException("读取输入流失败: " + e.getMessage(), e);
        }
    }

    /** 自动识别编码：优先 UTF-8，失败尝试 GBK，均失败抛异常 */
    private String decodeContent(byte[] bytes, String filename) {
        String utf8 = decodeSafely(bytes, StandardCharsets.UTF_8);
        if (utf8 != null) {
            return utf8;
        }
        String gbk = decodeSafely(bytes, Charset.forName("GBK"));
        if (gbk != null) {
            return gbk;
        }
        throw new IllegalArgumentException("无法识别文件编码: " + filename);
    }

    /** 严格解码：遇到非法字节返回 null */
    private String decodeSafely(byte[] bytes, Charset charset) {
        try {
            CharsetDecoder decoder = charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            return decoder.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** 搜索表头行，返回其索引；未找到返回 -1 */
    private int findHeaderLine(String[] lines) {
        for (int i = 0; i < lines.length; i++) {
            if (lines[i] != null && lines[i].trim().equals(EXPECTED_HEADER)) {
                return i;
            }
        }
        return -1;
    }

    /** 解析单行：任一关键字段失败则 WARN 并跳过，不阻断整体 */
    private void parseLine(String line, int lineNo, List<RawRecord> records) {
        String[] fields = line.split(",", -1);
        if (fields.length < MIN_FIELD_COUNT) {
            log.warn("第 {} 行字段不足（{} 列），跳过", lineNo, fields.length);
            return;
        }

        LocalDateTime transTime = parseTime(fields[IDX_TRANS_TIME], lineNo);
        BigDecimal amount = parseAmount(fields[IDX_AMOUNT], lineNo);
        String direction = parseDirection(fields[IDX_DIRECTION], lineNo);

        if (transTime == null || amount == null || direction == null) {
            return;
        }

        String externalId = normalizeExternalId(fields[IDX_EXTERNAL_ID]);
        String counterparty = fields[IDX_COUNTERPARTY].trim();
        String remark = buildRemark(fields[IDX_REMARK], fields[IDX_PRODUCT]);

        records.add(new RawRecord(externalId, amount, DEFAULT_CURRENCY, direction,
                counterparty, remark, transTime, SOURCE_SYSTEM));
    }

    /** 解析交易时间，失败返回 null */
    private LocalDateTime parseTime(String raw, int lineNo) {
        try {
            return LocalDateTime.parse(raw.trim(), TIME_FORMATTER);
        } catch (Exception e) {
            log.warn("第 {} 行时间解析失败: {}", lineNo, raw);
            return null;
        }
    }

    /** 解析金额：去除 ¥/￥ 符号，正则提取数字部分并取绝对值，失败返回 null */
    private BigDecimal parseAmount(String raw, int lineNo) {
        String cleaned = raw.replace("¥", "").replace("￥", "").trim();
        Matcher matcher = AMOUNT_PATTERN.matcher(cleaned);
        if (!matcher.find()) {
            log.warn("第 {} 行金额解析失败: {}", lineNo, raw);
            return null;
        }
        try {
            return new BigDecimal(matcher.group()).abs();
        } catch (Exception e) {
            log.warn("第 {} 行金额解析失败: {}", lineNo, raw);
            return null;
        }
    }

    /** 校验方向：仅保留合法值（收入/支出/IN/OUT），非法返回 null */
    private String parseDirection(String raw, int lineNo) {
        if (raw == null || raw.isBlank()) {
            log.warn("第 {} 行方向为空，跳过", lineNo);
            return null;
        }
        String trimmed = raw.trim();
        if (!VALID_DIRECTIONS.contains(trimmed)) {
            // 打印完整行供排查（包含所有字段）
            log.warn("第 {} 行方向无法识别: 【{}】, 完整行: {}", lineNo, trimmed, raw);
            return null;
        }
        return trimmed;
    }

    /** 外部 ID 规范化：去除 Tab 等空白，空白返回 null */
    private String normalizeExternalId(String raw) {
        String trimmed = raw.replace("\t", "").trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 拼接备注 + 商品说明 */
    private String buildRemark(String remark, String product) {
        String r = remark == null ? "" : remark.trim();
        String p = product == null ? "" : product.trim();
        if (r.isEmpty()) {
            return p;
        }
        if (p.isEmpty()) {
            return r;
        }
        return r + " " + p;
    }
}
