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

/**
 * SQL命令类型
 * @author Clinton Begin
 */
public enum SqlCommandType {

  /**
   * 未知命令，这个执行就会报错了
   */
  UNKNOWN,
  /**
   * 新增命令，对应xml<insert></insert>
   */
  INSERT,
  /**
   * 更新命令，对应xml<update></update>
   */
  UPDATE,
  /**
   * 删除命令，对应xml<delete></delete>
   */
  DELETE,
  /**
   * 查询命令，对应xml<select></select>
   */
  SELECT,
  /**
   * 刷新命令，主要用来批处理，需要在mapper方法上标记{@link org.apache.ibatis.annotations.Flush}
   */
  FLUSH

}
