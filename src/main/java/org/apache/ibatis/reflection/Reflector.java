/**
 *    Copyright 2009-2020 the original author or authors.
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
package org.apache.ibatis.reflection;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.ReflectPermission;
import java.lang.reflect.Type;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.ibatis.reflection.invoker.AmbiguousMethodInvoker;
import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.invoker.SetFieldInvoker;
import org.apache.ibatis.reflection.property.PropertyNamer;

/**
 * This class represents a cached set of class definition information that
 * allows for easy mapping between property names and getter/setter methods.
 *
 * @author Clinton Begin
 */
public class Reflector {

  /**
   * 类信息
   */
  private final Class<?> type;
  /**
   * 可读属性名
   */
  private final String[] readablePropertyNames;
  /**
   * 可写属性名
   */
  private final String[] writablePropertyNames;
  /**
   * set方法
   */
  private final Map<String, Invoker> setMethods = new HashMap<>();
  /**
   * get方法
   */
  private final Map<String, Invoker> getMethods = new HashMap<>();
  /**
   * set类型
   */
  private final Map<String, Class<?>> setTypes = new HashMap<>();
  /**
   * get类型
   */
  private final Map<String, Class<?>> getTypes = new HashMap<>();
  /**
   * 默认构造方法
   */
  private Constructor<?> defaultConstructor;
  /**
   * 属性映射(大小写不敏感)
   */
  private Map<String, String> caseInsensitivePropertyMap = new HashMap<>();

  public Reflector(Class<?> clazz) {
    type = clazz;
    // 添加默认构造方法
    addDefaultConstructor(clazz);
    // 添加get方法
    addGetMethods(clazz);
    // 添加set方法
    addSetMethods(clazz);
    // 添加属性字段
    addFields(clazz);
    // 记录可读属性(含未提供get方法的)
    readablePropertyNames = getMethods.keySet().toArray(new String[0]);
    // 记录可写属性(含未提供set方法的)
    writablePropertyNames = setMethods.keySet().toArray(new String[0]);
    // 保存大小写不敏感属性记录
    for (String propName : readablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
    for (String propName : writablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
  }

  /**
   * 查找默认构造方法
   *
   * @param clazz 类信息
   */
  private void addDefaultConstructor(Class<?> clazz) {
    Constructor<?>[] constructors = clazz.getDeclaredConstructors();
    Arrays.stream(constructors).filter(constructor -> constructor.getParameterTypes().length == 0)
      .findAny().ifPresent(constructor -> this.defaultConstructor = constructor);
  }

  /**
   * 获取get方法
   *
   * @param clazz 类型信息
   */
  private void addGetMethods(Class<?> clazz) {
    Map<String, List<Method>> conflictingGetters = new HashMap<>();
    Method[] methods = getClassMethods(clazz);
    Arrays.stream(methods).filter(m -> m.getParameterTypes().length == 0 && PropertyNamer.isGetter(m.getName()))
      .forEach(m -> addMethodConflict(conflictingGetters, PropertyNamer.methodToProperty(m.getName()), m));
    resolveGetterConflicts(conflictingGetters);
  }

  /**
   * 处理方法冲突,可能是一些重载方法或者是一些命名不规范的
   *
   * @param conflictingGetters 冲突方法列表
   */
  private void resolveGetterConflicts(Map<String, List<Method>> conflictingGetters) {
    for (Entry<String, List<Method>> entry : conflictingGetters.entrySet()) {
      Method winner = null;
      String propName = entry.getKey();
      boolean isAmbiguous = false;
      // 循环冲突方法列表,查找最优方法
      for (Method candidate : entry.getValue()) {
        // 首位标志,没有重载的时候也只有一个
        if (winner == null) {
          winner = candidate;
          continue;
        }
        Class<?> winnerType = winner.getReturnType();
        Class<?> candidateType = candidate.getReturnType();
        // 如果两个类型相等
        if (candidateType.equals(winnerType)) {
          //非基本bool情况下,就不知道调用谁的了
          if (!boolean.class.equals(candidateType)) {
            isAmbiguous = true;
            break;
          } else if (candidate.getName().startsWith("is")) {
            // bool基本类型以is为最优方法
            winner = candidate;
          }
        } else if (candidateType.isAssignableFrom(winnerType)) {
          // 如果标志位返回值是当前的返回值类型的子类话,就不用做什么处理了
          // OK getter type is descendant
        } else if (winnerType.isAssignableFrom(candidateType)) {
          // 如果当前的返回值类型是标志返回值的子类话,那些切换当前方法为标志位
          winner = candidate;
        } else {
          // 可能bool和Bool这样的重载方法  Boolean getTest() boolean isTest()
          isAmbiguous = true;
          break;
        }
      }
      addGetMethod(propName, winner, isAmbiguous);
    }
  }

  /**
   * 添加get方法记录
   *
   * @param name        方法名称
   * @param method      方法信息
   * @param isAmbiguous 是否模糊,true的情况会调用报错
   */
  private void addGetMethod(String name, Method method, boolean isAmbiguous) {
    MethodInvoker invoker = isAmbiguous
        ? new AmbiguousMethodInvoker(method, MessageFormat.format(
            "Illegal overloaded getter method with ambiguous type for property ''{0}'' in class ''{1}''. This breaks the JavaBeans specification and can cause unpredictable results.",
            name, method.getDeclaringClass().getName()))
        : new MethodInvoker(method);
    //记录get方法
    getMethods.put(name, invoker);
    Type returnType = TypeParameterResolver.resolveReturnType(method, type);
    //记录方法返回值
    getTypes.put(name, typeToClass(returnType));
  }

  /**
   * 添加set方法记录
   *
   * @param clazz 类信息
   */
  private void addSetMethods(Class<?> clazz) {
    Map<String, List<Method>> conflictingSetters = new HashMap<>();
    Method[] methods = getClassMethods(clazz);
    Arrays.stream(methods).filter(m -> m.getParameterTypes().length == 1 && PropertyNamer.isSetter(m.getName()))
      .forEach(m -> addMethodConflict(conflictingSetters, PropertyNamer.methodToProperty(m.getName()), m));
    resolveSetterConflicts(conflictingSetters);
  }

  /**
   * @param conflictingMethods 冲突方法列表
   * @param name               方法名称
   * @param method             方法
   */
  private void addMethodConflict(Map<String, List<Method>> conflictingMethods, String name, Method method) {
    if (isValidPropertyName(name)) {
      // 可能会有重载方法存在,需要用列表记录
      List<Method> list = conflictingMethods.computeIfAbsent(name, k -> new ArrayList<>());
      list.add(method);
    }
  }

  /**
   * 处理set方法冲突
   *
   * @param conflictingSetters 方法冲突列表
   */
  private void resolveSetterConflicts(Map<String, List<Method>> conflictingSetters) {
    for (Entry<String, List<Method>> entry : conflictingSetters.entrySet()) {
      String propName = entry.getKey();
      List<Method> setters = entry.getValue();
      Class<?> getterType = getTypes.get(propName);
      boolean isGetterAmbiguous = getMethods.get(propName) instanceof AmbiguousMethodInvoker;
      boolean isSetterAmbiguous = false;
      Method match = null;
      for (Method setter : setters) {
        //非歧义方法的时候,判断get方法返回值与set方法参数值是否匹配
        if (!isGetterAmbiguous && setter.getParameterTypes()[0].equals(getterType)) {
          // should be the best match
          match = setter;
          break;
        }
        if (!isSetterAmbiguous) {
          match = pickBetterSetter(match, setter, propName);
          isSetterAmbiguous = match == null;
        }
      }
      // 遍历结束,如果有匹配方法的时候,保存set方法记录
      if (match != null) {
        addSetMethod(propName, match);
      }
    }
  }

  /**
   * set方法选择
   *
   * @param setter1  标志位方法(可能为空)
   * @param setter2  遍历选择方法(不为空)
   * @param property 属性名称
   * @return set方法
   */
  private Method pickBetterSetter(Method setter1, Method setter2, String property) {
    if (setter1 == null) {
      // 待匹配方法为空,当遍历的方法置为标志位
      return setter2;
    }
    Class<?> paramType1 = setter1.getParameterTypes()[0];
    Class<?> paramType2 = setter2.getParameterTypes()[0];
    if (paramType1.isAssignableFrom(paramType2)) {
      // 如果遍历的方法参数是标志位方法参数的子类,则切换标志位方法为遍历方法
      return setter2;
    } else if (paramType2.isAssignableFrom(paramType1)) {
      // 如果标志位方法为遍历方法的参数的子类,则标志位方法还是自身
      return setter1;
    }
    // 处理set模糊方法
    MethodInvoker invoker = new AmbiguousMethodInvoker(setter1,
        MessageFormat.format(
            "Ambiguous setters defined for property ''{0}'' in class ''{1}'' with types ''{2}'' and ''{3}''.",
            property, setter2.getDeclaringClass().getName(), paramType1.getName(), paramType2.getName()));
    // 添加set方法记录
    setMethods.put(property, invoker);
    Type[] paramTypes = TypeParameterResolver.resolveParamTypes(setter1, type);
    // 添加set参数类型记录
    setTypes.put(property, typeToClass(paramTypes[0]));
    return null;
  }

  /**
   * 添加set方法记录
   *
   * @param name   属性名称
   * @param method set方法
   */
  private void addSetMethod(String name, Method method) {
    MethodInvoker invoker = new MethodInvoker(method);
    setMethods.put(name, invoker);
    Type[] paramTypes = TypeParameterResolver.resolveParamTypes(method, type);
    setTypes.put(name, typeToClass(paramTypes[0]));
  }

  private Class<?> typeToClass(Type src) {
    Class<?> result = null;
    if (src instanceof Class) {
      result = (Class<?>) src;
    } else if (src instanceof ParameterizedType) {
      result = (Class<?>) ((ParameterizedType) src).getRawType();
    } else if (src instanceof GenericArrayType) {
      Type componentType = ((GenericArrayType) src).getGenericComponentType();
      if (componentType instanceof Class) {
        result = Array.newInstance((Class<?>) componentType, 0).getClass();
      } else {
        Class<?> componentClass = typeToClass(componentType);
        result = Array.newInstance(componentClass, 0).getClass();
      }
    }
    if (result == null) {
      result = Object.class;
    }
    return result;
  }

  /**
   * 添加属性字段
   *
   * @param clazz 类信息
   */
  private void addFields(Class<?> clazz) {
    Field[] fields = clazz.getDeclaredFields();
    for (Field field : fields) {
      // 如果没set方法,添加基于直接操作属性的set方法
      if (!setMethods.containsKey(field.getName())) {
        // issue #379 - removed the check for final because JDK 1.5 allows
        // modification of final fields through reflection (JSR-133). (JGB)
        // pr #16 - final static can only be set by the classloader
        int modifiers = field.getModifiers();
        //排除掉final属性和static属性
        if (!(Modifier.isFinal(modifiers) && Modifier.isStatic(modifiers))) {
          addSetField(field);
        }
      }
      // 如果没有get方法,添加基于直接操作属性的get方法
      if (!getMethods.containsKey(field.getName())) {
        addGetField(field);
      }
    }
    // 递归父类字段
    if (clazz.getSuperclass() != null) {
      addFields(clazz.getSuperclass());
    }
  }

  /**
   * 添加set字段
   *
   * @param field 字段
   */
  private void addSetField(Field field) {
    if (isValidPropertyName(field.getName())) {
      //添加一个直接操作属性的反射方法
      setMethods.put(field.getName(), new SetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      //记录set参数类型
      setTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  /**
   * 添加get字段
   *
   * @param field 字段
   */
  private void addGetField(Field field) {
    if (isValidPropertyName(field.getName())) {
      //添加一个直接操作属性的反射方法
      getMethods.put(field.getName(), new GetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      //记录get返回值类型
      getTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  /**
   * 判断是否为合法的属性名称
   *
   * @param name 属性名
   * @return 是否合法
   */
  private boolean isValidPropertyName(String name) {
    return !(name.startsWith("$") || "serialVersionUID".equals(name) || "class".equals(name));
  }

  /**
   * 获取类所有方法(含私有)
   * This method returns an array containing all methods
   * declared in this class and any superclass.
   * We use this method, instead of the simpler <code>Class.getMethods()</code>,
   * because we want to look for private methods as well.
   *
   * @param clazz The class
   * @return An array containing all methods in this class
   */
  private Method[] getClassMethods(Class<?> clazz) {
    //这里采取了递归所有类继承方法(含私有)列表,而没有直接通过getMethods获取所有公开方法.
    Map<String, Method> uniqueMethods = new HashMap<>();
    Class<?> currentClass = clazz;
    //递归获取类所有的方法,不包含Object的方法.
    while (currentClass != null && currentClass != Object.class) {
      // 获取类本身方法
      addUniqueMethods(uniqueMethods, currentClass.getDeclaredMethods());

      // we also need to look for interface methods -
      // because the class may be abstract
      // 获取当前类接口方法
      Class<?>[] interfaces = currentClass.getInterfaces();
      for (Class<?> anInterface : interfaces) {
        addUniqueMethods(uniqueMethods, anInterface.getMethods());
      }
      // 切换父类递归获取
      currentClass = currentClass.getSuperclass();
    }

    Collection<Method> methods = uniqueMethods.values();

    return methods.toArray(new Method[0]);
  }

  /**
   * 记录方法名关系
   *
   * @param uniqueMethods 方法映射记录
   * @param methods       方法列表
   */
  private void addUniqueMethods(Map<String, Method> uniqueMethods, Method[] methods) {
    for (Method currentMethod : methods) {
      // 如果是桥接(主要是泛型)方法,就跳过.
      if (!currentMethod.isBridge()) {
        String signature = getSignature(currentMethod);
        // check to see if the method is already known
        // if it is known, then an extended class must have
        // overridden a method
        //记录方法列表
        if (!uniqueMethods.containsKey(signature)) {
          uniqueMethods.put(signature, currentMethod);
        }
      }
    }
  }

  /**
   * 获取方法签名 (返回值#方法名:方法参数列表)
   *
   * @param method 方法
   * @return 方法签名
   */
  private String getSignature(Method method) {
    StringBuilder sb = new StringBuilder();
    Class<?> returnType = method.getReturnType();
    if (returnType != null) {
      sb.append(returnType.getName()).append('#');
    }
    sb.append(method.getName());
    Class<?>[] parameters = method.getParameterTypes();
    for (int i = 0; i < parameters.length; i++) {
      sb.append(i == 0 ? ':' : ',').append(parameters[i].getName());
    }
    return sb.toString();
  }

  /**
   * Checks whether can control member accessible.
   *
   * @return If can control member accessible, it return {@literal true}
   * @since 3.5.0
   */
  public static boolean canControlMemberAccessible() {
    try {
      SecurityManager securityManager = System.getSecurityManager();
      if (null != securityManager) {
        securityManager.checkPermission(new ReflectPermission("suppressAccessChecks"));
      }
    } catch (SecurityException e) {
      return false;
    }
    return true;
  }

  /**
   * 获取反射类类信息
   * Gets the name of the class the instance provides information for.
   *
   * @return The class name
   */
  public Class<?> getType() {
    return type;
  }

  /**
   * 获取默认构造方法
   *
   * @return 默认构造方法
   */
  public Constructor<?> getDefaultConstructor() {
    if (defaultConstructor != null) {
      return defaultConstructor;
    } else {
      throw new ReflectionException("There is no default constructor for " + type);
    }
  }

  /**
   * 判断是否有默认构造方法
   *
   * @return 是否有默认构造
   */
  public boolean hasDefaultConstructor() {
    return defaultConstructor != null;
  }

  /**
   * 获取属性的赋值操作方法
   *
   * @param propertyName 属性名称
   * @return 反射赋值操作方法
   */
  public Invoker getSetInvoker(String propertyName) {
    Invoker method = setMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  /**
   * 获取属性的取值操作方法
   *
   * @param propertyName 属性名称
   * @return 反射取值操作方法
   */
  public Invoker getGetInvoker(String propertyName) {
    Invoker method = getMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  /**
   * 获取属性赋值参数值类型
   * Gets the type for a property setter.
   *
   * @param propertyName - the name of the property
   * @return The Class of the property setter
   */
  public Class<?> getSetterType(String propertyName) {
    Class<?> clazz = setTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /**
   * 获取属性取值返回类型
   * Gets the type for a property getter.
   *
   * @param propertyName - the name of the property
   * @return The Class of the property getter
   */
  public Class<?> getGetterType(String propertyName) {
    Class<?> clazz = getTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /**
   * 获取可读属性列表
   * Gets an array of the readable properties for an object.
   *
   * @return The array
   */
  public String[] getGetablePropertyNames() {
    return readablePropertyNames;
  }

  /**
   * 获取可写属性列表
   * Gets an array of the writable properties for an object.
   *
   * @return The array
   */
  public String[] getSetablePropertyNames() {
    return writablePropertyNames;
  }

  /**
   * 判断是否有操作属性方法
   * Check to see if a class has a writable property by name.
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a writable property by the name
   */
  public boolean hasSetter(String propertyName) {
    return setMethods.containsKey(propertyName);
  }

  /**
   * 判断是否要取值属性方法
   * Check to see if a class has a readable property by name.
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a readable property by the name
   */
  public boolean hasGetter(String propertyName) {
    return getMethods.containsKey(propertyName);
  }

  /**
   * 查找属性
   *
   * @param name 属性名称(不区分大小写)
   * @return 对象属性
   */
  public String findPropertyName(String name) {
    return caseInsensitivePropertyMap.get(name.toUpperCase(Locale.ENGLISH));
  }
}
