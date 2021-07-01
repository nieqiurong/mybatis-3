/**
 *    Copyright 2009-2018 the original author or authors.
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
import java.lang.reflect.Method;

import org.apache.ibatis.reflection.Reflector;

/**
 * @author Clinton Begin
 */
public class MethodInvoker implements Invoker {

  private final Class<?> type;
  private final Method method;

  public MethodInvoker(Method method) {
    this.method = method;
    // 理论上这里是操作get或set方法
    if (method.getParameterTypes().length == 1) {
      // 操作set方法,返回值类型参数值类型
      type = method.getParameterTypes()[0];
    } else {
      // get操作按方法返回值返回
      type = method.getReturnType();
    }
  }

  @Override
  public Object invoke(Object target, Object[] args) throws IllegalAccessException, InvocationTargetException {
    try {
      return method.invoke(target, args);
    } catch (IllegalAccessException e) {
      // 由于每次执行setAccessible会进行安全检查会比较耗时,这里在第一次调用类的方法反射操作会失败进入进来
      if (Reflector.canControlMemberAccessible()) {
        method.setAccessible(true);
        // 再次调用目标方法
        return method.invoke(target, args);
      } else {
        throw e;
      }
    }
  }

  @Override
  public Class<?> getType() {
    return type;
  }
}
