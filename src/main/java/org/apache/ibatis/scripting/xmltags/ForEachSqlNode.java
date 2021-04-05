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
package org.apache.ibatis.scripting.xmltags;

import java.util.Map;

import org.apache.ibatis.parsing.GenericTokenParser;
import org.apache.ibatis.session.Configuration;

/**
 *   <foreach item="item" index="index" collection="list"
 *       open="(" separator="," close=")">
 *         #{item}
 *   </foreach>
 * @author Clinton Begin
 */
public class ForEachSqlNode implements SqlNode {
  public static final String ITEM_PREFIX = "__frch_";

  /**
   * 表达式计算
   */
  private final ExpressionEvaluator evaluator;
  /**
   * 集合获取表达式
   */
  private final String collectionExpression;
  private final SqlNode contents;
  /**
   * 开始标签
   */
  private final String open;
  /**
   * 闭合标签
   */
  private final String close;
  /**
   * 分隔符
   */
  private final String separator;
  /**
   * 迭代变量
   */
  private final String item;
  /**
   * 迭代索引
   */
  private final String index;
  /**
   * mybatis配置
   */
  private final Configuration configuration;

  public ForEachSqlNode(Configuration configuration, SqlNode contents, String collectionExpression, String index, String item, String open, String close, String separator) {
    this.evaluator = new ExpressionEvaluator();
    this.collectionExpression = collectionExpression;
    this.contents = contents;
    this.open = open;
    this.close = close;
    this.separator = separator;
    this.index = index;
    this.item = item;
    this.configuration = configuration;
  }

  @Override
  public boolean apply(DynamicContext context) {
  /*
    <select id="selectPostIn" resultType="domain.blog.Post">
          SELECT *
          FROM POST P
          WHERE ID in
          <foreach item="item" index="index" collection="list"
              open="(" separator="," close=")">
                #{item}
          </foreach>
    </select>
  */
    Map<String, Object> bindings = context.getBindings();
    //通过表达式获取集合
    final Iterable<?> iterable = evaluator.evaluateIterable(collectionExpression, bindings);
    if (!iterable.iterator().hasNext()) {
      //空集合,提前中断
      return true;
    }

    boolean first = true;
    applyOpen(context);
    int i = 0;
    for (Object o : iterable) {
      DynamicContext oldContext = context;
      if (first || separator == null) {
        // 第一个元素前无需拼接分隔符
        context = new PrefixedContext(context, "");
      } else {
        // 添加分隔符至元素后
        context = new PrefixedContext(context, separator);
      }
      //创建前缀上下文之后,获取迭代唯一编号
      int uniqueNumber = context.getUniqueNumber();
      // Issue #709
      if (o instanceof Map.Entry) {
        @SuppressWarnings("unchecked")
        Map.Entry<Object, Object> mapEntry = (Map.Entry<Object, Object>) o;
        applyIndex(context, mapEntry.getKey(), uniqueNumber);
        applyItem(context, mapEntry.getValue(), uniqueNumber);
      } else {
        applyIndex(context, i, uniqueNumber);
        applyItem(context, o, uniqueNumber);
      }
      contents.apply(new FilteredDynamicContext(configuration, context, index, item, uniqueNumber));
      if (first) {
        // 这里为了解决foreach里面在嵌套条件判断,只有找到了第一个拼接过后的的子节点,这里的first才能成立
        first = !((PrefixedContext) context).isPrefixApplied();
      }
      context = oldContext;
      i++;
    }
    applyClose(context);
    // 清理最后绑定的变量,这里每次的遍历会覆盖
    context.getBindings().remove(item);
    context.getBindings().remove(index);
    return true;
  }

  /**
   * 绑定索引上下文
   * @param context 动态上下文
   * @param o 当前索引
   * @param i 上下文唯一索引编号
   */
  private void applyIndex(DynamicContext context, Object o, int i) {
    if (index != null) {
      // 索引变量 -> 元素索引
      context.bind(index, o);
      // __frch_index_0 -> 元素索引
      context.bind(itemizeItem(index, i), o);
    }
  }

  private void applyItem(DynamicContext context, Object o, int i) {
    if (item != null) {
      // 集合变量 -> 元素
      context.bind(item, o);
      // __item_index_0 -> 元素索引
      context.bind(itemizeItem(item, i), o);
    }
  }

  /**
   * 处理开始标签
   *
   * @param context 动态上下文
   */
  private void applyOpen(DynamicContext context) {
    if (open != null) {
      context.appendSql(open);
    }
  }

  /**
   * 处理闭合标签
   *
   * @param context 动态上下文
   */
  private void applyClose(DynamicContext context) {
    if (close != null) {
      context.appendSql(close);
    }
  }

  private static String itemizeItem(String item, int i) {
    return ITEM_PREFIX + item + "_" + i;
  }

  private static class FilteredDynamicContext extends DynamicContext {
    private final DynamicContext delegate;
    private final int index;
    private final String itemIndex;
    private final String item;

    public FilteredDynamicContext(Configuration configuration,DynamicContext delegate, String itemIndex, String item, int i) {
      super(configuration, null);
      this.delegate = delegate;
      this.index = i;
      this.itemIndex = itemIndex;
      this.item = item;
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
    public String getSql() {
      return delegate.getSql();
    }

    @Override
    public void appendSql(String sql) {
      GenericTokenParser parser = new GenericTokenParser("#{", "}", content -> {
        //item - > __frch_item_0
        //list与map,list情况下,索引项目应该没啥人用,但map情况下,可能需要迭代索引(也就是key),所以这里是先按迭代元素先处理,如果替换内容没变,就再按索引替换
        String newContent = content.replaceFirst("^\\s*" + item + "(?![^.,:\\s])", itemizeItem(item, index));
        if (itemIndex != null && newContent.equals(content)) {
          newContent = content.replaceFirst("^\\s*" + itemIndex + "(?![^.,:\\s])", itemizeItem(itemIndex, index));
        }
        return "#{" + newContent + "}";
      });
      /*
        <foreach item="item" index="index" collection="list" open="(" separator="," close=")">
          #{item}
        </foreach>
      */
      // 例如上面的sql就固定为#{item},迭代的时候需要将固定的值替换索引项目,就会变成#{__frch_item_0},#{__frch_item_1}....
      delegate.appendSql(parser.parse(sql));
    }

    @Override
    public int getUniqueNumber() {
      return delegate.getUniqueNumber();
    }

  }


  /**
   * 前缀上下文
   */
  private class PrefixedContext extends DynamicContext {
    /**
     * 代理对象
     */
    private final DynamicContext delegate;
    /**
     * 前缀
     */
    private final String prefix;
    /**
     * 拼接前缀标志位
     */
    private boolean prefixApplied;

    public PrefixedContext(DynamicContext delegate, String prefix) {
      super(configuration, null);
      this.delegate = delegate;
      this.prefix = prefix;
      this.prefixApplied = false;
    }

    public boolean isPrefixApplied() {
      return prefixApplied;
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
    public void appendSql(String sql) {
      if (!prefixApplied && sql != null && sql.trim().length() > 0) {
        delegate.appendSql(prefix);
        prefixApplied = true;
      }
      delegate.appendSql(sql);
    }

    @Override
    public String getSql() {
      return delegate.getSql();
    }

    @Override
    public int getUniqueNumber() {
      return delegate.getUniqueNumber();
    }
  }

}
