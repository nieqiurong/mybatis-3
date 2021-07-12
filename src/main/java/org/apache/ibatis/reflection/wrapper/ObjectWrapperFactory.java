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
package org.apache.ibatis.reflection.wrapper;

import org.apache.ibatis.reflection.MetaObject;

/**
 * 对象包装工厂,基本上没啥用
 *
 * @author Clinton Begin
 */
public interface ObjectWrapperFactory {

  /**
   * 判断对象是否有包装处理
   *
   * @param object 对象参数
   * @return 是否存在包装处理
   */
  boolean hasWrapperFor(Object object);

  /**
   * 获取对象包装处理
   *
   * @param metaObject 元对象信息
   * @param object     对象参数
   * @return 对象包装处理
   */
  ObjectWrapper getWrapperFor(MetaObject metaObject, Object object);

}
