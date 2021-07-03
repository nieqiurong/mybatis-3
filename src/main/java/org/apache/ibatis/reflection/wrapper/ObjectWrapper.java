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
package org.apache.ibatis.reflection.wrapper;

import java.util.List;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

/**
 * 对象包装处理
 *
 * @author Clinton Begin
 */
public interface ObjectWrapper {

  /**
   * 获取对象属性
   *
   * @param prop 属性标记
   * @return 属性值
   */
  Object get(PropertyTokenizer prop);

  /**
   * 设置对象属性
   *
   * @param prop  属性
   * @param value 属性值
   */
  void set(PropertyTokenizer prop, Object value);

  /**
   * 获取对象属性名称
   * @param name 属性名称
   * @param useCamelCaseMapping 是否大小写敏感
   * @return 属性
   */
  String findProperty(String name, boolean useCamelCaseMapping);

  /**
   * 获取get属性名称列表
   * @return 属性名称列表
   */
  String[] getGetterNames();

  /**
   * 获取set属性名称列表
   * @return 属性名称列表
   */
  String[] getSetterNames();

  /**
   * 获取set参数类型
   * @param name 属性名称
   * @return 参数类型
   */
  Class<?> getSetterType(String name);

  /**
   * 获取get属性返回值
   * @param name 属性名称
   * @return 返回值
   */
  Class<?> getGetterType(String name);

  /**
   * 是否存在set方法
   *
   * @param name 属性名称
   * @return 是否存在
   */
  boolean hasSetter(String name);

  /**
   * 是否存在get方法
   *
   * @param name 属性名称
   * @return 是否存在
   */
  boolean hasGetter(String name);

  MetaObject instantiatePropertyValue(String name, PropertyTokenizer prop, ObjectFactory objectFactory);

  /**
   * 判断是否为集合
   *
   * @return 是否为集合
   */
  boolean isCollection();

  /**
   * 添加一个元素
   *
   * @param element 元素
   */
  void add(Object element);

  /**
   * 添加一个集合
   *
   * @param element 集合列表
   * @param <E>     泛型
   */
  <E> void addAll(List<E> element);

}
