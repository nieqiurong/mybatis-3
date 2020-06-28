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
package org.apache.ibatis.plugin;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.reflection.ExceptionUtil;

/**
 * 插件（动态代理实现）
 *
 * @author Clinton Begin
 */
public class Plugin implements InvocationHandler {

  /**
   * 目标对象
   */
  private final Object target;
  /**
   * 拦截器
   */
  private final Interceptor interceptor;
  /**
   * 签名方法
   */
  private final Map<Class<?>, Set<Method>> signatureMap;

  private Plugin(Object target, Interceptor interceptor, Map<Class<?>, Set<Method>> signatureMap) {
    this.target = target;
    this.interceptor = interceptor;
    this.signatureMap = signatureMap;
  }

  /**
   * 生成动态代理对象
   *
   * @param target      目标对象
   * @param interceptor 拦截器
   * @return 目标方法返回值
   */
  public static Object wrap(Object target, Interceptor interceptor) {
    //这也就是为啥自定义插件需要重写一下plugin方法来instanceof匹配上来再来调用wrap原因，因为如果不是当前需要拦截的对象，就无需去解析注解了
    Map<Class<?>, Set<Method>> signatureMap = getSignatureMap(interceptor); //解析拦截器上注解
    Class<?> type = target.getClass();
    Class<?>[] interfaces = getAllInterfaces(type, signatureMap); //解析目标对象需要拦截的接口
    if (interfaces.length > 0) {
      //如果有的话，则生成代理类
      return Proxy.newProxyInstance(
        type.getClassLoader(),
        interfaces,
        new Plugin(target, interceptor, signatureMap));
    }
    //否则直接返回目标对象
    return target;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    try {
      //这里注意一下，并不是接口的方法都是能拦截的，得通过明确的代理对象调用方法才能拦截，比如通过Executor的createCacheKey就是内部自己调用的
      Set<Method> methods = signatureMap.get(method.getDeclaringClass());
      if (methods != null && methods.contains(method)) {  //判断目标方法是不是包含拦截方法
        return interceptor.intercept(new Invocation(target, method, args)); //执行插件逻辑
      }
      return method.invoke(target, args); //方法名或者拦截对象匹配不上，调用目标方法
    } catch (Exception e) {
      throw ExceptionUtil.unwrapThrowable(e);
    }
  }

  /**
   * 获取拦截器上拦截方法集合
   *
   * @param interceptor 拦截器
   * @return 方法集合 （key也就是Executor，ParameterHandler，ResultSetHandler，StatementHandler四大对象）
   */
  private static Map<Class<?>, Set<Method>> getSignatureMap(Interceptor interceptor) {
    Intercepts interceptsAnnotation = interceptor.getClass().getAnnotation(Intercepts.class);
    // issue #251
    if (interceptsAnnotation == null) {
      throw new PluginException("No @Intercepts annotation was found in interceptor " + interceptor.getClass().getName());
    }
    Signature[] sigs = interceptsAnnotation.value();
    Map<Class<?>, Set<Method>> signatureMap = new HashMap<>();
    for (Signature sig : sigs) {
      Set<Method> methods = signatureMap.computeIfAbsent(sig.type(), k -> new HashSet<>());
      try {
        //这里注意一下，由于是拦截接口的，所有方法都是public的，如果我们自己写普通类拦截的话进行测试玩的话，非public方法会报方法找不到的
        Method method = sig.type().getMethod(sig.method(), sig.args());
        methods.add(method);
      } catch (NoSuchMethodException e) {
        throw new PluginException("Could not find method on " + sig.type() + " named " + sig.method() + ". Cause: " + e, e);
      }
    }
    return signatureMap;
  }

  /**
   * 获取当前目标需要拦截的接口
   *
   * @param type         class
   * @param signatureMap 方法签名
   * @return 接口集合
   */
  private static Class<?>[] getAllInterfaces(Class<?> type, Map<Class<?>, Set<Method>> signatureMap) {
    Set<Class<?>> interfaces = new HashSet<>();
    while (type != null) {
      //例如CachingExecutor实现了Executor接口
      for (Class<?> c : type.getInterfaces()) {
        if (signatureMap.containsKey(c)) {
          interfaces.add(c);
        }
      }
      type = type.getSuperclass();
    }
    return interfaces.toArray(new Class<?>[interfaces.size()]);
  }

}
