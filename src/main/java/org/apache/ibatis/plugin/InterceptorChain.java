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
package org.apache.ibatis.plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 拦截器调用链
 * @author Clinton Begin
 */
public class InterceptorChain {
  
  /**
   * 所有插件集合
   */
  private final List<Interceptor> interceptors = new ArrayList<>();

  /**
   * 应用插件
   *
   * @param target 目标对象
   * @return 目标对象
   */
  public Object pluginAll(Object target) {
    //遍历所有插件查找符合当前接口的插件，生成动态代理对象。
    for (Interceptor interceptor : interceptors) {
      //如果存在多个同类型的插件，则会形成一个套娃，插件执行应用顺序就会反过来，也就是先注册的插件后执行。
      target = interceptor.plugin(target);
    }
    return target;
  }

  /**
   * 注册拦截器
   *
   * @param interceptor 拦截器
   */
  public void addInterceptor(Interceptor interceptor) {
    interceptors.add(interceptor);
  }

  /**
   * 返回拦截器集合
   *
   * @return 拦截器集合（不可修改）
   */
  public List<Interceptor> getInterceptors() {
    return Collections.unmodifiableList(interceptors);
  }

}
