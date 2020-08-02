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
package org.apache.ibatis.mapping;

import java.sql.SQLException;
import java.util.Properties;

import javax.sql.DataSource;

/**
 * 数据库厂商处理接口
 * Should return an id to identify the type of this database.
 * That id can be used later on to build different queries for each database type
 * This mechanism enables supporting multiple vendors or versions
 *
 * @author Eduardo Macarron
 */
public interface DatabaseIdProvider {
  
  /**
   * 设置属性
   *
   * @param p 配置属性
   */
  default void setProperties(Properties p) {
    // NOP
  }
  
  /**
   * 获取数据源厂商Id
   *
   * @param dataSource 数据源
   * @return 厂商ID
   * @throws SQLException exception
   */
  String getDatabaseId(DataSource dataSource) throws SQLException;
}
