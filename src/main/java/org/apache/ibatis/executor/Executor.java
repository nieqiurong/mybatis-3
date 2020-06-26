/**
 *    Copyright 2009-2015 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.executor;

import java.sql.SQLException;
import java.util.List;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;

/**
 * @author Clinton Begin
 */
public interface Executor {

  ResultHandler NO_RESULT_HANDLER = null;

  int update(MappedStatement ms, Object parameter) throws SQLException;

  <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, CacheKey cacheKey, BoundSql boundSql) throws SQLException;

  <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler) throws SQLException;

  <E> Cursor<E> queryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds) throws SQLException;

  List<BatchResult> flushStatements() throws SQLException;

  void commit(boolean required) throws SQLException;

  /**
   * 回滚事务
   *
   * @param required 标志位
   * @throws SQLException SQLException
   */
  void rollback(boolean required) throws SQLException;

  /**
   * 创建缓存Key
   *
   * @param ms              MappedStatement
   * @param parameterObject 参数
   * @param rowBounds       RowBounds
   * @param boundSql        BoundSql
   * @return 缓存Key
   */
  CacheKey createCacheKey(MappedStatement ms, Object parameterObject, RowBounds rowBounds, BoundSql boundSql);

  /**
   * 是否命中一级缓存
   *
   * @param ms  MappedStatement
   * @param key CacheKey
   * @return 是否缓存
   */
  boolean isCached(MappedStatement ms, CacheKey key);

  /**
   * 清理一级缓存数据
   */
  void clearLocalCache();

  /**
   * 延迟加载
   *
   * @param ms           MappedStatement
   * @param resultObject 元数据信息
   * @param property     属性
   * @param key          缓存Key
   * @param targetType   返回值类型
   */
  void deferLoad(MappedStatement ms, MetaObject resultObject, String property, CacheKey key, Class<?> targetType);

  /**
   * 获取事务
   *
   * @return Transaction
   */
  Transaction getTransaction();

  /**
   * 关闭执行器
   *
   * @param forceRollback 是否强制回滚事务
   */
  void close(boolean forceRollback);

  /**
   * 判断执行器是否关闭
   *
   * @return 是否关闭
   */
  boolean isClosed();

  void setExecutorWrapper(Executor executor);

}
