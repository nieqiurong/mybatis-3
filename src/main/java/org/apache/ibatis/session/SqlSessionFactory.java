/**
 *    Copyright 2009-2019 the original author or authors.
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
package org.apache.ibatis.session;

import java.sql.Connection;

/**
 * sqlSession工厂
 * Creates an {@link SqlSession} out of a connection or a DataSource
 *
 * @author Clinton Begin
 */
public interface SqlSessionFactory {
  
  /**
   * 打开sqlSession
   *
   * @return sqlSession
   */
  SqlSession openSession();
  
  /**
   * 打开sqlSession
   *
   * @param autoCommit 是否自动提交
   * @return sqlSession
   */
  SqlSession openSession(boolean autoCommit);
  
  /**
   * 打开sqlSession
   *
   * @param connection 数据库连接
   * @return sqlSession
   */
  SqlSession openSession(Connection connection);
  
  /**
   * 打开sqlSession
   *
   * @param level 事务隔离级别
   * @return sqlSession
   */
  SqlSession openSession(TransactionIsolationLevel level);
  
  /**
   * 打开sqlSession
   *
   * @param execType 执行器类型
   * @return sqlSession
   */
  SqlSession openSession(ExecutorType execType);
  
  /**
   * 打开sqlSession
   *
   * @param execType   执行器类型
   * @param autoCommit 是否自动提交
   * @return sqlSession
   */
  SqlSession openSession(ExecutorType execType, boolean autoCommit);
  
  /**
   * 打开sqlSession
   *
   * @param execType 执行器类型
   * @param level    事务隔离级别
   * @return sqlSession
   */
  SqlSession openSession(ExecutorType execType, TransactionIsolationLevel level);
  
  /**
   * 打开sqlSession
   *
   * @param execType   执行器类型
   * @param connection 数据库连接
   * @return sqlSession
   */
  SqlSession openSession(ExecutorType execType, Connection connection);
  
  /**
   * 获取Configuration对象信息
   *
   * @return configuration
   */
  Configuration getConfiguration();

}
