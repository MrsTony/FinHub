task: 实现 FingerprintGeneratorImpl 的 generate 方法

范围：
- 实现文件：src/main/java/com/finhub/fundflow/infrastructure/service/FingerprintGeneratorImpl.java
- 接口定义：com.finhub.fundflow.domain.service.FingerprintGenerator（禁止修改）
- 禁止修改：接口文件、测试文件

要求：
1. 算法：SHA-256(金额截断精度 + "|" + 时间截断到分钟 + "|" + 对方户名标准化 + "|" + 备注空值占位 + "|" + 盐值)
2. 金额截断：使用 Money.amount()（已强制精度 2 位小数），toPlainString()
3. 时间截断：transTime.truncatedTo(ChronoUnit.MINUTES)
4. 对方户名标准化：去除前后空格、转小写、去除特殊字符（只保留字母数字汉字）
5. 备注空值占位：null 或 blank → "__EMPTY__"
6. 盐值：直接拼接，不处理
7. 返回：new Fingerprint(hashHexString, salt)
8. 异常：任何步骤 null 输入 → NullPointerException，标准化失败 → IllegalArgumentException
9. 使用 java.security.MessageDigest，不引入外部库
10. 完成后执行：mvn test -Dtest=FingerprintGeneratorImplTest

检查点：完成后暂停，等待我 review