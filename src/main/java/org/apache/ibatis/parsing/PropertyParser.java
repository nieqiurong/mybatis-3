/**
 *    Copyright 2009-2016 the original author or authors.
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
package org.apache.ibatis.parsing;

import java.util.Properties;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class PropertyParser {

  private static final String KEY_PREFIX = "org.apache.ibatis.parsing.PropertyParser.";
  /**
   * The special property key that indicate whether enable a default value on placeholder.
   * <p>
   *   The default value is {@code false} (indicate disable a default value on placeholder)
   *   If you specify the {@code true}, you can specify key and default value on placeholder (e.g. {@code ${db.username:postgres}}).
   * </p>
   * @since 3.4.2
   */
  public static final String KEY_ENABLE_DEFAULT_VALUE = KEY_PREFIX + "enable-default-value";

  /**
   * The special property key that specify a separator for key and default value on placeholder.
   * <p>
   *   The default separator is {@code ":"}.
   * </p>
   * @since 3.4.2
   */
  public static final String KEY_DEFAULT_VALUE_SEPARATOR = KEY_PREFIX + "default-value-separator";

  private static final String ENABLE_DEFAULT_VALUE = "false";
  private static final String DEFAULT_VALUE_SEPARATOR = ":";

  private PropertyParser() {
    // Prevent Instantiation
  }

  /**
   * 属性解析
   *
   * @param string    属性表达式式
   * @param variables 属性集合
   * @return 属性值
   */
  public static String parse(String string, Properties variables) {
    VariableTokenHandler handler = new VariableTokenHandler(variables); //构建属性处理器
    GenericTokenParser parser = new GenericTokenParser("${", "}", handler); //创建占位符处理
    return parser.parse(string);  //解析属性变量
  }

  /**
   * 属性变量处理器
   */
  private static class VariableTokenHandler implements TokenHandler {
    /**
     * 属性集合
     */
    private final Properties variables;
    /**
     * 是否启用默认属性值解析
     */
    private final boolean enableDefaultValue;
    /**
     * 默认属性值分隔符
     */
    private final String defaultValueSeparator;

    private VariableTokenHandler(Properties variables) {
      //设置属性值
      this.variables = variables;
      //判断默认属性值解析是否激活,如果没有的话,使用默认值false
      this.enableDefaultValue = Boolean.parseBoolean(getPropertyValue(KEY_ENABLE_DEFAULT_VALUE, ENABLE_DEFAULT_VALUE));
      //判断默认属性值分隔符是否设置,如果没有的话,使用默认值:
      this.defaultValueSeparator = getPropertyValue(KEY_DEFAULT_VALUE_SEPARATOR, DEFAULT_VALUE_SEPARATOR);
    }

    /**
     * 获取属性值
     *
     * @param key          属性key
     * @param defaultValue 默认值
     * @return 属性值
     */
    private String getPropertyValue(String key, String defaultValue) {
      return (variables == null) ? defaultValue : variables.getProperty(key, defaultValue);
    }

    @Override
    public String handleToken(String content) {
      if (variables != null) {
        String key = content;
        if (enableDefaultValue) {  //启用默认值的时候
          final int separatorIndex = content.indexOf(defaultValueSeparator);  //查找默认分隔符开始位置
          String defaultValue = null;
          if (separatorIndex >= 0) {
            key = content.substring(0, separatorIndex); //截取分隔符前内容即为key值
            defaultValue = content.substring(separatorIndex + defaultValueSeparator.length());  //分隔符后即为默认值
          }
          if (defaultValue != null) {
            //存在默认值的情况,如果获取不到属性值的情况,即返回默认值
            return variables.getProperty(key, defaultValue);
          }
        }
        if (variables.containsKey(key)) {
          //查找到变量值,返回
          return variables.getProperty(key);
        }
      }
      //找不到的情况,原封不动返回
      return "${" + content + "}";
    }
  }

}
