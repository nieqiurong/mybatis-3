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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;

import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

/**
 * 类元数据信息
 *
 * @author Clinton Begin
 */
public class MetaClass {

  /**
   * 反射工厂
   */
  private final ReflectorFactory reflectorFactory;
  /**
   * 反射操作
   */
  private final Reflector reflector;

  /**
   * 私有构造
   *
   * @param type             类信息
   * @param reflectorFactory 反射工厂
   */
  private MetaClass(Class<?> type, ReflectorFactory reflectorFactory) {
    this.reflectorFactory = reflectorFactory;
    this.reflector = reflectorFactory.findForClass(type);
  }

  /**
   * 创建类元数据信息
   *
   * @param type             类信息
   * @param reflectorFactory 反射工厂
   * @return 元数据信息
   */
  public static MetaClass forClass(Class<?> type, ReflectorFactory reflectorFactory) {
    return new MetaClass(type, reflectorFactory);
  }

  /**
   * 属性类元数据信息
   *
   * @param name 属性名称
   * @return 类元数据信息
   */
  public MetaClass metaClassForProperty(String name) {
    Class<?> propType = reflector.getGetterType(name);
    return MetaClass.forClass(propType, reflectorFactory);
  }

  /**
   * 查找对象属性
   * @param name 属性表达式
   * @return
   */
  public String findProperty(String name) {
    StringBuilder prop = buildProperty(name, new StringBuilder());
    return prop.length() > 0 ? prop.toString() : null;
  }

  /**
   * 查找对象属性
   * @param name 属性表达式
   * @param useCamelCaseMapping 是否驼峰命名
   * @return 属性访问
   */
  public String findProperty(String name, boolean useCamelCaseMapping) {
    if (useCamelCaseMapping) {
      name = name.replace("_", "");
    }
    return findProperty(name);
  }

  /**
   * 获取可读属性列表
   *
   * @return 可读属性
   */
  public String[] getGetterNames() {
    return reflector.getGetablePropertyNames();
  }

  /**
   * 获取可写属性列表
   *
   * @return 可写属性列表
   */
  public String[] getSetterNames() {
    return reflector.getSetablePropertyNames();
  }

  /**
   * 获取属性赋值参数值类型
   *
   * @param name 属性表达式
   * @return 参数类型
   */
  public Class<?> getSetterType(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      MetaClass metaProp = metaClassForProperty(prop.getName());
      return metaProp.getSetterType(prop.getChildren());
    } else {
      return reflector.getSetterType(prop.getName());
    }
  }

  /**
   * 获取属性访问返回值
   *
   * @param name 属性表达式
   * @return 返回值类型(这里单独处理了访问Collection元素, 如果有泛型, 则这里返回是泛型类型)
   */
  public Class<?> getGetterType(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      MetaClass metaProp = metaClassForProperty(prop);
      return metaProp.getGetterType(prop.getChildren());
    }
    // issue #506. Resolve the type inside a Collection Object
    return getGetterType(prop);
  }

  /**
   * 获取属性类元数据信息
   *
   * @param prop 分词器
   * @return 元数据信息
   */
  private MetaClass metaClassForProperty(PropertyTokenizer prop) {
    Class<?> propType = getGetterType(prop);
    return MetaClass.forClass(propType, reflectorFactory);
  }

  /**
   * 处理集合对象类型
   *
   * @param prop 分词器
   * @return 属性类型
   */
  private Class<?> getGetterType(PropertyTokenizer prop) {
    // 访问对象属性,获取返回值类型,当分词器访问集合元素时,需要处理泛型问题
    Class<?> type = reflector.getGetterType(prop.getName());
    // 这里存在两个问题,没写泛型的处理不了,map处理不了.
    if (prop.getIndex() != null && Collection.class.isAssignableFrom(type)) {
      //处理集合(泛型)访问情况
      Type returnType = getGenericGetterType(prop.getName());
      if (returnType instanceof ParameterizedType) {
        // 如果是泛型参数的话,要提取泛型 List<Demo>
        Type[] actualTypeArguments = ((ParameterizedType) returnType).getActualTypeArguments();
        if (actualTypeArguments != null && actualTypeArguments.length == 1) {
          //得到泛型值<?>
          returnType = actualTypeArguments[0];
          if (returnType instanceof Class) {
            // 具体泛型: Demo 返回
            type = (Class<?>) returnType;
          } else if (returnType instanceof ParameterizedType) {
            // 套中套 List<Map<String,String>> 得到类型就是map
            type = (Class<?>) ((ParameterizedType) returnType).getRawType();
          }
        }
      }
    }
    return type;
  }

  /**
   * 获取类属性访问返回值
   *
   * @param propertyName 属性名
   * @return 返回值
   */
  private Type getGenericGetterType(String propertyName) {
    try {
      Invoker invoker = reflector.getGetInvoker(propertyName);
      if (invoker instanceof MethodInvoker) {
        // 提供了get方法,获取get方法返回值
        Field declaredMethod = MethodInvoker.class.getDeclaredField("method");
        declaredMethod.setAccessible(true);
        Method method = (Method) declaredMethod.get(invoker);
        return TypeParameterResolver.resolveReturnType(method, reflector.getType());
      } else if (invoker instanceof GetFieldInvoker) {
        // 未提供访问属性方法,直接获取属性类型
        Field declaredField = GetFieldInvoker.class.getDeclaredField("field");
        declaredField.setAccessible(true);
        Field field = (Field) declaredField.get(invoker);
        return TypeParameterResolver.resolveFieldType(field, reflector.getType());
      }
    } catch (NoSuchFieldException | IllegalAccessException e) {
      // Ignored
    }
    return null;
  }

  /**
   * 判断是否有可操作属性方法
   *
   * @param name 属性表达式
   * @return 是否可操作
   */
  public boolean hasSetter(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      // 存在子访问,递归处理判断
      if (reflector.hasSetter(prop.getName())) {
        MetaClass metaProp = metaClassForProperty(prop.getName());
        return metaProp.hasSetter(prop.getChildren());
      } else {
        return false;
      }
    } else {
      // 没有分词,直接判断是否能操作到bean属性
      return reflector.hasSetter(prop.getName());
    }
  }

  /**
   * 判断是否有可访问属性方法
   *
   * @param name 属性表达式
   * @return 是否可访问
   */
  public boolean hasGetter(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      // 存在子访问,递归处理判断
      if (reflector.hasGetter(prop.getName())) {
        MetaClass metaProp = metaClassForProperty(prop);
        return metaProp.hasGetter(prop.getChildren());
      } else {
        return false;
      }
    } else {
      // 没有分词,直接判断是否能读取到bean属性
      return reflector.hasGetter(prop.getName());
    }
  }

  /**
   * 获取属性取值操作方法
   *
   * @param name 属性名
   * @return 取值方法
   */
  public Invoker getGetInvoker(String name) {
    return reflector.getGetInvoker(name);
  }

  /**
   * 获取属性赋值操作方法
   *
   * @param name 属性名
   * @return 赋值方法
   */
  public Invoker getSetInvoker(String name) {
    return reflector.getSetInvoker(name);
  }

  /**
   * 构建属性访问
   *
   * @param name    属性表达式 (user.name or name)
   * @param builder 字符串构建
   * @return builder
   */
  private StringBuilder buildProperty(String name, StringBuilder builder) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      // 分词情况,递归处理,拼接真实属性访问
      String propertyName = reflector.findPropertyName(prop.getName());
      if (propertyName != null) {
        builder.append(propertyName);
        builder.append(".");
        MetaClass metaProp = metaClassForProperty(propertyName);
        metaProp.buildProperty(prop.getChildren(), builder);
      }
    } else {
      // 没有分词情况,直接访问对象属性
      String propertyName = reflector.findPropertyName(name);
      if (propertyName != null) {
        builder.append(propertyName);
      }
    }
    return builder;
  }

  /**
   * 是否有默认构造
   *
   * @return 是否有默认构造
   */
  public boolean hasDefaultConstructor() {
    return reflector.hasDefaultConstructor();
  }

}
