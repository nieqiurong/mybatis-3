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

/**
 * 结果集标志位
 * 构造:
 * <constructor>
 * <idArg column="id" javaType="int" name="id" />
 * <arg column="age" javaType="_int" name="age" />
 * <arg column="username" javaType="String" name="username" />
 * </constructor>
 * 主键:
 * <id property="id" column="post_id"/>
 *
 * @author Clinton Begin
 */
public enum ResultFlag {

  /**
   * 主键
   */
  ID,
  /**
   * 构造器
   */
  CONSTRUCTOR

}
