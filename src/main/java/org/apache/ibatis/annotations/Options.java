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
package org.apache.ibatis.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.mapping.StatementType;

/**
 * 属性选项
 * The annotation that specify options for customizing default behaviors.
 *
 * <p>
 * <b>How to use:</b>
 *
 * <pre>
 * public interface UserMapper {
 *   &#064;Option(useGeneratedKeys = true, keyProperty = "id")
 *   &#064;Insert("INSERT INTO users (name) VALUES(#{name})")
 *   boolean insert(User user);
 * }
 * </pre>
 *
 * @author Clinton Begin
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(Options.List.class)
public @interface Options {
  /**
   * 缓存刷新策略
   * The options for the {@link Options#flushCache()}.
   * The default is {@link FlushCachePolicy#DEFAULT}
   */
  enum FlushCachePolicy {
    /**
     * <code>false</code> 默认行为,查询不刷新,更新操作刷新. for select statement; <code>true</code> for insert/update/delete statement.
     */
    DEFAULT,
    /**
     * 等于强制刷新 Flushes cache regardless of the statement type.
     */
    TRUE,
    /**
     * 不刷新缓存, Does not flush cache regardless of the statement type.
     */
    FALSE
  }

  /**
   * 是否使用缓存
   * Returns whether use the 2nd cache feature if assigned the cache.
   *
   * @return {@code true} if use; {@code false} if otherwise
   */
  boolean useCache() default true;

  /**
   * 缓存刷新策略
   * Returns the 2nd cache flush strategy.
   *
   * @return the 2nd cache flush strategy
   */
  FlushCachePolicy flushCache() default FlushCachePolicy.DEFAULT;

  /**
   * 结果集滚动类型
   * Returns the result set type.
   *
   * @return the result set type
   */
  ResultSetType resultSetType() default ResultSetType.DEFAULT;

  /**
   * 语句类型
   * Return the statement type.
   *
   * @return the statement type
   */
  StatementType statementType() default StatementType.PREPARED;

  /**
   * 抓取结果集数量
   * Returns the fetch size.
   *
   * @return the fetch size
   */
  int fetchSize() default -1;

  /**
   * 超时时间
   * Returns the statement timeout.
   *
   * @return the statement timeout
   */
  int timeout() default -1;

  /**
   * 是否生成主键
   * Returns whether use the generated keys feature supported by JDBC 3.0
   *
   * @return {@code true} if use; {@code false} if otherwise
   */
  boolean useGeneratedKeys() default false;

  /**
   * 键值属性名,多个用,分隔开来
   * Returns property names that holds a key value.
   * <p>
   * If you specify multiple property, please separate using comma(',').
   * </p>
   *
   * @return property names that separate with comma(',')
   */
  String keyProperty() default "";

  /**
   * 键值字段名,多个用,分隔开来
   * Returns column names that retrieves a key value.
   * <p>
   * If you specify multiple column, please separate using comma(',').
   * </p>
   *
   * @return column names that separate with comma(',')
   */
  String keyColumn() default "";

  /**
   * 结果集名,多个用,分隔开来
   * Returns result set names.
   * <p>
   * If you specify multiple result set, please separate using comma(',').
   * </p>
   *
   * @return result set names that separate with comma(',')
   */
  String resultSets() default "";

  /**
   * 数据库厂商id
   *
   * @return A database id that correspond this options
   * @since 3.5.5
   */
  String databaseId() default "";

  /**
   * 组合注解
   * @Options.List({@Options(databaseId = "hsql"),@Options(databaseId = "derby"),@Options(flushCache = Options.FlushCachePolicy.TRUE)})
   *  等于
   *  @Options(databaseId = "hsql")
   *  @Options(databaseId = "derby")
   *  @Options(flushCache = Options.FlushCachePolicy.TRUE)
   * The container annotation for {@link Options}.
   * @author Kazuki Shimizu
   * @since 3.5.5
   */
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  @interface List {
    Options[] value();
  }

}
