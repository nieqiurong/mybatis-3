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
package org.apache.ibatis.reflection.invoker;

import java.lang.reflect.InvocationTargetException;

/**
 * 反射操作接口
 *
 * @author Clinton Begin
 */
public interface Invoker {

  /**
   * 反射调用对象方法或属性
   *
   * @param target 目标对象
   * @param args   方法参数
   * @return 目标方法返回值或属性值
   * @throws IllegalAccessException    IllegalAccessException
   * @throws InvocationTargetException InvocationTargetException
   */
  Object invoke(Object target, Object[] args) throws IllegalAccessException, InvocationTargetException;

  /**
   * 目标方法参数或返回值类型
   * 1.操作字段,返回值为字段类型
   * 2.操作set方法,类型为方法参数类型
   * 3.操作get方法,类型为方法返回值类型
   *
   * @return 类型
   */
  Class<?> getType();
}
