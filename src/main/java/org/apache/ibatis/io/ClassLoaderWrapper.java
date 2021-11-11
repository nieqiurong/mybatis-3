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
package org.apache.ibatis.io;

import java.io.InputStream;
import java.net.URL;

/**
 * 类加载器资源加载装饰
 * A class to wrap access to multiple class loaders making them work as one
 *
 * @author Clinton Begin
 */
public class ClassLoaderWrapper {

  /**
   * 默认类加载器
   */
  ClassLoader defaultClassLoader;
  /**
   * 系统类加载器
   */
  ClassLoader systemClassLoader;

  ClassLoaderWrapper() {
    try {
      //初始化系统类加载器
      systemClassLoader = ClassLoader.getSystemClassLoader();
    } catch (SecurityException ignored) {
      // AccessControlException on Google App Engine
    }
  }

  /**
   * 通过资源路径加载资源
   * Get a resource as a URL using the current class path
   *
   * @param resource - the resource to locate
   * @return the resource or null
   */
  public URL getResourceAsURL(String resource) {
    return getResourceAsURL(resource, getClassLoaders(null));
  }

  /**
   * 通过资源路径获取资源访问符
   * Get a resource from the classpath, starting with a specific class loader
   *
   * @param resource    - the resource to find
   * @param classLoader - the first classloader to try
   * @return the stream or null
   */
  public URL getResourceAsURL(String resource, ClassLoader classLoader) {
    return getResourceAsURL(resource, getClassLoaders(classLoader));
  }

  /**
   * 通过资源路径获取输入流
   * Get a resource from the classpath
   *
   * @param resource - the resource to find
   * @return the stream or null
   */
  public InputStream getResourceAsStream(String resource) {
    return getResourceAsStream(resource, getClassLoaders(null));
  }

  /**
   * 通过资源路径获取输入流
   * Get a resource from the classpath, starting with a specific class loader
   *
   * @param resource    - the resource to find
   * @param classLoader - the first class loader to try
   * @return the stream or null
   */
  public InputStream getResourceAsStream(String resource, ClassLoader classLoader) {
    return getResourceAsStream(resource, getClassLoaders(classLoader));
  }

  /**
   * 加载类信息
   * Find a class on the classpath (or die trying)
   *
   * @param name - the class to look for
   * @return - the class
   * @throws ClassNotFoundException Duh.
   */
  public Class<?> classForName(String name) throws ClassNotFoundException {
    return classForName(name, getClassLoaders(null));
  }

  /**
   * 加载类信息
   * Find a class on the classpath, starting with a specific classloader (or die trying)
   *
   * @param name        - the class to look for
   * @param classLoader - the first classloader to try
   * @return - the class
   * @throws ClassNotFoundException Duh.
   */
  public Class<?> classForName(String name, ClassLoader classLoader) throws ClassNotFoundException {
    return classForName(name, getClassLoaders(classLoader));
  }

  /**
   * 通过指定类加载器集合获取资源输入流
   * Try to get a resource from a group of classloaders
   *
   * @param resource    - the resource to get
   * @param classLoader - the classloaders to examine
   * @return the resource or null
   */
  InputStream getResourceAsStream(String resource, ClassLoader[] classLoader) {
    for (ClassLoader cl : classLoader) {  //递归遍历类加载器集合
      if (null != cl) { //类加载器数组里可能存在一些空的类加载器,需要判断一下

        // try to find the resource as passed
        InputStream returnValue = cl.getResourceAsStream(resource); //优先加载类路径下到资源

        // now, some class loaders want this leading "/", so we'll add it and try again if we didn't find the resource
        if (null == returnValue) {  //如果类路径下无资源,尝试从根路径开始加载
          returnValue = cl.getResourceAsStream("/" + resource);
        }

        if (null != returnValue) {
          return returnValue;
        }
      }
    }
    return null;
  }

  /**
   * Get a resource as a URL using the current class path
   *
   * @param resource    - the resource to locate
   * @param classLoader - the class loaders to examine
   * @return the resource or null
   */
  URL getResourceAsURL(String resource, ClassLoader[] classLoader) {

    URL url;

    for (ClassLoader cl : classLoader) {  //递归遍历类加载器集合

      if (null != cl) { //类加载器数组里可能存在一些空的类加载器,需要判断一下

        // look for the resource as passed in...
        url = cl.getResource(resource); //优先加载类路径下到资源

        // ...but some class loaders want this leading "/", so we'll add it
        // and try again if we didn't find the resource
        if (null == url) {
          url = cl.getResource("/" + resource); //如果类路径下无资源,尝试从根路径开始加载
        }

        // "It's always in the last place I look for it!"
        // ... because only an idiot would keep looking for it after finding it, so stop looking already.
        if (null != url) {
          return url;
        }

      }

    }

    // didn't find it anywhere.
    return null;

  }

  /**
   * 加载类信息
   * Attempt to load a class from a group of classloaders
   *
   * @param name        - the class to load
   * @param classLoader - the group of classloaders to examine
   * @return the class
   * @throws ClassNotFoundException - Remember the wisdom of Judge Smails: Well, the world needs ditch diggers, too.
   */
  Class<?> classForName(String name, ClassLoader[] classLoader) throws ClassNotFoundException {

    for (ClassLoader cl : classLoader) {  //递归遍历类加载器集合

      if (null != cl) { //类加载器数组里可能存在一些空的类加载器,需要判断一下

        try {

          Class<?> c = Class.forName(name, true, cl);

          if (null != c) {  //按道理来说,上面的加载之后是不会返回空了,未加载成功就会抛异常了
            return c;
          }

        } catch (ClassNotFoundException e) {  //捕获异常并忽略异常信息,交给下一个类加载器继续加载
          // we'll ignore this until all classloaders fail to locate the class
        }

      }

    }
    //所有类加载器加载不成功返回ClassNotFoundException,保持方法原义
    throw new ClassNotFoundException("Cannot find class: " + name);

  }

  /**
   * 获取类加载器数组
   * @param classLoader 指定类加载器
   * @return 类加载器数组
   */
  ClassLoader[] getClassLoaders(ClassLoader classLoader) {
    return new ClassLoader[]{
        classLoader,  //用户指定类加载器
        defaultClassLoader, //默认类加载器
        Thread.currentThread().getContextClassLoader(), //当前线程类加载器
        getClass().getClassLoader(),  //当前类加载器
        systemClassLoader //系统类加载器
    };
  }

}
