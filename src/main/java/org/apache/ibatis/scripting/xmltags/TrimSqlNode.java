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
package org.apache.ibatis.scripting.xmltags;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.ibatis.session.Configuration;

/**
 *
 * <trim prefix="WHERE" prefixOverrides="AND |OR ">
 * </trim>
 *
 * <trim prefix="SET" suffixOverrides=",">
 * </trim>
 * @author Clinton Begin
 */
public class TrimSqlNode implements SqlNode {

  /**
   * 节点内容
   */
  private final SqlNode contents;
  /***
   * 前缀
   */
  private final String prefix;
  /**
   * 后缀
   */
  private final String suffix;
  /**
   * 前缀覆盖
   */
  private final List<String> prefixesToOverride;
  /**
   * 后缀覆盖
   */
  private final List<String> suffixesToOverride;
  private final Configuration configuration;

  public TrimSqlNode(Configuration configuration, SqlNode contents, String prefix, String prefixesToOverride, String suffix, String suffixesToOverride) {
    this(configuration, contents, prefix, parseOverrides(prefixesToOverride), suffix, parseOverrides(suffixesToOverride));
  }

  protected TrimSqlNode(Configuration configuration, SqlNode contents, String prefix, List<String> prefixesToOverride, String suffix, List<String> suffixesToOverride) {
    this.contents = contents;
    this.prefix = prefix;
    this.prefixesToOverride = prefixesToOverride;
    this.suffix = suffix;
    this.suffixesToOverride = suffixesToOverride;
    this.configuration = configuration;
  }

  @Override
  public boolean apply(DynamicContext context) {
    FilteredDynamicContext filteredDynamicContext = new FilteredDynamicContext(context);
    //执行sql节点应用,但这个时候是委托到FilteredDynamicContext上的,应用的sql是不会影响到原真实节点内容的
    boolean result = contents.apply(filteredDynamicContext);
    filteredDynamicContext.applyAll();
    return result;
  }

  private static List<String> parseOverrides(String overrides) {
    if (overrides != null) {
      final StringTokenizer parser = new StringTokenizer(overrides, "|", false);
      final List<String> list = new ArrayList<>(parser.countTokens());
      while (parser.hasMoreTokens()) {
        list.add(parser.nextToken().toUpperCase(Locale.ENGLISH));
      }
      return list;
    }
    return Collections.emptyList();
  }

  private class FilteredDynamicContext extends DynamicContext {
    /**
     * 代理对象
     */
    private DynamicContext delegate;
    /**
     * 前缀应用标志位
     */
    private boolean prefixApplied;
    /**
     * 后缀应用标志位
     */
    private boolean suffixApplied;
    /**
     * 新建一个sql构建者来记录后面的sql应用
     */
    private StringBuilder sqlBuffer;

    public FilteredDynamicContext(DynamicContext delegate) {
      super(configuration, null);
      this.delegate = delegate;
      this.prefixApplied = false;
      this.suffixApplied = false;
      this.sqlBuffer = new StringBuilder();
    }

    public void applyAll() {
      sqlBuffer = new StringBuilder(sqlBuffer.toString().trim());
      String trimmedUppercaseSql = sqlBuffer.toString().toUpperCase(Locale.ENGLISH);
      //如果后面的子标签节点有匹配成功,则会生成sql节点内容,所以当长度大于0的时候,则要处理前后缀.
      if (trimmedUppercaseSql.length() > 0) {
        applyPrefix(sqlBuffer, trimmedUppercaseSql);
        applySuffix(sqlBuffer, trimmedUppercaseSql);
      }
      //将生成的sql内容追加回原代理节点上
      delegate.appendSql(sqlBuffer.toString());
    }

    @Override
    public Map<String, Object> getBindings() {
      return delegate.getBindings();
    }

    @Override
    public void bind(String name, Object value) {
      delegate.bind(name, value);
    }

    @Override
    public int getUniqueNumber() {
      return delegate.getUniqueNumber();
    }

    @Override
    public void appendSql(String sql) {
      sqlBuffer.append(sql);
    }

    @Override
    public String getSql() {
      return delegate.getSql();
    }

    /**
     * 前缀处理
     *
     * @param sql                 原sql构建者
     * @param trimmedUppercaseSql 原sql文本
     */
    private void applyPrefix(StringBuilder sql, String trimmedUppercaseSql) {
      if (!prefixApplied) {
        // 标记已处理完成
        prefixApplied = true;
        //如果有前缀覆盖的话,移除掉前缀文本,例如当where标签中if子标签有成立项,则需要移除前缀AND
        //其他标签见 org.apache.ibatis.scripting.xmltags.WhereSqlNode.prefixList,然后应用前缀
        /*
            <where>
                  <if test="state != null">
                      AND state = #{state}
                  </if>
                  <if test="title != null">
                      AND title like #{title}
                  </if>
                  <if test="author != null and author.name != null">
                      AND author_name like #{author.name}
                  </if>
           </where>
         */
        if (prefixesToOverride != null) {
          for (String toRemove : prefixesToOverride) {
            if (trimmedUppercaseSql.startsWith(toRemove)) {
              sql.delete(0, toRemove.trim().length());
              break;
            }
          }
        }
        //前缀不为空的情况下,插入前缀至sql前
        if (prefix != null) {
          sql.insert(0, " ");
          sql.insert(0, prefix);
        }
      }
    }

    /**
     * 后缀处理
     * @param sql 原sql构建者
     * @param trimmedUppercaseSql 原sql文本内容
     */
    private void applySuffix(StringBuilder sql, String trimmedUppercaseSql) {
      if (!suffixApplied) {
        //标记后缀处理完成
        suffixApplied = true;
        //如果有后缀覆盖的话,则要移除后缀暖色
        /*
              <set>
                    <if test="username != null">username=#{username},</if>
                    <if test="password != null">password=#{password},</if>
                    <if test="email != null">email=#{email},</if>
                    <if test="bio != null">bio=#{bio},</if>
              </set>
         */
        if (suffixesToOverride != null) {
          for (String toRemove : suffixesToOverride) {
            if (trimmedUppercaseSql.endsWith(toRemove) || trimmedUppercaseSql.endsWith(toRemove.trim())) {
              int start = sql.length() - toRemove.trim().length();
              int end = sql.length();
              sql.delete(start, end);
              break;
            }
          }
        }
        //后缀不为空,则拼接后缀.
        if (suffix != null) {
          sql.append(" ");
          sql.append(suffix);
        }
      }
    }

  }

}
