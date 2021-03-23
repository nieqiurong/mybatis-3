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
package org.apache.ibatis.session;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.BiFunction;

import org.apache.ibatis.binding.MapperRegistry;
import org.apache.ibatis.builder.CacheRefResolver;
import org.apache.ibatis.builder.IncompleteElementException;
import org.apache.ibatis.builder.ResultMapResolver;
import org.apache.ibatis.builder.annotation.MethodResolver;
import org.apache.ibatis.builder.xml.XMLStatementBuilder;
import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.decorators.FifoCache;
import org.apache.ibatis.cache.decorators.LruCache;
import org.apache.ibatis.cache.decorators.SoftCache;
import org.apache.ibatis.cache.decorators.WeakCache;
import org.apache.ibatis.cache.impl.PerpetualCache;
import org.apache.ibatis.datasource.jndi.JndiDataSourceFactory;
import org.apache.ibatis.datasource.pooled.PooledDataSourceFactory;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSourceFactory;
import org.apache.ibatis.executor.BatchExecutor;
import org.apache.ibatis.executor.CachingExecutor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.ReuseExecutor;
import org.apache.ibatis.executor.SimpleExecutor;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.executor.loader.cglib.CglibProxyFactory;
import org.apache.ibatis.executor.loader.javassist.JavassistProxyFactory;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.resultset.DefaultResultSetHandler;
import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.executor.statement.RoutingStatementHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.logging.commons.JakartaCommonsLoggingImpl;
import org.apache.ibatis.logging.jdk14.Jdk14LoggingImpl;
import org.apache.ibatis.logging.log4j.Log4jImpl;
import org.apache.ibatis.logging.log4j2.Log4j2Impl;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.logging.slf4j.Slf4jImpl;
import org.apache.ibatis.logging.stdout.StdOutImpl;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMap;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.InterceptorChain;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.DefaultObjectFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.DefaultObjectWrapperFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.scripting.LanguageDriverRegistry;
import org.apache.ibatis.scripting.defaults.RawLanguageDriver;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.transaction.managed.ManagedTransactionFactory;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeAliasRegistry;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * @author Clinton Begin
 */
public class Configuration {

  /**
   * 环境信息
   */
  protected Environment environment;

  protected boolean safeRowBoundsEnabled;
  protected boolean safeResultHandlerEnabled = true;
  /**
   * 是否启用驼峰映射
   */
  protected boolean mapUnderscoreToCamelCase;
  protected boolean aggressiveLazyLoading;
  protected boolean multipleResultSetsEnabled = true;
  /**
   * 允许自动生成主键
   */
  protected boolean useGeneratedKeys;
  /**
   * 是否使用列标签
   * 主要出现在as语句的情况，比如 select name as userName ... 使用getColumnLabel返回的会是userName，而使用getColumnName就返回的是name了
   */
  protected boolean useColumnLabel = true;
  /**
   * 二级缓存总开关,只是用来控制下执行器模式.
   * 最终的二级缓存还要配合在xml中配置<cache/>或使用{@link org.apache.ibatis.annotations.CacheNamespace}
   * 因为二级缓存的最终实现还是在{@link CachingExecutor}
   *
   * @see #newExecutor(Transaction, ExecutorType)
   */
  protected boolean cacheEnabled = true;
  /**
   * 返回值为null是否调用对象set方法，例如返回值为map的时候，如果设置为false，当这列值为空的时候，不会调用put方法。
   */
  protected boolean callSettersOnNulls;
  /**
   * 使用参数名称作为变量参数（jdk1.8特性，编译可以保留原参数名称）
   */
  protected boolean useActualParamName = true;
  protected boolean returnInstanceForEmptyRow;
  /**
   * 删除sql空白内容
   */
  protected boolean shrinkWhitespacesInSql;
  /**
   * 日志名称前缀
   * 主要控制statementLog的打印 （前缀+命名空间+方法名）
   * DEBUG nieqiuqiu-org.apache.ibatis.autoconstructor.AutoConstructorMapper.getSubject [main] - ==>  Preparing: SELECT * FROM subject WHERE id = ?
   * DEBUG nieqiuqiu-org.apache.ibatis.autoconstructor.AutoConstructorMapper.getSubject [main] - ==> Parameters: 1(Integer)
   * DEBUG nieqiuqiu-org.apache.ibatis.autoconstructor.AutoConstructorMapper.getSubject [main] - <==      Total: 1
   */
  protected String logPrefix;
  /**
   * 日志实现类
   */
  protected Class<? extends Log> logImpl;
  /**
   * VFS实现类
   */
  protected Class<? extends VFS> vfsImpl;
  /**
   * 一级缓存作用范围
   */
  protected LocalCacheScope localCacheScope = LocalCacheScope.SESSION;
  /**
   * 参数为空，且未指定jdbcType时，默认空值jdbc类型 {@link PreparedStatement#setNull(int, int)}
   * 暂时在Oracle数据库下出问题多，需切换到NULL。
   */
  protected JdbcType jdbcTypeForNull = JdbcType.OTHER;
  protected Set<String> lazyLoadTriggerMethods = new HashSet<>(Arrays.asList("equals", "clone", "hashCode", "toString"));
  /**
   * 超时时间 {@link Statement#setQueryTimeout(int)}
   */
  protected Integer defaultStatementTimeout;
  protected Integer defaultFetchSize;
  protected ResultSetType defaultResultSetType;
  /**
   * 默认执行器对象
   */
  protected ExecutorType defaultExecutorType = ExecutorType.SIMPLE;
  protected AutoMappingBehavior autoMappingBehavior = AutoMappingBehavior.PARTIAL;
  protected AutoMappingUnknownColumnBehavior autoMappingUnknownColumnBehavior = AutoMappingUnknownColumnBehavior.NONE;

  /**
   * 属性配置
   */
  protected Properties variables = new Properties();
  /**
   * 反射工厂实例
   */
  protected ReflectorFactory reflectorFactory = new DefaultReflectorFactory();
  /**
   * 对象工厂实例
   */
  protected ObjectFactory objectFactory = new DefaultObjectFactory();
  /**
   * 对象包装工厂
   */
  protected ObjectWrapperFactory objectWrapperFactory = new DefaultObjectWrapperFactory();
  /**
   * 延迟加载开关
   */
  protected boolean lazyLoadingEnabled = false;
  /**
   * 代理创建工厂
   */
  protected ProxyFactory proxyFactory = new JavassistProxyFactory(); // #224 Using internal Javassist instead of OGNL
  /**
   * 数据库厂商标识，这功能比较鸡肋。不用关心
   */
  protected String databaseId;
  /**
   * Configuration factory class.
   * Used to create Configuration for loading deserialized unread properties.
   *
   * @see <a href='https://code.google.com/p/mybatis/issues/detail?id=300'>Issue 300 (google code)</a>
   */
  protected Class<?> configurationFactory;

  /**
   * mapper注册对象
   */
  protected final MapperRegistry mapperRegistry = new MapperRegistry(this);
  /**
   * 拦截器调用链
   */
  protected final InterceptorChain interceptorChain = new InterceptorChain();
  /**
   * 类型处理器注册对象
   */
  protected final TypeHandlerRegistry typeHandlerRegistry = new TypeHandlerRegistry(this);
  /**
   * 别名注册对象
   */
  protected final TypeAliasRegistry typeAliasRegistry = new TypeAliasRegistry();
  /**
   * 动态脚本语言注册对象
   */
  protected final LanguageDriverRegistry languageRegistry = new LanguageDriverRegistry();

  protected final Map<String, MappedStatement> mappedStatements = new StrictMap<MappedStatement>("Mapped Statements collection")
      .conflictMessageProducer((savedValue, targetValue) ->
          ". please check " + savedValue.getResource() + " and " + targetValue.getResource());
  protected final Map<String, Cache> caches = new StrictMap<>("Caches collection");
  protected final Map<String, ResultMap> resultMaps = new StrictMap<>("Result Maps collection");
  protected final Map<String, ParameterMap> parameterMaps = new StrictMap<>("Parameter Maps collection");
  /**
   * 主键生成器集合
   */
  protected final Map<String, KeyGenerator> keyGenerators = new StrictMap<>("Key Generators collection");

  /**
   * 存储已加载过的mapper资源
   */
  protected final Set<String> loadedResources = new HashSet<>();
  protected final Map<String, XNode> sqlFragments = new StrictMap<>("XML fragments parsed from previous mappers");

  /**
   * 缓存解析失败的Statement
   */
  protected final Collection<XMLStatementBuilder> incompleteStatements = new LinkedList<>();
  /**
   * 缓存解析失败的缓存引用
   */
  protected final Collection<CacheRefResolver> incompleteCacheRefs = new LinkedList<>();
  /**
   * 缓存解析失败的ResultMap引用
   */
  protected final Collection<ResultMapResolver> incompleteResultMaps = new LinkedList<>();
  /**
   * 缓存解析失败的方法（注解情况）
   */
  protected final Collection<MethodResolver> incompleteMethods = new LinkedList<>();

  /*
   * A map holds cache-ref relationship. The key is the namespace that
   * references a cache bound to another namespace and the value is the
   * namespace which the actual cache is bound to.
   */
  /**
   * 缓存引用列表
   */
  protected final Map<String, String> cacheRefMap = new HashMap<>();

  public Configuration(Environment environment) {
    this();
    this.environment = environment;
  }

  public Configuration() {
    //注册别名事务工厂实例
    typeAliasRegistry.registerAlias("JDBC", JdbcTransactionFactory.class);
    typeAliasRegistry.registerAlias("MANAGED", ManagedTransactionFactory.class);
    //注册数据源别名实例
    typeAliasRegistry.registerAlias("JNDI", JndiDataSourceFactory.class);
    typeAliasRegistry.registerAlias("POOLED", PooledDataSourceFactory.class);
    typeAliasRegistry.registerAlias("UNPOOLED", UnpooledDataSourceFactory.class);
    //缓存实现别名注册
    typeAliasRegistry.registerAlias("PERPETUAL", PerpetualCache.class);
    typeAliasRegistry.registerAlias("FIFO", FifoCache.class);
    typeAliasRegistry.registerAlias("LRU", LruCache.class);
    typeAliasRegistry.registerAlias("SOFT", SoftCache.class);
    typeAliasRegistry.registerAlias("WEAK", WeakCache.class);
    //数据库厂商别名注册
    typeAliasRegistry.registerAlias("DB_VENDOR", VendorDatabaseIdProvider.class);
    //动态sql脚本语言处理别名注册
    typeAliasRegistry.registerAlias("XML", XMLLanguageDriver.class);
    typeAliasRegistry.registerAlias("RAW", RawLanguageDriver.class);
    //日志别名注册
    typeAliasRegistry.registerAlias("SLF4J", Slf4jImpl.class);
    typeAliasRegistry.registerAlias("COMMONS_LOGGING", JakartaCommonsLoggingImpl.class);
    typeAliasRegistry.registerAlias("LOG4J", Log4jImpl.class);
    typeAliasRegistry.registerAlias("LOG4J2", Log4j2Impl.class);
    typeAliasRegistry.registerAlias("JDK_LOGGING", Jdk14LoggingImpl.class);
    typeAliasRegistry.registerAlias("STDOUT_LOGGING", StdOutImpl.class);
    typeAliasRegistry.registerAlias("NO_LOGGING", NoLoggingImpl.class);
    //代理对象工厂别名注册
    typeAliasRegistry.registerAlias("CGLIB", CglibProxyFactory.class);
    typeAliasRegistry.registerAlias("JAVASSIST", JavassistProxyFactory.class);
    //设置默认脚本语言处理
    languageRegistry.setDefaultDriverClass(XMLLanguageDriver.class);
    //注册静态sql语言处理
    languageRegistry.register(RawLanguageDriver.class);
  }

  /**
   * 获取日志前缀
   *
   * @return 日志前缀
   */
  public String getLogPrefix() {
    return logPrefix;
  }

  /**
   * 设置日志前缀
   *
   * @param logPrefix 日志前缀
   */
  public void setLogPrefix(String logPrefix) {
    this.logPrefix = logPrefix;
  }

  /**
   * 获取日志实现类
   *
   * @return 日志实现
   */
  public Class<? extends Log> getLogImpl() {
    return logImpl;
  }

  /**
   * 设置日志实现类
   *
   * @param logImpl 日志实现类
   */
  public void setLogImpl(Class<? extends Log> logImpl) {
    if (logImpl != null) {
      this.logImpl = logImpl;
      LogFactory.useCustomLogging(this.logImpl);
    }
  }

  /**
   * 获取VFS实现
   *
   * @return vfs实现类
   */
  public Class<? extends VFS> getVfsImpl() {
    return this.vfsImpl;
  }

  /**
   * 设置VFS实现
   *
   * @param vfsImpl vfs实现类
   */
  public void setVfsImpl(Class<? extends VFS> vfsImpl) {
    if (vfsImpl != null) {
      this.vfsImpl = vfsImpl;
      VFS.addImplClass(this.vfsImpl);
    }
  }

  /**
   * 返回值为null是否调用对象set方法
   *
   * @return 是否调用
   */
  public boolean isCallSettersOnNulls() {
    return callSettersOnNulls;
  }

  /**
   * 设置返回值为null是否调用对象set方法
   *
   * @param callSettersOnNulls 是否调用
   */
  public void setCallSettersOnNulls(boolean callSettersOnNulls) {
    this.callSettersOnNulls = callSettersOnNulls;
  }

  /**
   * 是否使用参数名称作为变量参数
   *
   * @return 是否使用
   */
  public boolean isUseActualParamName() {
    return useActualParamName;
  }

  /**
   * 设置使用参数名称作为变量参数
   *
   * @param useActualParamName 是否使用
   */
  public void setUseActualParamName(boolean useActualParamName) {
    this.useActualParamName = useActualParamName;
  }

  public boolean isReturnInstanceForEmptyRow() {
    return returnInstanceForEmptyRow;
  }

  public void setReturnInstanceForEmptyRow(boolean returnEmptyInstance) {
    this.returnInstanceForEmptyRow = returnEmptyInstance;
  }

  public boolean isShrinkWhitespacesInSql() {
    return shrinkWhitespacesInSql;
  }

  public void setShrinkWhitespacesInSql(boolean shrinkWhitespacesInSql) {
    this.shrinkWhitespacesInSql = shrinkWhitespacesInSql;
  }

  /**
   * 获取数据库厂商标识
   *
   * @return 厂商标识
   */
  public String getDatabaseId() {
    return databaseId;
  }

  /**
   * 设置数据库厂商标识
   *
   * @param databaseId 厂商标识
   */
  public void setDatabaseId(String databaseId) {
    this.databaseId = databaseId;
  }

  public Class<?> getConfigurationFactory() {
    return configurationFactory;
  }

  public void setConfigurationFactory(Class<?> configurationFactory) {
    this.configurationFactory = configurationFactory;
  }

  public boolean isSafeResultHandlerEnabled() {
    return safeResultHandlerEnabled;
  }

  public void setSafeResultHandlerEnabled(boolean safeResultHandlerEnabled) {
    this.safeResultHandlerEnabled = safeResultHandlerEnabled;
  }

  public boolean isSafeRowBoundsEnabled() {
    return safeRowBoundsEnabled;
  }

  public void setSafeRowBoundsEnabled(boolean safeRowBoundsEnabled) {
    this.safeRowBoundsEnabled = safeRowBoundsEnabled;
  }

  /**
   * 是否启用驼峰映射
   *
   * @return 是否启用
   */
  public boolean isMapUnderscoreToCamelCase() {
    return mapUnderscoreToCamelCase;
  }

  /**
   * 设置是否启用驼峰映射
   *
   * @param mapUnderscoreToCamelCase 是否驼峰映射
   */
  public void setMapUnderscoreToCamelCase(boolean mapUnderscoreToCamelCase) {
    this.mapUnderscoreToCamelCase = mapUnderscoreToCamelCase;
  }

  /**
   * 标记资源已加载完成
   *
   * @param resource 资源
   */
  public void addLoadedResource(String resource) {
    loadedResources.add(resource);
  }

  /**
   * 判断资源是否被加载
   *
   * @param resource 资源
   * @return 是否已加载
   */
  public boolean isResourceLoaded(String resource) {
    return loadedResources.contains(resource);
  }

  /**
   * 获取环境配置信息
   *
   * @return 环境信息
   */
  public Environment getEnvironment() {
    return environment;
  }

  /**
   * 设置环境配置信息
   *
   * @param environment 环境信息
   */
  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }

  public AutoMappingBehavior getAutoMappingBehavior() {
    return autoMappingBehavior;
  }

  public void setAutoMappingBehavior(AutoMappingBehavior autoMappingBehavior) {
    this.autoMappingBehavior = autoMappingBehavior;
  }

  /**
   * Gets the auto mapping unknown column behavior.
   *
   * @return the auto mapping unknown column behavior
   * @since 3.4.0
   */
  public AutoMappingUnknownColumnBehavior getAutoMappingUnknownColumnBehavior() {
    return autoMappingUnknownColumnBehavior;
  }

  /**
   * Sets the auto mapping unknown column behavior.
   *
   * @param autoMappingUnknownColumnBehavior
   *          the new auto mapping unknown column behavior
   * @since 3.4.0
   */
  public void setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior autoMappingUnknownColumnBehavior) {
    this.autoMappingUnknownColumnBehavior = autoMappingUnknownColumnBehavior;
  }

  /**
   * 是否延迟加载
   *
   * @return 是否延迟加载
   */
  public boolean isLazyLoadingEnabled() {
    return lazyLoadingEnabled;
  }

  /**
   * 设置延迟加载
   *
   * @param lazyLoadingEnabled 是否延迟加载
   */
  public void setLazyLoadingEnabled(boolean lazyLoadingEnabled) {
    this.lazyLoadingEnabled = lazyLoadingEnabled;
  }

  /**
   * 获取对象代理工厂
   *
   * @return 对象代理工厂
   */
  public ProxyFactory getProxyFactory() {
    return proxyFactory;
  }

  /**
   * 设置对象代理工厂
   *
   * @param proxyFactory 对象代理工厂，默认JavassistProxyFactory
   */
  public void setProxyFactory(ProxyFactory proxyFactory) {
    if (proxyFactory == null) {
      proxyFactory = new JavassistProxyFactory();
    }
    this.proxyFactory = proxyFactory;
  }

  public boolean isAggressiveLazyLoading() {
    return aggressiveLazyLoading;
  }

  public void setAggressiveLazyLoading(boolean aggressiveLazyLoading) {
    this.aggressiveLazyLoading = aggressiveLazyLoading;
  }

  public boolean isMultipleResultSetsEnabled() {
    return multipleResultSetsEnabled;
  }

  public void setMultipleResultSetsEnabled(boolean multipleResultSetsEnabled) {
    this.multipleResultSetsEnabled = multipleResultSetsEnabled;
  }

  public Set<String> getLazyLoadTriggerMethods() {
    return lazyLoadTriggerMethods;
  }

  public void setLazyLoadTriggerMethods(Set<String> lazyLoadTriggerMethods) {
    this.lazyLoadTriggerMethods = lazyLoadTriggerMethods;
  }

  /**
   * 允许自动生成主键
   *
   * @return 是否允许自动生成
   */
  public boolean isUseGeneratedKeys() {
    return useGeneratedKeys;
  }

  /**
   * 设置是否自动生成主键
   *
   * @param useGeneratedKeys 自动生成主键
   */
  public void setUseGeneratedKeys(boolean useGeneratedKeys) {
    this.useGeneratedKeys = useGeneratedKeys;
  }

  /**
   * 获取默认执行器
   *
   * @return 执行器
   */
  public ExecutorType getDefaultExecutorType() {
    return defaultExecutorType;
  }

  /**
   * 设置默认执行器
   *
   * @param defaultExecutorType 执行器
   */
  public void setDefaultExecutorType(ExecutorType defaultExecutorType) {
    this.defaultExecutorType = defaultExecutorType;
  }

  /**
   * 是否启用缓存
   *
   * @return 是否启用
   */
  public boolean isCacheEnabled() {
    return cacheEnabled;
  }

  /**
   * 设置缓存是否启用
   *
   * @param cacheEnabled 是否启用
   */
  public void setCacheEnabled(boolean cacheEnabled) {
    this.cacheEnabled = cacheEnabled;
  }

  /**
   * 获取默认超时时间
   *
   * @return 超时时间
   */
  public Integer getDefaultStatementTimeout() {
    return defaultStatementTimeout;
  }

  /**
   * 设置默认超时时间
   *
   * @param defaultStatementTimeout 超市时间
   */
  public void setDefaultStatementTimeout(Integer defaultStatementTimeout) {
    this.defaultStatementTimeout = defaultStatementTimeout;
  }

  /**
   * Gets the default fetch size.
   *
   * @return the default fetch size
   * @since 3.3.0
   */
  public Integer getDefaultFetchSize() {
    return defaultFetchSize;
  }

  /**
   * Sets the default fetch size.
   *
   * @param defaultFetchSize
   *          the new default fetch size
   * @since 3.3.0
   */
  public void setDefaultFetchSize(Integer defaultFetchSize) {
    this.defaultFetchSize = defaultFetchSize;
  }

  /**
   * Gets the default result set type.
   *
   * @return the default result set type
   * @since 3.5.2
   */
  public ResultSetType getDefaultResultSetType() {
    return defaultResultSetType;
  }

  /**
   * Sets the default result set type.
   *
   * @param defaultResultSetType
   *          the new default result set type
   * @since 3.5.2
   */
  public void setDefaultResultSetType(ResultSetType defaultResultSetType) {
    this.defaultResultSetType = defaultResultSetType;
  }

  /**
   * 是否使用列标签
   *
   * @return 是否使用
   */
  public boolean isUseColumnLabel() {
    return useColumnLabel;
  }

  /**
   * 设置是否使用列标签
   *
   * @param useColumnLabel 是否使用
   */
  public void setUseColumnLabel(boolean useColumnLabel) {
    this.useColumnLabel = useColumnLabel;
  }

  /**
   * 获取一级缓存作用范围
   *
   * @return 作用范围
   */
  public LocalCacheScope getLocalCacheScope() {
    return localCacheScope;
  }

  /**
   * 设置一级缓存作用范围
   *
   * @param localCacheScope 作用范围
   */
  public void setLocalCacheScope(LocalCacheScope localCacheScope) {
    this.localCacheScope = localCacheScope;
  }

  /**
   * 默认空值jdbc处理类型
   *
   * @return jdbc类型
   */
  public JdbcType getJdbcTypeForNull() {
    return jdbcTypeForNull;
  }

  /**
   * 设置空值jdbc处理类型
   *
   * @param jdbcTypeForNull 处理类型
   */
  public void setJdbcTypeForNull(JdbcType jdbcTypeForNull) {
    this.jdbcTypeForNull = jdbcTypeForNull;
  }

  /**
   * 获取属性配置
   *
   * @return 属性配置
   */
  public Properties getVariables() {
    return variables;
  }

  /**
   * 设置属性配置
   *
   * @param variables 属性配置
   */
  public void setVariables(Properties variables) {
    this.variables = variables;
  }

  /**
   * 获取类型处理器注册对象
   *
   * @return 注册对象
   */
  public TypeHandlerRegistry getTypeHandlerRegistry() {
    return typeHandlerRegistry;
  }

  /**
   * 设置默认枚举处理器
   * Set a default {@link TypeHandler} class for {@link Enum}.
   * A default {@link TypeHandler} is {@link org.apache.ibatis.type.EnumTypeHandler}.
   *
   * @param typeHandler a type handler class for {@link Enum}
   * @since 3.4.5
   */
  public void setDefaultEnumTypeHandler(Class<? extends TypeHandler> typeHandler) {
    if (typeHandler != null) {
      getTypeHandlerRegistry().setDefaultEnumTypeHandler(typeHandler);
    }
  }

  /**
   * 获取别名注册对象
   *
   * @return 注册对象
   */
  public TypeAliasRegistry getTypeAliasRegistry() {
    return typeAliasRegistry;
  }

  /**
   * 获取mapper注册
   *
   * @return the mapper registry
   * @since 3.2.2
   */
  public MapperRegistry getMapperRegistry() {
    return mapperRegistry;
  }

  /**
   * 获取反射工厂
   *
   * @return 反射工厂
   */
  public ReflectorFactory getReflectorFactory() {
    return reflectorFactory;
  }

  /**
   * 设置反射工厂
   *
   * @param reflectorFactory 反射工厂
   */
  public void setReflectorFactory(ReflectorFactory reflectorFactory) {
    this.reflectorFactory = reflectorFactory;
  }

  /**
   * 获取对象创建工厂
   *
   * @return 对象工厂
   */
  public ObjectFactory getObjectFactory() {
    return objectFactory;
  }

  /**
   * 设置对象创建工厂
   *
   * @param objectFactory 对象工厂
   */
  public void setObjectFactory(ObjectFactory objectFactory) {
    this.objectFactory = objectFactory;
  }

  /**
   * 获取对象包装工厂
   *
   * @return 对象包装工厂
   */
  public ObjectWrapperFactory getObjectWrapperFactory() {
    return objectWrapperFactory;
  }

  /**
   * 设置对象包装工厂
   *
   * @param objectWrapperFactory 包装工厂
   */
  public void setObjectWrapperFactory(ObjectWrapperFactory objectWrapperFactory) {
    this.objectWrapperFactory = objectWrapperFactory;
  }

  /**
   * 获取所有注册插件集合
   *
   * @return the interceptors 插件集合
   * @since 3.2.2
   */
  public List<Interceptor> getInterceptors() {
    return interceptorChain.getInterceptors();
  }

  /**
   * 获取动态脚本处理语言注册对象
   *
   * @return 注册对象
   */
  public LanguageDriverRegistry getLanguageRegistry() {
    return languageRegistry;
  }

  /**
   * 设置默认动态SQL脚本处理语言
   *
   * @param driver driver
   */
  public void setDefaultScriptingLanguage(Class<? extends LanguageDriver> driver) {
    if (driver == null) {
      driver = XMLLanguageDriver.class;
    }
    getLanguageRegistry().setDefaultDriverClass(driver);
  }

  /**
   * 获取默认动态SQL脚本处理语言
   *
   * @return 默认动态sql处理脚本语言
   */
  public LanguageDriver getDefaultScriptingLanguageInstance() {
    return languageRegistry.getDefaultDriver();
  }

  /**
   * 获取动态SQL脚本处理语言
   * Gets the language driver.
   *
   * @param langClass the lang class
   * @return the language driver
   * @since 3.5.1
   */
  public LanguageDriver getLanguageDriver(Class<? extends LanguageDriver> langClass) {
    if (langClass == null) {
      //返回默认处理语言
      return languageRegistry.getDefaultDriver();
    }
    //注册脚本语言
    languageRegistry.register(langClass);
    //返回注册的实例
    return languageRegistry.getDriver(langClass);
  }

  /**
   * Gets the default scripting lanuage instance.
   *
   * @return the default scripting lanuage instance
   * @deprecated Use {@link #getDefaultScriptingLanguageInstance()}
   */
  @Deprecated
  public LanguageDriver getDefaultScriptingLanuageInstance() {
    return getDefaultScriptingLanguageInstance();
  }

  public MetaObject newMetaObject(Object object) {
    return MetaObject.forObject(object, objectFactory, objectWrapperFactory, reflectorFactory);
  }

  public ParameterHandler newParameterHandler(MappedStatement mappedStatement, Object parameterObject, BoundSql boundSql) {
    ParameterHandler parameterHandler = mappedStatement.getLang().createParameterHandler(mappedStatement, parameterObject, boundSql);
    //创建ParameterHandler后执行拦截器调用链处理
    parameterHandler = (ParameterHandler) interceptorChain.pluginAll(parameterHandler);
    return parameterHandler;
  }

  public ResultSetHandler newResultSetHandler(Executor executor, MappedStatement mappedStatement, RowBounds rowBounds, ParameterHandler parameterHandler,
      ResultHandler resultHandler, BoundSql boundSql) {
    ResultSetHandler resultSetHandler = new DefaultResultSetHandler(executor, mappedStatement, parameterHandler, resultHandler, boundSql, rowBounds);
    //创建ResultSetHandler后执行拦截器调用链处理
    resultSetHandler = (ResultSetHandler) interceptorChain.pluginAll(resultSetHandler);
    return resultSetHandler;
  }

  public StatementHandler newStatementHandler(Executor executor, MappedStatement mappedStatement, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
    StatementHandler statementHandler = new RoutingStatementHandler(executor, mappedStatement, parameterObject, rowBounds, resultHandler, boundSql);
    //创建StatementHandler后执行拦截器调用链处理
    statementHandler = (StatementHandler) interceptorChain.pluginAll(statementHandler);
    return statementHandler;
  }

  /**
   * 创建默认执行器
   *
   * @param transaction 事务对象
   * @return 执行器对象
   */
  public Executor newExecutor(Transaction transaction) {
    return newExecutor(transaction, defaultExecutorType);
  }

  /**
   * 创建执行器
   *
   * @param transaction  事务对象
   * @param executorType 执行器类型
   * @return 执行器对象
   */
  public Executor newExecutor(Transaction transaction, ExecutorType executorType) {
    executorType = executorType == null ? defaultExecutorType : executorType;
    executorType = executorType == null ? ExecutorType.SIMPLE : executorType;
    //上面这两行代码忽略不管，当做没看到
    Executor executor;
    if (ExecutorType.BATCH == executorType) {
      executor = new BatchExecutor(this, transaction);
    } else if (ExecutorType.REUSE == executorType) {
      executor = new ReuseExecutor(this, transaction);
    } else {
      executor = new SimpleExecutor(this, transaction);
    }
    if (cacheEnabled) {
      //通过CachingExecutor来装饰执行器实现二级缓存
      executor = new CachingExecutor(executor);
    }
    //创建执行器后执行拦截器调用链处理
    executor = (Executor) interceptorChain.pluginAll(executor);
    return executor;
  }

  /**
   * 注册主键生成器
   *
   * @param id           命名空间.方法名!selectKey。
   * @param keyGenerator 生成器
   */
  public void addKeyGenerator(String id, KeyGenerator keyGenerator) {
    keyGenerators.put(id, keyGenerator);
  }

  /**
   * 获取所有生成器Id
   *
   * @return 生成器集合ID列表
   */
  public Collection<String> getKeyGeneratorNames() {
    return keyGenerators.keySet();
  }

  /**
   * 获取生成器集合
   *
   * @return 生成器集合
   */
  public Collection<KeyGenerator> getKeyGenerators() {
    return keyGenerators.values();
  }

  /**
   * 获取生成器
   *
   * @param id id
   * @return 生成器
   */
  public KeyGenerator getKeyGenerator(String id) {
    return keyGenerators.get(id);
  }

  /**
   * 判断是否存在集合
   *
   * @param id 生成器Id
   * @return 是否存在
   */
  public boolean hasKeyGenerator(String id) {
    return keyGenerators.containsKey(id);
  }

  /**
   * 注册缓存
   *
   * @param cache 缓存对象
   */
  public void addCache(Cache cache) {
    caches.put(cache.getId(), cache);
  }

  /**
   * 获取所有缓存命名空间
   *
   * @return 命名空间集合
   */
  public Collection<String> getCacheNames() {
    return caches.keySet();
  }

  /**
   * 获取缓存所有值
   *
   * @return 缓存值集合
   */
  public Collection<Cache> getCaches() {
    return caches.values();
  }

  /**
   * 通过命名空间获取缓存
   *
   * @param id 命名空间
   * @return 缓存对象
   */
  public Cache getCache(String id) {
    return caches.get(id);
  }

  /**
   * 判断命名空间是否有缓存
   *
   * @param id 命名空间
   * @return 是否有缓存
   */
  public boolean hasCache(String id) {
    return caches.containsKey(id);
  }

  /**
   * 添加resultMap映射
   *
   * @param rm resultMap
   */
  public void addResultMap(ResultMap rm) {
    resultMaps.put(rm.getId(), rm);
    checkLocallyForDiscriminatedNestedResultMaps(rm);
    checkGloballyForDiscriminatedNestedResultMaps(rm);
  }

  public Collection<String> getResultMapNames() {
    return resultMaps.keySet();
  }

  public Collection<ResultMap> getResultMaps() {
    return resultMaps.values();
  }

  public ResultMap getResultMap(String id) {
    return resultMaps.get(id);
  }

  public boolean hasResultMap(String id) {
    return resultMaps.containsKey(id);
  }

  @Deprecated
  public void addParameterMap(ParameterMap pm) {
    parameterMaps.put(pm.getId(), pm);
  }

  public Collection<String> getParameterMapNames() {
    return parameterMaps.keySet();
  }

  public Collection<ParameterMap> getParameterMaps() {
    return parameterMaps.values();
  }

  public ParameterMap getParameterMap(String id) {
    return parameterMaps.get(id);
  }

  public boolean hasParameterMap(String id) {
    return parameterMaps.containsKey(id);
  }

  public void addMappedStatement(MappedStatement ms) {
    mappedStatements.put(ms.getId(), ms);
  }

  public Collection<String> getMappedStatementNames() {
    buildAllStatements();
    return mappedStatements.keySet();
  }

  public Collection<MappedStatement> getMappedStatements() {
    buildAllStatements();
    return mappedStatements.values();
  }

  public Collection<XMLStatementBuilder> getIncompleteStatements() {
    return incompleteStatements;
  }

  public void addIncompleteStatement(XMLStatementBuilder incompleteStatement) {
    incompleteStatements.add(incompleteStatement);
  }

  public Collection<CacheRefResolver> getIncompleteCacheRefs() {
    return incompleteCacheRefs;
  }

  public void addIncompleteCacheRef(CacheRefResolver incompleteCacheRef) {
    incompleteCacheRefs.add(incompleteCacheRef);
  }

  public Collection<ResultMapResolver> getIncompleteResultMaps() {
    return incompleteResultMaps;
  }

  public void addIncompleteResultMap(ResultMapResolver resultMapResolver) {
    incompleteResultMaps.add(resultMapResolver);
  }

  public void addIncompleteMethod(MethodResolver builder) {
    incompleteMethods.add(builder);
  }

  public Collection<MethodResolver> getIncompleteMethods() {
    return incompleteMethods;
  }

  public MappedStatement getMappedStatement(String id) {
    return this.getMappedStatement(id, true);
  }

  public MappedStatement getMappedStatement(String id, boolean validateIncompleteStatements) {
    if (validateIncompleteStatements) {
      buildAllStatements();
    }
    return mappedStatements.get(id);
  }

  public Map<String, XNode> getSqlFragments() {
    return sqlFragments;
  }

  /**
   * 注册插件
   * @param interceptor 插件对象
   */
  public void addInterceptor(Interceptor interceptor) {
    interceptorChain.addInterceptor(interceptor);
  }

  /**
   * 按包注册mapper（mapper实现特定的接口）
   *
   * @param packageName 包名
   * @param superType   接口
   */
  public void addMappers(String packageName, Class<?> superType) {
    mapperRegistry.addMappers(packageName, superType);
  }

  /**
   * 按包注册mapper
   *
   * @param packageName 包名
   */
  public void addMappers(String packageName) {
    mapperRegistry.addMappers(packageName);
  }

  /**
   * 注册mapper
   *
   * @param type mapperClass
   * @param <T>  T
   */
  public <T> void addMapper(Class<T> type) {
    mapperRegistry.addMapper(type);
  }

  /**
   * 获取mapper的动态代理对象
   *
   * @param type       mapperClass
   * @param sqlSession sqlSession
   * @param <T>        T
   * @return Proxy
   */
  public <T> T getMapper(Class<T> type, SqlSession sqlSession) {
    return mapperRegistry.getMapper(type, sqlSession);
  }

  /**
   * 判断当前mapperClass是否被注册过
   *
   * @param type mapperClass
   * @return 是否已注册
   */
  public boolean hasMapper(Class<?> type) {
    return mapperRegistry.hasMapper(type);
  }

  public boolean hasStatement(String statementName) {
    return hasStatement(statementName, true);
  }

  public boolean hasStatement(String statementName, boolean validateIncompleteStatements) {
    if (validateIncompleteStatements) {
      buildAllStatements();
    }
    return mappedStatements.containsKey(statementName);
  }

  /**
   * 加入缓存引用列表
   *
   * @param namespace           当前命名空间
   * @param referencedNamespace 引用命名空间
   */
  public void addCacheRef(String namespace, String referencedNamespace) {
    cacheRefMap.put(namespace, referencedNamespace);
  }

  /*
   * Parses all the unprocessed statement nodes in the cache. It is recommended
   * to call this method once all the mappers are added as it provides fail-fast
   * statement validation.
   */
  protected void buildAllStatements() {
    parsePendingResultMaps();
    if (!incompleteCacheRefs.isEmpty()) {
      synchronized (incompleteCacheRefs) {
        incompleteCacheRefs.removeIf(x -> x.resolveCacheRef() != null);
      }
    }
    if (!incompleteStatements.isEmpty()) {
      synchronized (incompleteStatements) {
        incompleteStatements.removeIf(x -> {
          x.parseStatementNode();
          return true;
        });
      }
    }
    if (!incompleteMethods.isEmpty()) {
      synchronized (incompleteMethods) {
        incompleteMethods.removeIf(x -> {
          x.resolve();
          return true;
        });
      }
    }
  }

  private void parsePendingResultMaps() {
    if (incompleteResultMaps.isEmpty()) {
      return;
    }
    synchronized (incompleteResultMaps) {
      boolean resolved;
      IncompleteElementException ex = null;
      do {
        resolved = false;
        Iterator<ResultMapResolver> iterator = incompleteResultMaps.iterator();
        while (iterator.hasNext()) {
          try {
            iterator.next().resolve();
            iterator.remove();
            resolved = true;
          } catch (IncompleteElementException e) {
            ex = e;
          }
        }
      } while (resolved);
      if (!incompleteResultMaps.isEmpty() && ex != null) {
        // At least one result map is unresolvable.
        throw ex;
      }
    }
  }

  /**
   * Extracts namespace from fully qualified statement id.
   *
   * @param statementId
   *          the statement id
   * @return namespace or null when id does not contain period.
   */
  protected String extractNamespace(String statementId) {
    int lastPeriod = statementId.lastIndexOf('.');
    return lastPeriod > 0 ? statementId.substring(0, lastPeriod) : null;
  }

  // Slow but a one time cost. A better solution is welcome.
  protected void checkGloballyForDiscriminatedNestedResultMaps(ResultMap rm) {
    if (rm.hasNestedResultMaps()) {
      for (Map.Entry<String, ResultMap> entry : resultMaps.entrySet()) {
        Object value = entry.getValue();
        if (value instanceof ResultMap) {
          ResultMap entryResultMap = (ResultMap) value;
          if (!entryResultMap.hasNestedResultMaps() && entryResultMap.getDiscriminator() != null) {
            Collection<String> discriminatedResultMapNames = entryResultMap.getDiscriminator().getDiscriminatorMap().values();
            if (discriminatedResultMapNames.contains(rm.getId())) {
              entryResultMap.forceNestedResultMaps();
            }
          }
        }
      }
    }
  }

  /**
   * 检查resultMap里面嵌套discriminator
   *   <resultMap
   *     type="org.apache.ibatis.submitted.discriminator.Vehicle"
   *     id="vehicleResult">
   *     <id property="id" column="id" />
   *     <result property="maker" column="maker" />
   *     <discriminator javaType="int" column="vehicle_type">
   *       <case value="1"
   *         resultType="org.apache.ibatis.submitted.discriminator.Car">
   *         <result property="doorCount" column="door_count" />
   *       </case>
   *       <case value="2"
   *         resultType="org.apache.ibatis.submitted.discriminator.Truck">
   *         <result property="carryingCapacity"
   *           column="carrying_capacity" />
   *       </case>
   *     </discriminator>
   *   </resultMap>
   * @param rm resultMap
   */
  // Slow but a one time cost. A better solution is welcome.
  protected void checkLocallyForDiscriminatedNestedResultMaps(ResultMap rm) {
    if (!rm.hasNestedResultMaps() && rm.getDiscriminator() != null) {
      for (Map.Entry<String, String> entry : rm.getDiscriminator().getDiscriminatorMap().entrySet()) {
        String discriminatedResultMapName = entry.getValue();
        if (hasResultMap(discriminatedResultMapName)) {
          ResultMap discriminatedResultMap = resultMaps.get(discriminatedResultMapName);
          if (discriminatedResultMap.hasNestedResultMaps()) {
            rm.forceNestedResultMaps();
            break;
          }
        }
      }
    }
  }

  protected static class StrictMap<V> extends HashMap<String, V> {

    private static final long serialVersionUID = -4950446264854982944L;
    private final String name;
    private BiFunction<V, V, String> conflictMessageProducer;

    public StrictMap(String name, int initialCapacity, float loadFactor) {
      super(initialCapacity, loadFactor);
      this.name = name;
    }

    public StrictMap(String name, int initialCapacity) {
      super(initialCapacity);
      this.name = name;
    }

    public StrictMap(String name) {
      super();
      this.name = name;
    }

    public StrictMap(String name, Map<String, ? extends V> m) {
      super(m);
      this.name = name;
    }

    /**
     * Assign a function for producing a conflict error message when contains value with the same key.
     * <p>
     * function arguments are 1st is saved value and 2nd is target value.
     * @param conflictMessageProducer A function for producing a conflict error message
     * @return a conflict error message
     * @since 3.5.0
     */
    public StrictMap<V> conflictMessageProducer(BiFunction<V, V, String> conflictMessageProducer) {
      this.conflictMessageProducer = conflictMessageProducer;
      return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public V put(String key, V value) {
      if (containsKey(key)) { //如果长key重复了的话，就会参数异常了。
        throw new IllegalArgumentException(name + " already contains value for " + key
          + (conflictMessageProducer == null ? "" : conflictMessageProducer.apply(super.get(key), value)));
      }
      //这里用来生成短key，现在基本上没啥用了，基本上都是用命名空间.方法名来的，感觉用短key不是很直观。
      //如果我们加个开关开控制下是不是会好点，就能能减少缓存的容量（好像这点内存也占不了多少），后期get的时候只有在允许生产短key的时候才需要instanceof
      if (key.contains(".")) {
        final String shortKey = getShortName(key);
        //判断短key是不是存在了
        if (super.get(shortKey) == null) {
          //放入短key缓存
          super.put(shortKey, value);
        } else {
          //这里也就是标记一下一个短key有冲突的情况存在，当用短key去get数据的时候，如果value值是Ambiguity的话，就会抛出异常了。
          super.put(shortKey, (V) new Ambiguity(shortKey));
        }
      }
      //放入完整key缓存
      return super.put(key, value);
    }

    @Override
    public V get(Object key) {
      V value = super.get(key);
      //找不到数据会抛出异常了
      if (value == null) {
        throw new IllegalArgumentException(name + " does not contain value for " + key);
      }
      //这里通过短key如果找到多个就完蛋了。
      if (value instanceof Ambiguity) {
        throw new IllegalArgumentException(((Ambiguity) value).getSubject() + " is ambiguous in " + name
            + " (try using the full name including the namespace, or rename one of the entries)");
      }
      return value;
    }

    protected static class Ambiguity {
      private final String subject;

      public Ambiguity(String subject) {
        this.subject = subject;
      }

      public String getSubject() {
        return subject;
      }
    }

    /**
     * 获取短key，也就是取最后一个.后面的内容
     * 栗子：org.apache.ibatis.builder.AnnotationMapperBuilderTest$Mapper.selectWithOptionsAndWithoutOptionsAttributes -> selectWithOptionsAndWithoutOptionsAttributes
     *
     * @param key 原key（命名空间+方法名）
     * @return 方法名
     */
    private String getShortName(String key) {
      final String[] keyParts = key.split("\\.");
      return keyParts[keyParts.length - 1];
    }
  }

}
