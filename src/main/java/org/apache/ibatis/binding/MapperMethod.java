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
package org.apache.ibatis.binding;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.ibatis.annotations.Flush;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.reflection.TypeParameterResolver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 * @author Lasse Voss
 * @author Kazuki Shimizu
 */
public class MapperMethod {

  private final SqlCommand command;
  private final MethodSignature method;

  public MapperMethod(Class<?> mapperInterface, Method method, Configuration config) {
    this.command = new SqlCommand(config, mapperInterface, method);
    this.method = new MethodSignature(config, mapperInterface, method);
  }
  
  /**
   * 执行mapper代理方法
   * 也就是当执行具体mappr方法调用的时候，是委托给sqlSession来执行处理逻辑的。
   * @param sqlSession sqlSession
   * @param args       方法参数
   * @return 返回值
   */
  public Object execute(SqlSession sqlSession, Object[] args) {
    Object result;
    //这里其实对于sqlSession来说只有update和select的区别。
    switch (command.getType()) {
      case INSERT: {
        Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.insert(command.getName(), param));
        break;
      }
      case UPDATE: {
        Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.update(command.getName(), param));
        break;
      }
      case DELETE: {
        Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.delete(command.getName(), param));
        break;
      }
      case SELECT:
        //查询方法返回值情况稍微多点
        if (method.returnsVoid() && method.hasResultHandler()) {
          executeWithResultHandler(sqlSession, args);
          result = null;
        } else if (method.returnsMany()) {
          result = executeForMany(sqlSession, args);
        } else if (method.returnsMap()) {
          result = executeForMap(sqlSession, args);
        } else if (method.returnsCursor()) {
          result = executeForCursor(sqlSession, args);
        } else {
          Object param = method.convertArgsToSqlCommandParam(args);
          result = sqlSession.selectOne(command.getName(), param);
          if (method.returnsOptional()
              && (result == null || !method.getReturnType().equals(result.getClass()))) {
            result = Optional.ofNullable(result);
          }
        }
        break;
      case FLUSH:
        //刷新缓存
        result = sqlSession.flushStatements();
        break;
      default:
        throw new BindingException("Unknown execution method for: " + command.getName());
    }
    if (result == null && method.getReturnType().isPrimitive() && !method.returnsVoid()) {
      throw new BindingException("Mapper method '" + command.getName()
          + " attempted to return null from a method with a primitive return type (" + method.getReturnType() + ").");
    }
    return result;
  }
  
  /**
   * 返回影响结果集
   *
   * @param rowCount 行结果集
   * @return 返回值（支持void，int（含包装类），long（含包装类），boolean（含包装类））
   */
  private Object rowCountResult(int rowCount) {
    final Object result;
    if (method.returnsVoid()) {
      result = null;
    } else if (Integer.class.equals(method.getReturnType()) || Integer.TYPE.equals(method.getReturnType())) {
      result = rowCount;
    } else if (Long.class.equals(method.getReturnType()) || Long.TYPE.equals(method.getReturnType())) {
      result = (long) rowCount;
    } else if (Boolean.class.equals(method.getReturnType()) || Boolean.TYPE.equals(method.getReturnType())) {
      result = rowCount > 0;
    } else {
      throw new BindingException("Mapper method '" + command.getName() + "' has an unsupported return type: " + method.getReturnType());
    }
    return result;
  }
  
  /**
   * 自定义resultHandler处理
   *
   * @param sqlSession sqlSession
   * @param args       方法参数
   */
  private void executeWithResultHandler(SqlSession sqlSession, Object[] args) {
    MappedStatement ms = sqlSession.getConfiguration().getMappedStatement(command.getName());
    if (!StatementType.CALLABLE.equals(ms.getStatementType())
        && void.class.equals(ms.getResultMaps().get(0).getType())) {
      throw new BindingException("method " + command.getName()
          + " needs either a @ResultMap annotation, a @ResultType annotation,"
          + " or a resultType attribute in XML so a ResultHandler can be used as a parameter.");
    }
    Object param = method.convertArgsToSqlCommandParam(args);
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      sqlSession.select(command.getName(), param, rowBounds, method.extractResultHandler(args));
    } else {
      sqlSession.select(command.getName(), param, method.extractResultHandler(args));
    }
  }
  
  /**
   * 处理返回多结果集的情况
   * @param sqlSession sqlSession
   * @param args 方法参数
   * @param <E> 泛型
   * @return list or array
   */
  private <E> Object executeForMany(SqlSession sqlSession, Object[] args) {
    List<E> result;
    Object param = method.convertArgsToSqlCommandParam(args);
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      result = sqlSession.selectList(command.getName(), param, rowBounds);
    } else {
      result = sqlSession.selectList(command.getName(), param);
    }
    // issue #510 Collections & arrays support
    if (!method.getReturnType().isAssignableFrom(result.getClass())) {
      if (method.getReturnType().isArray()) {
        return convertToArray(result);
      } else {
        return convertToDeclaredCollection(sqlSession.getConfiguration(), result);
      }
    }
    return result;
  }
  
  /**
   * 游标查询
   *
   * @param sqlSession sqlSession
   * @param args       方法参数
   * @param <T>        泛型
   * @return 游标
   */
  private <T> Cursor<T> executeForCursor(SqlSession sqlSession, Object[] args) {
    Cursor<T> result;
    Object param = method.convertArgsToSqlCommandParam(args);
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      result = sqlSession.selectCursor(command.getName(), param, rowBounds);
    } else {
      result = sqlSession.selectCursor(command.getName(), param);
    }
    return result;
  }
  
  /**
   * 结果集转换collection
   *
   * @param config configuration
   * @param list   结果集
   * @param <E>    泛型
   * @return collection
   */
  private <E> Object convertToDeclaredCollection(Configuration config, List<E> list) {
    Object collection = config.getObjectFactory().create(method.getReturnType());
    MetaObject metaObject = config.newMetaObject(collection);
    metaObject.addAll(list);
    return collection;
  }
  
  /**
   * list转换为数组
   *
   * @param list list
   * @param <E>  泛型
   * @return 数组返回值
   */
  @SuppressWarnings("unchecked")
  private <E> Object convertToArray(List<E> list) {
    Class<?> arrayComponentType = method.getReturnType().getComponentType();
    Object array = Array.newInstance(arrayComponentType, list.size());
    //处理基本类型转换
    if (arrayComponentType.isPrimitive()) {
      for (int i = 0; i < list.size(); i++) {
        Array.set(array, i, list.get(i));
      }
      return array;
    } else {
      //直接转换成数组
      return list.toArray((E[]) array);
    }
  }
  
  /**
   * 处理返回map
   *
   * @param sqlSession sqlSession
   * @param args       方法参数
   * @param <K>        k
   * @param <V>        v
   * @return map返回值
   */
  private <K, V> Map<K, V> executeForMap(SqlSession sqlSession, Object[] args) {
    Map<K, V> result;
    Object param = method.convertArgsToSqlCommandParam(args);
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      result = sqlSession.selectMap(command.getName(), param, method.getMapKey(), rowBounds);
    } else {
      result = sqlSession.selectMap(command.getName(), param, method.getMapKey());
    }
    return result;
  }

  public static class ParamMap<V> extends HashMap<String, V> {

    private static final long serialVersionUID = -2212268410512043556L;

    @Override
    public V get(Object key) {
      if (!super.containsKey(key)) {
        throw new BindingException("Parameter '" + key + "' not found. Available parameters are " + keySet());
      }
      return super.get(key);
    }

  }

  /**
   * SQL命令
   */
  public static class SqlCommand {

    /**
     * MappedStatementId
     */
    private final String name;
    /**
     * SQL命令类型
     */
    private final SqlCommandType type;

    public SqlCommand(Configuration configuration, Class<?> mapperInterface, Method method) {
      final String methodName = method.getName();
      final Class<?> declaringClass = method.getDeclaringClass();
      MappedStatement ms = resolveMappedStatement(mapperInterface, methodName, declaringClass,
          configuration);
      if (ms == null) {
        //当找不到MappedStatement的时候，查看方法上是否标记Flush注解用来刷新缓存
        if (method.getAnnotation(Flush.class) != null) {
          name = null;
          type = SqlCommandType.FLUSH;
        } else {
          //如果不是Flush方法的话，就必须要有具体MappedStatement来，也就是实现方法一样的。
          throw new BindingException("Invalid bound statement (not found): "
              + mapperInterface.getName() + "." + methodName);
        }
      } else {
        name = ms.getId();
        type = ms.getSqlCommandType();
        if (type == SqlCommandType.UNKNOWN) {
          //未知命令需要抛出异常
          throw new BindingException("Unknown execution method for: " + name);
        }
      }
    }

    public String getName() {
      return name;
    }

    public SqlCommandType getType() {
      return type;
    }

    /**
     * 解析MappedStatement
     *
     * @param mapperInterface mapperClass
     * @param methodName      方法名称
     * @param declaringClass  方法申明类
     * @param configuration   Configuration对象
     * @return mapperStatement
     */
    private MappedStatement resolveMappedStatement(Class<?> mapperInterface, String methodName,
                                                   Class<?> declaringClass, Configuration configuration) {
      String statementId = mapperInterface.getName() + "." + methodName;
      if (configuration.hasStatement(statementId)) {  //首先判断是否含有映射语句，不能直接get，如果直接get的话，当为空的时候就会异常
        return configuration.getMappedStatement(statementId);
      } else if (mapperInterface.equals(declaringClass)) {
        //中断递归调用
        return null;
      }
      /*
        当存在接口继承的时候，需要递归查找mapperStatement
        如果继承了，复写了statement的情况下，就会在上面的判断就会退出去了，这里处理方法继承未重写的情况。
        当然也不能通过方法名的所在class来获取对应的mapperStatement，因为不能确定是在哪个mapper.xml里面复写掉的，只能通过当前类往上找。
       */
      for (Class<?> superInterface : mapperInterface.getInterfaces()) {
        if (declaringClass.isAssignableFrom(superInterface)) {
          MappedStatement ms = resolveMappedStatement(superInterface, methodName,
            declaringClass, configuration);
          if (ms != null) {
            return ms;
          }
        }
      }
      return null;
    }
  }

  /**
   * 方法签名对象
   */
  public static class MethodSignature {

    /**
     * 是否返回多条（返回值为集合或数组的情况）
     */
    private final boolean returnsMany;
    /**
     * 是否返回Map
     */
    private final boolean returnsMap;
    /**
     * 是否无返回值
     */
    private final boolean returnsVoid;
    /**
     * 是否返回游标
     */
    private final boolean returnsCursor;
    /**
     * 是否返回Optional
     */
    private final boolean returnsOptional;
    /**
     * 返回值类型
     */
    private final Class<?> returnType;
    /**
     * 当返回值为Map时，当做key的列
     */
    private final String mapKey;
    /**
     * ResultHandler参数索引
     */
    private final Integer resultHandlerIndex;
    /**
     * RowBounds参数索引
     */
    private final Integer rowBoundsIndex;
    /**
     * 参数解析器
     */
    private final ParamNameResolver paramNameResolver;

    public MethodSignature(Configuration configuration, Class<?> mapperInterface, Method method) {
      Type resolvedReturnType = TypeParameterResolver.resolveReturnType(method, mapperInterface);
      if (resolvedReturnType instanceof Class<?>) {
        this.returnType = (Class<?>) resolvedReturnType;
      } else if (resolvedReturnType instanceof ParameterizedType) {
        this.returnType = (Class<?>) ((ParameterizedType) resolvedReturnType).getRawType();
      } else {
        this.returnType = method.getReturnType();
      }
      this.returnsVoid = void.class.equals(this.returnType);
      this.returnsMany = configuration.getObjectFactory().isCollection(this.returnType) || this.returnType.isArray();
      this.returnsCursor = Cursor.class.equals(this.returnType);
      this.returnsOptional = Optional.class.equals(this.returnType);
      this.mapKey = getMapKey(method);
      this.returnsMap = this.mapKey != null;
      this.rowBoundsIndex = getUniqueParamIndex(method, RowBounds.class);
      this.resultHandlerIndex = getUniqueParamIndex(method, ResultHandler.class);
      this.paramNameResolver = new ParamNameResolver(configuration, method);
    }
  
    /**
     * 将方法参数转转为sql命令参数（多参数转单参数）
     *
     * @param args 方法参数
     * @return 参数
     */
    public Object convertArgsToSqlCommandParam(Object[] args) {
      return paramNameResolver.getNamedParams(args);
    }
  
    /**
     * 判断是否有RowBounds参数
     *
     * @return 是否含有
     */
    public boolean hasRowBounds() {
      return rowBoundsIndex != null;
    }
  
    /**
     * 提取RowBounds参数
     *
     * @param args 方法参数
     * @return RowBounds
     */
    public RowBounds extractRowBounds(Object[] args) {
      return hasRowBounds() ? (RowBounds) args[rowBoundsIndex] : null;
    }
  
    /**
     * 判断是否有ResultHandler参数
     *
     * @return 是否含有
     */
    public boolean hasResultHandler() {
      return resultHandlerIndex != null;
    }
  
    /**
     * 提取ResultHandler参数
     *
     * @param args 方法参数
     * @return ResultHandler
     */
    public ResultHandler extractResultHandler(Object[] args) {
      return hasResultHandler() ? (ResultHandler) args[resultHandlerIndex] : null;
    }
  
    /**
     * 获取方法返回类型
     *
     * @return 返回值类型
     */
    public Class<?> getReturnType() {
      return returnType;
    }
  
    /**
     * 是否返回多条记录
     *
     * @return 是否多条
     */
    public boolean returnsMany() {
      return returnsMany;
    }
  
    /**
     * 是否返回Map
     *
     * @return 是否返回Map
     */
    public boolean returnsMap() {
      return returnsMap;
    }
  
    /**
     * 是否返回void
     *
     * @return 是否返回void
     */
    public boolean returnsVoid() {
      return returnsVoid;
    }
  
    /**
     * 是否返回游标
     *
     * @return 是否返回游标
     */
    public boolean returnsCursor() {
      return returnsCursor;
    }
  
    /**
     * 是否返回Optional
     * return whether return type is {@code java.util.Optional}.
     *
     * @return return {@code true}, if return type is {@code java.util.Optional}
     * @since 3.5.0
     */
    public boolean returnsOptional() {
      return returnsOptional;
    }
  
    /**
     * 获取指定唯一参数下标位置（RowBounds，ResultHandler），方法中存在多个会抛出异常，
     *
     * @param method    方法
     * @param paramType 参数类型
     * @return 参数下标
     */
    private Integer getUniqueParamIndex(Method method, Class<?> paramType) {
      Integer index = null;
      final Class<?>[] argTypes = method.getParameterTypes();
      for (int i = 0; i < argTypes.length; i++) {
        if (paramType.isAssignableFrom(argTypes[i])) {
          if (index == null) {
            index = i;
          } else {
            throw new BindingException(method.getName() + " cannot have multiple " + paramType.getSimpleName() + " parameters");
          }
        }
      }
      return index;
    }
  
    /**
     * 获取生成map的key值列
     *
     * @return key值列
     */
    public String getMapKey() {
      return mapKey;
    }
  
    /**
     * 当返回值为map的时候，指定生成key值得字段。
     *
     * @param method mapper方法
     * @return key值列
     */
    private String getMapKey(Method method) {
      String mapKey = null;
      //当返回值为map的时候，反射获取方法上MapKey注解。
      if (Map.class.isAssignableFrom(method.getReturnType())) {
        final MapKey mapKeyAnnotation = method.getAnnotation(MapKey.class);
        if (mapKeyAnnotation != null) {
          mapKey = mapKeyAnnotation.value();
        }
      }
      return mapKey;
    }
  }

}
