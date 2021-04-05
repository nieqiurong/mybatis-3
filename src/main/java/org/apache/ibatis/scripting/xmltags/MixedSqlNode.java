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

import java.util.List;

/**
 * 这个等同于一级节点内容
 *
 * @author Clinton Begin
 * @see XMLScriptBuilder#parseDynamicTags(org.apache.ibatis.parsing.XNode)
 * @see DynamicSqlSource#DynamicSqlSource(org.apache.ibatis.session.Configuration, org.apache.ibatis.scripting.xmltags.SqlNode)
 */
public class MixedSqlNode implements SqlNode {
  private final List<SqlNode> contents;

  /**
   * 构造解析节点
   * @param contents 子节点内容
   */
  public MixedSqlNode(List<SqlNode> contents) {
    this.contents = contents;
  }

  @Override
  public boolean apply(DynamicContext context) {
    contents.forEach(node -> node.apply(context));
    return true;
  }
}
