/**
 *    Copyright 2009-2020 the original author or authors.
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
package org.apache.ibatis.reflection;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

public class ParamNameResolver {

  public static final String GENERIC_NAME_PREFIX = "param";
  
  /**
   * 是否启用了编译参数名称保留
   * test(int name,int age),如果开启了话，能读取到参数名称为name，age，可以省略@param注解，没保留就是arg0，arg1了
   */
  private final boolean useActualParamName;
  
  /**
   * 储存方法参数下标对应参数名称
   * <p>
   * The key is the index and the value is the name of the parameter.<br />
   * The name is obtained from {@link Param} if specified. When {@link Param} is not specified,
   * the parameter index is used. Note that this index could be different from the actual index
   * when the method has special parameters (i.e. {@link RowBounds} or {@link ResultHandler}).
   * </p>
   * <ul>
   * <li>aMethod(@Param("M") int a, @Param("N") int b) -&gt; {{0, "M"}, {1, "N"}}</li>
   * <li>aMethod(int a, int b) -&gt; {{0, "0"}, {1, "1"}}</li>
   * <li>aMethod(int a, RowBounds rb, int b) -&gt; {{0, "0"}, {2, "1"}}</li>
   * </ul>
   */
  private final SortedMap<Integer, String> names;
  
  /**
   * 是否标记了@Param注解标识
   */
  private boolean hasParamAnnotation;

  public ParamNameResolver(Configuration config, Method method) {
    this.useActualParamName = config.isUseActualParamName();  //是否开启了编译参数名称保留
    final Class<?>[] paramTypes = method.getParameterTypes(); //读取方法参数类型
    final Annotation[][] paramAnnotations = method.getParameterAnnotations(); //读取方法参数注解，这方法有点蛋疼，是个二维数组，第一层理解为参数长度
    final SortedMap<Integer, String> map = new TreeMap<>();
    int paramCount = paramAnnotations.length; //方法参数长度
    // get names from @Param annotations
    for (int paramIndex = 0; paramIndex < paramCount; paramIndex++) {
      if (isSpecialParameter(paramTypes[paramIndex])) {
        // 如果是特殊参数的话，就跳过。
        continue;
      }
      String name = null;
      //注解参数处理开始
      for (Annotation annotation : paramAnnotations[paramIndex]) {
        //判断当前参数是否使用了注解
        if (annotation instanceof Param) {
          hasParamAnnotation = true;  //切换标志位
          name = ((Param) annotation).value();  //参数名称就是注解value值了
          break;
        }
      }
      //注解参数处理结束
      //处理未标记@Param注解
      if (name == null) {
        if (useActualParamName) {
          //使用java8编译参数名称保留处理
          name = getActualParamName(method, paramIndex);
        }
        //当关闭了参数编译保留的情况，就需要用下标（从0开始，跳过了特殊参数，下标就用map的容量来维持增长）来标记了，但是没看懂为啥不是if else，难道上面还有返回null的情况？？？
        if (name == null) {
          // use the parameter index as the name ("0", "1", ...)
          // gcode issue #71
          name = String.valueOf(map.size());
        }
      }
      map.put(paramIndex, name);
    }
    names = Collections.unmodifiableSortedMap(map);
  }
  
  /**
   * 处理编译参数名称保留
   *
   * @param method     方法
   * @param paramIndex 参数索引
   * @return 参数名
   */
  private String getActualParamName(Method method, int paramIndex) {
    return ParamNameUtil.getParamNames(method).get(paramIndex);
  }
  
  /**
   * 判断是否为特殊参数（RowBounds or ResultHandler）
   *
   * @param clazz class
   * @return 是否特殊参数
   */
  private static boolean isSpecialParameter(Class<?> clazz) {
    return RowBounds.class.isAssignableFrom(clazz) || ResultHandler.class.isAssignableFrom(clazz);
  }
  
  /**
   * 获取所有参数名称
   * Returns parameter names referenced by SQL providers.
   *
   * @return the names
   */
  public String[] getNames() {
    return names.values().toArray(new String[0]);
  }

  /**
   * 将方法参数转换成一个（多参数的情况下）包装参数。
   * 情况一：无方法参数，返回null
   * 情况二：一个参数值，未标记@Param注解，如果是集合参数的话，包装成ParamMap，否则返回原value值
   * 情况三：一个参数值，标记@Param注解或多参数值，转为ParamMap
   * <p>
   * A single non-special parameter is returned without a name.
   * Multiple parameters are named using the naming rule.
   * In addition to the default names, this method also adds the generic names (param1, param2,
   * ...).
   * </p>
   *
   * @param args
   *          the args
   * @return the named params
   */
  public Object getNamedParams(Object[] args) {
    final int paramCount = names.size();
    //无参数情况
    if (args == null || paramCount == 0) {
      return null;
    } else if (!hasParamAnnotation && paramCount == 1) {
      //单参数无注解情况
      Object value = args[names.firstKey()];
      //如果是集合参数的话，包装成ParamMap。
      return wrapToMapIfCollection(value, useActualParamName ? names.get(0) : null);
    } else {
      //一个参数值，标记注解或多个参数值的情况
      final Map<String, Object> param = new ParamMap<>();
      int i = 0;
      //这里就会出现2N参数
      for (Map.Entry<Integer, String> entry : names.entrySet()) {
        //参数名称->参数值
        param.put(entry.getValue(), args[entry.getKey()]);
        // add generic param names (param1, param2, ...)
        final String genericParamName = GENERIC_NAME_PREFIX + (i + 1);
        // ensure not to overwrite parameter named with @Param
        if (!names.containsValue(genericParamName)) {
          //当原参数不存在通用参数，增加通用参数
          param.put(genericParamName, args[entry.getKey()]);
        }
        i++;
      }
      return param;
    }
  }

  /**
   * 如果是集合（array or collection）参数的话，包装成ParamMap
   * 情况一：当参数为collection的时候，会返回key值:collection,list,{actualParamName}
   * 情况二：当参数为array，返回key值:array,{actualParamName}
   * 情况三：当参数为其他值，返回原值
   *
   * Wrap to a {@link ParamMap} if object is {@link Collection} or array.
   *
   * @param object a parameter object 参数值
   * @param actualParamName an actual parameter name 参数名（当开启编译参数名称保留才有值）
   *                        (If specify a name, set an object to {@link ParamMap} with specified name)
   * @return a {@link ParamMap}
   * @since 3.5.5
   */
  public static Object wrapToMapIfCollection(Object object, String actualParamName) {
    if (object instanceof Collection) {
      ParamMap<Object> map = new ParamMap<>();
      map.put("collection", object);
      if (object instanceof List) {
        map.put("list", object);
      }
      Optional.ofNullable(actualParamName).ifPresent(name -> map.put(name, object));
      return map;
    } else if (object != null && object.getClass().isArray()) {
      ParamMap<Object> map = new ParamMap<>();
      map.put("array", object);
      Optional.ofNullable(actualParamName).ifPresent(name -> map.put(name, object));
      return map;
    }
    return object;
  }

}
