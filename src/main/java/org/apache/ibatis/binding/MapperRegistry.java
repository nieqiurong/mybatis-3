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
package org.apache.ibatis.binding;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.builder.annotation.MapperAnnotationBuilder;
import org.apache.ibatis.io.ResolverUtil;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;

/**
 * Mapper注册对象
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 * @author Lasse Voss
 */
public class MapperRegistry {

  /**
   * mybatis全局配置对象
   */
  private final Configuration config;
  /**
   * 注册mapper集合
   */
  private final Map<Class<?>, MapperProxyFactory<?>> knownMappers = new HashMap<>();

  public MapperRegistry(Configuration config) {
    this.config = config;
  }

  /**
   * 获取mapper的动态代理对象
   *
   * @param type       mapperClass
   * @param sqlSession sqlSession
   * @param <T>        T
   * @return Proxy
   */
  @SuppressWarnings("unchecked")
  public <T> T getMapper(Class<T> type, SqlSession sqlSession) {
    final MapperProxyFactory<T> mapperProxyFactory = (MapperProxyFactory<T>) knownMappers.get(type);
    if (mapperProxyFactory == null) { //没有注册过的mapper,直接抛出异常
      throw new BindingException("Type " + type + " is not known to the MapperRegistry.");
    }
    try {
      return mapperProxyFactory.newInstance(sqlSession);  //创建mapper动态代理
    } catch (Exception e) {
      throw new BindingException("Error getting mapper instance. Cause: " + e, e);
    }
  }

  /**
   * 判断当前mapperClass是否被注册过
   *
   * @param type mapperClass
   * @return 是否已注册
   */
  public <T> boolean hasMapper(Class<T> type) {
    return knownMappers.containsKey(type);
  }

  /**
   * 注册mapper
   *
   * @param type mapperClass
   * @param <T>  泛型
   */
  public <T> void addMapper(Class<T> type) {
    if (type.isInterface()) { //只代理接口
      if (hasMapper(type)) {  //防止重复注册,如果已经注册过了,则抛出异常.
        throw new BindingException("Type " + type + " is already known to the MapperRegistry.");
      }
      boolean loadCompleted = false;  //加载完成标志位
      try {
        /*
          这里有个情况,就是在解析的时候会去加载一次xml(org.apache.ibatis.builder.annotation.MapperAnnotationBuilder.loadXmlResource)
          如果定义了xml,那又会进行xml解析,继续调用命名空间注册,那就会形成一个环.
          所以这里提前写入,然后再注册命名空间的时候就不会形成一个环(org.apache.ibatis.builder.xml.XMLMapperBuilder.bindMapperForNamespace)
        */
        knownMappers.put(type, new MapperProxyFactory<>(type));
        // It's important that the type is added before the parser is run
        // otherwise the binding may automatically be attempted by the
        // mapper parser. If the type is already known, it won't try.
        MapperAnnotationBuilder parser = new MapperAnnotationBuilder(config, type);
        parser.parse();
        loadCompleted = true; //切换标志位
      } finally {
        if (!loadCompleted) { //加载错误的需要移除掉
          knownMappers.remove(type);
        }
      }
    }
  }

  /**
   * Gets the mappers.
   * 获取所有注册mapper
   * @return the mappers
   * @since 3.2.2
   */
  public Collection<Class<?>> getMappers() {
    return Collections.unmodifiableCollection(knownMappers.keySet());
  }

  /**
   * Adds the mappers.
   * 根据包名和父类接口扫描注册mapper
   *
   * @param packageName the package name 包名
   * @param superType   the super type 父类接口
   * @since 3.2.2
   */
  public void addMappers(String packageName, Class<?> superType) {
    ResolverUtil<Class<?>> resolverUtil = new ResolverUtil<>();
    resolverUtil.find(new ResolverUtil.IsA(superType), packageName);  //通过包名查找指定接口类型的子类
    Set<Class<? extends Class<?>>> mapperSet = resolverUtil.getClasses();
    for (Class<?> mapperClass : mapperSet) {
      addMapper(mapperClass); //获取所有class遍历注册
    }
  }

  /**
   * Adds the mappers.
   * 根据包名注册mapper
   *
   * @param packageName 包名
   *                    the package name
   * @since 3.2.2
   */
  public void addMappers(String packageName) {
    addMappers(packageName, Object.class);
  }

}
