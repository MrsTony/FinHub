package com.finhub.fundflow.infrastructure.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finhub.fundflow.infrastructure.repository.po.TransactionPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@link TransactionPO} 的 MyBatis-Plus Mapper。
 *
 * <p>继承 {@link BaseMapper} 获得标准 CRUD（selectById/selectOne/selectList/insert/selectCount 等），
 * 复杂查询走 LambdaQueryWrapper，无需手写 XML。</p>
 */
@Mapper
public interface TransactionMapper extends BaseMapper<TransactionPO> {
}
