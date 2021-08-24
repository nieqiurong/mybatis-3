/**
 * Copyright 2009-2019 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ibatis.builder;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeAliasRegistry;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * @author Clinton Begin
 */
public abstract class BaseBuilder {
  /**
   * mybatis配置对象
   */
  protected final Configuration configuration;
  /**
   * 类型别名注册对象
   */
  protected final TypeAliasRegistry typeAliasRegistry;
  /**
   * 类型处理器注册对象
   */
  protected final TypeHandlerRegistry typeHandlerRegistry;

  public BaseBuilder(Configuration configuration) {
    this.configuration = configuration;
    this.typeAliasRegistry = this.configuration.getTypeAliasRegistry();
    this.typeHandlerRegistry = this.configuration.getTypeHandlerRegistry();
  }

  /**
   * 获取mybatis配置对象
   *
   * @return mybatis配置对象
   */
  public Configuration getConfiguration() {
    return configuration;
  }

  /**
   * 解析正则表达式
   *
   * @param regex        正则
   * @param defaultValue 默认正则
   * @return 正则表达式
   */
  protected Pattern parseExpression(String regex, String defaultValue) {
    return Pattern.compile(regex == null ? defaultValue : regex);
  }

  /**
   * string转换boolean
   *
   * @param value        数值
   * @param defaultValue 默认值
   * @return Boolean
   */
  protected Boolean booleanValueOf(String value, Boolean defaultValue) {
    return value == null ? defaultValue : Boolean.valueOf(value);
  }

  /**
   * string转换Integer
   *
   * @param value        数值
   * @param defaultValue 默认数值
   * @return Integer
   */
  protected Integer integerValueOf(String value, Integer defaultValue) {
    return value == null ? defaultValue : Integer.valueOf(value);
  }

  /**
   * string参数转set集合
   *
   * @param value        value值(多值用,分隔)
   * @param defaultValue 默认值
   * @return set集合
   */
  protected Set<String> stringSetValueOf(String value, String defaultValue) {
    value = value == null ? defaultValue : value;
    return new HashSet<>(Arrays.asList(value.split(",")));
  }

  /**
   * 解析jdbc类型
   *
   * @param alias jdbc别名
   * @return jdbc类型
   */
  protected JdbcType resolveJdbcType(String alias) {
    if (alias == null) {
      return null;
    }
    try {
      return JdbcType.valueOf(alias);
    } catch (IllegalArgumentException e) {
      throw new BuilderException("Error resolving JdbcType. Cause: " + e, e);
    }
  }

  /**
   * 解析ResultSetType
   *
   * @param alias ResultSetType名称
   * @return ResultSetType
   */
  protected ResultSetType resolveResultSetType(String alias) {
    if (alias == null) {
      return null;
    }
    try {
      return ResultSetType.valueOf(alias);
    } catch (IllegalArgumentException e) {
      throw new BuilderException("Error resolving ResultSetType. Cause: " + e, e);
    }
  }

  /**
   * 解析参数类型
   *
   * @param alias 枚举名
   * @return 参数类型
   */
  protected ParameterMode resolveParameterMode(String alias) {
    if (alias == null) {
      return null;
    }
    try {
      return ParameterMode.valueOf(alias);
    } catch (IllegalArgumentException e) {
      throw new BuilderException("Error resolving ParameterMode. Cause: " + e, e);
    }
  }

  /**
   * 创建实例
   *
   * @param alias 全类名or别名(需要有无参构造)
   * @return 实例信息
   */
  protected Object createInstance(String alias) {
    Class<?> clazz = resolveClass(alias); //解析获取类信息
    if (clazz == null) {
      //无类信息返回空(一般是alias参数为空的情况)
      return null;
    }
    try {
      //调用无参构造创建实例
      return clazz.getDeclaredConstructor().newInstance();
    } catch (Exception e) {
      throw new BuilderException("Error creating instance. Cause: " + e, e);
    }
  }

  /**
   * 解析class
   *
   * @param alias (全类名or别名)
   * @param <T>   泛型
   * @return 类信息
   */
  protected <T> Class<? extends T> resolveClass(String alias) {
    if (alias == null) {
      return null;
    }
    try {
      return resolveAlias(alias);
    } catch (Exception e) {
      throw new BuilderException("Error resolving class. Cause: " + e, e);
    }
  }

  /**
   * 解析类型处理器
   *
   * @param javaType         java类型
   * @param typeHandlerAlias 类型处理器全类名或别名
   * @return 类型处理器
   */
  protected TypeHandler<?> resolveTypeHandler(Class<?> javaType, String typeHandlerAlias) {
    if (typeHandlerAlias == null) {
      //无类型处理器.返回空
      return null;
    }
    Class<?> type = resolveClass(typeHandlerAlias); //解析类型处理器信息
    if (type != null && !TypeHandler.class.isAssignableFrom(type)) {
      //如果类不是TypeHandler的子类,抛出异常
      throw new BuilderException("Type " + type.getName() + " is not a valid TypeHandler because it does not implement TypeHandler interface");
    }
    @SuppressWarnings("unchecked") // already verified it is a TypeHandler
    Class<? extends TypeHandler<?>> typeHandlerType = (Class<? extends TypeHandler<?>>) type;
    return resolveTypeHandler(javaType, typeHandlerType);
  }

  /**
   * 引用类型处理器信息
   *
   * @param javaType        java类型
   * @param typeHandlerType 类型处理器类信息
   * @return 类型处理器实例
   */
  protected TypeHandler<?> resolveTypeHandler(Class<?> javaType, Class<? extends TypeHandler<?>> typeHandlerType) {
    if (typeHandlerType == null) {
      //快速返回
      return null;
    }
    // javaType ignored for injected handlers see issue #746 for full detail
    // 先判断类型处理器是否注册过
    TypeHandler<?> handler = typeHandlerRegistry.getMappingTypeHandler(typeHandlerType);
    if (handler == null) {
      // 没有实例,注册一个类型处理器实例
      handler = typeHandlerRegistry.getInstance(javaType, typeHandlerType);
    }
    return handler;
  }

  /**
   * 引用类信息
   *
   * @param alias 全类名或别名
   * @param <T>   泛型
   * @return 类信息
   */
  protected <T> Class<? extends T> resolveAlias(String alias) {
    return typeAliasRegistry.resolveAlias(alias);
  }
}
