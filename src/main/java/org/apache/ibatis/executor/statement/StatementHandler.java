/**
 *    Copyright 2009-2016 the original author or authors.
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
package org.apache.ibatis.executor.statement;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.ResultHandler;

/**
 * @author Clinton Begin
 */
public interface StatementHandler {

  /**
   * 获取Statement对象
   *
   * @param connection         连接对象
   * @param transactionTimeout 事务提交超时时间
   * @return java.sql.Statement
   * @throws SQLException sqlException
   */
  Statement prepare(Connection connection, Integer transactionTimeout)
      throws SQLException;

  /**
   * 设置Statement执行参数
   *
   * @param statement java.sql.Statement
   * @throws SQLException sqlException
   */
  void parameterize(Statement statement)
      throws SQLException;

  /**
   * jdbc批处理
   *
   * @param statement java.sql.Statement
   * @throws SQLException sqlException
   */
  void batch(Statement statement)
      throws SQLException;

  /**
   * jdbc更新
   *
   * @param statement java.sql.Statement
   * @return 影响行数
   * @throws SQLException sqlException
   */
  int update(Statement statement)
      throws SQLException;

  /**
   * 普通sql查询
   *
   * @param statement     java.sql.Statement
   * @param resultHandler 结果集处理器
   * @param <E>           泛型
   * @return 结果集
   * @throws SQLException sqlException
   */
  <E> List<E> query(Statement statement, ResultHandler resultHandler)
      throws SQLException;

  /**
   * 游标查询
   *
   * @param statement java.sql.Statement
   * @param <E>       泛型
   * @return 游标
   * @throws SQLException sqlException
   */
  <E> Cursor<E> queryCursor(Statement statement)
      throws SQLException;

  /**
   * 获取执行SQL对象
   *
   * @return SQL参数对象
   */
  BoundSql getBoundSql();

  /**
   * 获取参数处理器
   *
   * @return 参数处理器
   */
  ParameterHandler getParameterHandler();

}
