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
package org.apache.ibatis.parsing;

/**
 * 占位符解析
 * @author Clinton Begin
 */
public class GenericTokenParser {
  /**
   * 开始标签
   */
  private final String openToken;
  /**
   * 闭合标签
   */
  private final String closeToken;
  /**
   * 参数处理
   */
  private final TokenHandler handler;

  public GenericTokenParser(String openToken, String closeToken, TokenHandler handler) {
    this.openToken = openToken;
    this.closeToken = closeToken;
    this.handler = handler;
  }

  /**
   * 处理占位符内容
   *
   * @param text 占位符内容
   * @return 替换占位符后文本
   */
  public String parse(String text) {
    if (text == null || text.isEmpty()) { //空值或空字符,返回字符串空
      return "";
    }
    // search open token  查找占位符开始位置
    int start = text.indexOf(openToken);
    if (start == -1) {  //无占位符内容,返回原文本
      return text;
    }
    char[] src = text.toCharArray();
    int offset = 0;
    final StringBuilder builder = new StringBuilder();
    StringBuilder expression = null;  //记录占位符内表达式内容
    while (start > -1) {
      if (start > 0 && src[start - 1] == '\\') {  //如果转义符开始的前有转义符,那就不需要处理里面占位符内容,只要剔除掉转义符. 示例: \\${skipped} variable
        // this open token is escaped. remove the backslash and continue.
        builder.append(src, offset, start - offset - 1).append(openToken);  //拼接转义符前内容+占位符开始位置(等于去掉了从开始位置到占位符开始位置之间的转义符)
        offset = start + openToken.length(); //计算下个占位符开始位置
      } else {
        // found open token. let's search close token. 找到占位符内容
        if (expression == null) {
          expression = new StringBuilder(); //初始化内容
        } else {
          expression.setLength(0);  //重置长度
        }
        builder.append(src, offset, start - offset);  //记录占位符起始之前内容
        offset = start + openToken.length();  //占位符开始位置
        int end = text.indexOf(closeToken, offset); //占位符结束位置
        while (end > -1) {
          if (end > offset && src[end - 1] == '\\') {  //处理占位符结束标签转义. 示例:${var{with\}brace}
            // this close token is escaped. remove the backslash and continue.
            expression.append(src, offset, end - offset - 1).append(closeToken); //记录开始到占位符之间内容
            offset = end + closeToken.length(); //切换偏移量至转义结束标签位置
            end = text.indexOf(closeToken, offset); //计算下一个结束标签位置
          } else {
            expression.append(src, offset, end - offset); //提取占位符内容 ${first_name} 提取出first_name
            break;
          }
        }
        if (end == -1) {
          // close token was not found. 处理有占位符开始,没占位符结束的情况 Hello ${ this is a test.
          builder.append(src, start, src.length - start); //无占位符结束标签,拼接占位符开始到结束后的内容
          offset = src.length;  //切换偏移量至字符串结束位置
        } else {
          builder.append(handler.handleToken(expression.toString())); //调用占位符处理器,处理占位符内容,就是将里面的表达式替换成你需要的内容,比如${firs_name}替换成?
          offset = end + closeToken.length(); //切换偏移量至占位符结束位置
        }
      }
      start = text.indexOf(openToken, offset); //计算下个占位符开始位置
    }
    if (offset < src.length) {  //偏移量还小于整个字符串长度的情况,这个时候后面就是纯文本内容了,只要拼接后面的内容就行了
      builder.append(src, offset, src.length - offset);
    }
    return builder.toString();
  }
}
