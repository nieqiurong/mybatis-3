/**
 *    Copyright 2009-2017 the original author or authors.
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
package org.apache.ibatis.reflection.property;

import java.util.Iterator;

/**
 * 属性分词器
 *
 * @author Clinton Begin
 */
public class PropertyTokenizer implements Iterator<PropertyTokenizer> {
  /**
   * 属性名(当为集合时,属性要去除访问索引)
   */
  private String name;
  /**
   * 索引名称(完整的分词前属性)
   */
  private final String indexedName;
  /**
   * 索引下标 xx[0].xx or map[xx].xx
   */
  private String index;
  /**
   * 子属性
   */
  private final String children;

  /**
   * 属性分词处理
   * 主要分两种情况:
   * 一:bean属性访问  user.name
   * 二:集合属性访问   list[0].name  map[key]
   *
   * @param fullname 全名称
   */
  public PropertyTokenizer(String fullname) {
    int delim = fullname.indexOf('.');
    if (delim > -1) {
      name = fullname.substring(0, delim);
      children = fullname.substring(delim + 1);
    } else {
      name = fullname;
      children = null;
    }
    indexedName = name;
    //处理列表属性访问: list[0].name
    delim = name.indexOf('[');
    if (delim > -1) {
      //获取索引下标(可能是list的下标,也可能是map的key)
      index = name.substring(delim + 1, name.length() - 1);
      //属性即为分隔符前的字符串: list
      name = name.substring(0, delim);
    }
  }

  /**
   * 获取属性名称
   *
   * @return 获取属性名
   */
  public String getName() {
    return name;
  }

  /**
   * 获取索引项
   *
   * @return 索引项
   */
  public String getIndex() {
    return index;
  }

  /**
   * 获取索引名
   *
   * @return 索引名
   */
  public String getIndexedName() {
    return indexedName;
  }

  /**
   * 获取子节点
   *
   * @return 子节点
   */
  public String getChildren() {
    return children;
  }

  /**
   * 判断是否含有子节点
   *
   * @return 是否含有子节点
   */
  @Override
  public boolean hasNext() {
    return children != null;
  }

  /**
   * 存在子节点继续分词
   *
   * @return 分词器
   */
  @Override
  public PropertyTokenizer next() {
    return new PropertyTokenizer(children);
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException("Remove is not supported, as it has no meaning in the context of properties.");
  }
}
