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
package org.apache.ibatis.mapping;

import org.apache.ibatis.executor.statement.CallableStatementHandler;
import org.apache.ibatis.executor.statement.PreparedStatementHandler;
import org.apache.ibatis.executor.statement.SimpleStatementHandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.Statement;

/**
 * 执行SQL语句类型
 * @author Clinton Begin
 */
public enum StatementType {
  /**
   * 普通执行，等同于使用{@link Statement}进行sql执行。{@link SimpleStatementHandler}
   */
  STATEMENT,
  /**
   * 预编译处理，等同于使用{@link PreparedStatement}进行sql执行。{@link PreparedStatementHandler}
   */
  PREPARED,
  /**
   * 存储过程，等同于使用{@link CallableStatement}进行sql执行{@link CallableStatementHandler}
   */
  CALLABLE
}
