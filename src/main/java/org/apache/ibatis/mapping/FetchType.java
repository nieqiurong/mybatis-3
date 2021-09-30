/**
 *    Copyright 2009-2015 the original author or authors.
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
package org.apache.ibatis.mapping;

import org.apache.ibatis.session.Configuration;

/**
 * 延迟加载数据抓取策略
 *
 * @author Eduardo Macarron
 */
public enum FetchType {
  /**
   * 惰性(也就是懒加载,当嵌套查询时,会创建代理对象,等调用代理属性时才发出sql查询)
   */
  LAZY,
  /**
   * 饥饿(立刻加载)
   */
  EAGER,
  /**
   * 默认值(使用全局配置{@link Configuration#isLazyLoadingEnabled()})
   */
  DEFAULT
}
