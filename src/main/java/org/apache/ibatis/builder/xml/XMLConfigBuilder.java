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
package org.apache.ibatis.builder.xml;

import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.AutoMappingBehavior;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.JdbcType;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XMLConfigBuilder extends BaseBuilder {
  
  /**
   * 解析标志位（防止多次调用解析方法）
   */
  private boolean parsed;
  /**
   * xpath解析器
   */
  private final XPathParser parser;
  /**
   * 环境ID
   */
  private String environment;
  /**
   * 反射工厂
   */
  private final ReflectorFactory localReflectorFactory = new DefaultReflectorFactory();

  public XMLConfigBuilder(Reader reader) {
    this(reader, null, null);
  }

  public XMLConfigBuilder(Reader reader, String environment) {
    this(reader, environment, null);
  }

  public XMLConfigBuilder(Reader reader, String environment, Properties props) {
    this(new XPathParser(reader, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  public XMLConfigBuilder(InputStream inputStream) {
    this(inputStream, null, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment) {
    this(inputStream, environment, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment, Properties props) {
    this(new XPathParser(inputStream, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  private XMLConfigBuilder(XPathParser parser, String environment, Properties props) {
    super(new Configuration());
    ErrorContext.instance().resource("SQL Mapper Configuration");
    this.configuration.setVariables(props);
    this.parsed = false;
    this.environment = environment;
    this.parser = parser;
  }

  public Configuration parse() {
    if (parsed) {
      //如果多次调用解析就抛出异常
      throw new BuilderException("Each XMLConfigBuilder can only be used once.");
    }
    parsed = true;  //切换解析标志位开始解析configuration节点
    parseConfiguration(parser.evalNode("/configuration"));
    return configuration;
  }
  
  /**
   * 解析configuration配置
   *
   * @param root <configuration></configuration>节点
   */
  private void parseConfiguration(XNode root) {
    try {
      // issue #117 read properties first
      propertiesElement(root.evalNode("properties"));
      Properties settings = settingsAsProperties(root.evalNode("settings"));
      loadCustomVfs(settings);
      loadCustomLogImpl(settings);
      typeAliasesElement(root.evalNode("typeAliases"));
      pluginElement(root.evalNode("plugins"));
      objectFactoryElement(root.evalNode("objectFactory"));
      objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
      reflectorFactoryElement(root.evalNode("reflectorFactory"));
      settingsElement(settings);
      // read it after objectFactory and objectWrapperFactory issue #631
      environmentsElement(root.evalNode("environments"));
      databaseIdProviderElement(root.evalNode("databaseIdProvider"));
      typeHandlerElement(root.evalNode("typeHandlers"));
      mapperElement(root.evalNode("mappers"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
    }
  }
  
  /**
   * 解析settings节点，转换成Properties
   * <settings>
   *   <setting name="cacheEnabled" value="true"/>
   *   <setting name="lazyLoadingEnabled" value="true"/>
   * </settings>
   * @param context <settings></settings> 节点
   * @return 属性配置
   */
  private Properties settingsAsProperties(XNode context) {
    if (context == null) {
      return new Properties();
    }
    Properties props = context.getChildrenAsProperties(); //读取到所有子节点<setting></setting>
    // Check that all settings are known to the configuration class
    MetaClass metaConfig = MetaClass.forClass(Configuration.class, localReflectorFactory);  //获取Configuration元数据信息
    for (Object key : props.keySet()) {
      //遍历所有setting节点，判断是否有对应的set方法，也就是检查配置的setting中毒配置必须是Configuration中的属性
      if (!metaConfig.hasSetter(String.valueOf(key))) { //key就是setting中的name属性
        throw new BuilderException("The setting " + key + " is not known.  Make sure you spelled it correctly (case sensitive).");
      }
    }
    return props;
  }
  
  /**
   * 加载自定义VFS
   *
   * @param props 配置属性
   * @throws ClassNotFoundException exception
   */
  private void loadCustomVfs(Properties props) throws ClassNotFoundException {
    String value = props.getProperty("vfsImpl");
    if (value != null) {
      String[] clazzes = value.split(",");
      for (String clazz : clazzes) {
        if (!clazz.isEmpty()) {
          @SuppressWarnings("unchecked")
          Class<? extends VFS> vfsImpl = (Class<? extends VFS>)Resources.classForName(clazz);
          configuration.setVfsImpl(vfsImpl);
        }
      }
    }
  }
  
  /**
   * 加载日志实现类
   *
   * @param props 配置信息
   */
  private void loadCustomLogImpl(Properties props) {
    Class<? extends Log> logImpl = resolveClass(props.getProperty("logImpl"));
    configuration.setLogImpl(logImpl);
  }
  
  /**
   * 解析typeAliases节点
   * <typeAliases>
   *   <typeAlias alias="Author" type="domain.blog.Author"/>
   *   <package name="domain.blog"/>
   * </typeAliases>
   * @param parent <typeAliases></typeAliases>节点
   */
  private void typeAliasesElement(XNode parent) {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        if ("package".equals(child.getName())) {  //按包注册
          String typeAliasPackage = child.getStringAttribute("name");
          configuration.getTypeAliasRegistry().registerAliases(typeAliasPackage);
        } else {  //按类注册
          String alias = child.getStringAttribute("alias");
          String type = child.getStringAttribute("type");
          try {
            Class<?> clazz = Resources.classForName(type);
            if (alias == null) {
              typeAliasRegistry.registerAlias(clazz);
            } else {
              typeAliasRegistry.registerAlias(alias, clazz);
            }
          } catch (ClassNotFoundException e) {
            throw new BuilderException("Error registering typeAlias for '" + alias + "'. Cause: " + e, e);
          }
        }
      }
    }
  }

  /**
   * 解析插件节点
   *
   * @param parent plugins节点
   * @throws Exception exception
   */
  private void pluginElement(XNode parent) throws Exception {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        String interceptor = child.getStringAttribute("interceptor"); //获取插件节点
        Properties properties = child.getChildrenAsProperties();//解析插件属性
        Interceptor interceptorInstance = (Interceptor) resolveClass(interceptor).getDeclaredConstructor().newInstance(); //生成插件实例对象
        interceptorInstance.setProperties(properties); //写入插件属性
        configuration.addInterceptor(interceptorInstance);
      }
    }
  }
  
  /**
   * 解析objectFactory节点
   * <objectFactory type="org.mybatis.example.ExampleObjectFactory">
   *   <property name="someProperty" value="100"/>
   * </objectFactory>
   * @param context <objectFactory></objectFactory> 节点
   * @throws Exception exception
   */
  private void objectFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties properties = context.getChildrenAsProperties();
      ObjectFactory factory = (ObjectFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      factory.setProperties(properties);
      configuration.setObjectFactory(factory);
    }
  }
  
  /**
   * 解析objectWrapperFactory节点
   * <objectWrapperFactory type="org.apache.ibatis.reflection.wrapper.DefaultObjectWrapperFactory"/>
   * @param context <objectWrapperFactory></objectWrapperFactory>节点
   * @throws Exception
   */
  private void objectWrapperFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      ObjectWrapperFactory factory = (ObjectWrapperFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      configuration.setObjectWrapperFactory(factory);
    }
  }
  
  /**
   * 解析reflectorFactory节点
   * <reflectorFactory type="org.apache.ibatis.reflection.DefaultReflectorFactory"/>
   * @param context <reflectorFactory></reflectorFactory>节点
   * @throws Exception exception
   */
  private void reflectorFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      ReflectorFactory factory = (ReflectorFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      configuration.setReflectorFactory(factory);
    }
  }
  
  /**
   * 解析properties节点
   * @param context <properties></properties>节点
   *  <properties resource=""> or  <properties url="">
   * <properties resource="org/mybatis/example/config.properties">
   *   <property name="username" value="dev_user"/>
   *   <property name="password" value="F2Fa3!33TYyg"/>
   * </properties>
   * @throws Exception exception
   */
  private void propertiesElement(XNode context) throws Exception {
    if (context != null) {
      Properties defaults = context.getChildrenAsProperties();   //优先解析property子节点
      String resource = context.getStringAttribute("resource"); //获取相对类路径配置文件
      String url = context.getStringAttribute("url"); //资源定位符
      if (resource != null && url != null) {  //只能二选一
        throw new BuilderException("The properties element cannot specify both a URL and a resource based property file reference.  Please specify one or the other.");
      }
      //外部配置覆盖默认属性配置
      if (resource != null) {
        defaults.putAll(Resources.getResourceAsProperties(resource));
      } else if (url != null) {
        defaults.putAll(Resources.getUrlAsProperties(url));
      }
      Properties vars = configuration.getVariables();
      if (vars != null) {
        //configuration配置信息优先级要高，所以需要用configuration中的来覆盖掉当前的
        defaults.putAll(vars);
      }
      parser.setVariables(defaults);  //将当前配置信息传递给xpath解析器
      configuration.setVariables(defaults); //覆盖完成，重新设置回去属性配置
    }
  }
  
  /**
   * 指定configuration属性
   *
   * @param props 配置信息
   */
  private void settingsElement(Properties props) {
    configuration.setAutoMappingBehavior(AutoMappingBehavior.valueOf(props.getProperty("autoMappingBehavior", "PARTIAL")));
    configuration.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.valueOf(props.getProperty("autoMappingUnknownColumnBehavior", "NONE")));
    configuration.setCacheEnabled(booleanValueOf(props.getProperty("cacheEnabled"), true));
    configuration.setProxyFactory((ProxyFactory) createInstance(props.getProperty("proxyFactory")));
    configuration.setLazyLoadingEnabled(booleanValueOf(props.getProperty("lazyLoadingEnabled"), false));
    configuration.setAggressiveLazyLoading(booleanValueOf(props.getProperty("aggressiveLazyLoading"), false));
    configuration.setMultipleResultSetsEnabled(booleanValueOf(props.getProperty("multipleResultSetsEnabled"), true));
    configuration.setUseColumnLabel(booleanValueOf(props.getProperty("useColumnLabel"), true));
    configuration.setUseGeneratedKeys(booleanValueOf(props.getProperty("useGeneratedKeys"), false));
    configuration.setDefaultExecutorType(ExecutorType.valueOf(props.getProperty("defaultExecutorType", "SIMPLE")));
    configuration.setDefaultStatementTimeout(integerValueOf(props.getProperty("defaultStatementTimeout"), null));
    configuration.setDefaultFetchSize(integerValueOf(props.getProperty("defaultFetchSize"), null));
    configuration.setDefaultResultSetType(resolveResultSetType(props.getProperty("defaultResultSetType")));
    configuration.setMapUnderscoreToCamelCase(booleanValueOf(props.getProperty("mapUnderscoreToCamelCase"), false));
    configuration.setSafeRowBoundsEnabled(booleanValueOf(props.getProperty("safeRowBoundsEnabled"), false));
    configuration.setLocalCacheScope(LocalCacheScope.valueOf(props.getProperty("localCacheScope", "SESSION")));
    configuration.setJdbcTypeForNull(JdbcType.valueOf(props.getProperty("jdbcTypeForNull", "OTHER")));
    configuration.setLazyLoadTriggerMethods(stringSetValueOf(props.getProperty("lazyLoadTriggerMethods"), "equals,clone,hashCode,toString"));
    configuration.setSafeResultHandlerEnabled(booleanValueOf(props.getProperty("safeResultHandlerEnabled"), true));
    configuration.setDefaultScriptingLanguage(resolveClass(props.getProperty("defaultScriptingLanguage")));
    configuration.setDefaultEnumTypeHandler(resolveClass(props.getProperty("defaultEnumTypeHandler")));
    configuration.setCallSettersOnNulls(booleanValueOf(props.getProperty("callSettersOnNulls"), false));
    configuration.setUseActualParamName(booleanValueOf(props.getProperty("useActualParamName"), true));
    configuration.setReturnInstanceForEmptyRow(booleanValueOf(props.getProperty("returnInstanceForEmptyRow"), false));
    configuration.setLogPrefix(props.getProperty("logPrefix"));
    configuration.setConfigurationFactory(resolveClass(props.getProperty("configurationFactory")));
    configuration.setShrinkWhitespacesInSql(booleanValueOf(props.getProperty("shrinkWhitespacesInSql"), false));
  }
  
  /**
   * 解析环境变量<environments></environments>
   *
   * @param context environments节点
   * @throws Exception exception
   */
  private void environmentsElement(XNode context) throws Exception {
    if (context != null) {
      if (environment == null) {
        //未指定环境配置的话，读取environments中的默认环境ID
        environment = context.getStringAttribute("default");
      }
      for (XNode child : context.getChildren()) {
        //解析子节点<environment></environment>
        String id = child.getStringAttribute("id");
        //判断当前的环境是否匹配当前节点
        if (isSpecifiedEnvironment(id)) {
          TransactionFactory txFactory = transactionManagerElement(child.evalNode("transactionManager")); //解析出事务工厂
          DataSourceFactory dsFactory = dataSourceElement(child.evalNode("dataSource"));  //解析出数据源信息
          DataSource dataSource = dsFactory.getDataSource();
          //环境构建者
          Environment.Builder environmentBuilder = new Environment.Builder(id)
            .transactionFactory(txFactory)
            .dataSource(dataSource);
          //设置环境信息
          configuration.setEnvironment(environmentBuilder.build());
        }
      }
    }
  }
  
  /**
   * 解析databaseIdProvider节点
   * <databaseIdProvider type="DB_VENDOR">
   *   <property name="SQL Server" value="sqlserver"/>
   *   <property name="DB2" value="db2"/>
   *   <property name="Oracle" value="oracle" />
   * </databaseIdProvider>
   * @param context <databaseIdProvider></databaseIdProvider>节点
   * @throws Exception exception
   */
  private void databaseIdProviderElement(XNode context) throws Exception {
    DatabaseIdProvider databaseIdProvider = null;
    if (context != null) {
      String type = context.getStringAttribute("type");
      // awful patch to keep backward compatibility
      if ("VENDOR".equals(type)) {
        type = "DB_VENDOR";
      }
      Properties properties = context.getChildrenAsProperties();
      databaseIdProvider = (DatabaseIdProvider) resolveClass(type).getDeclaredConstructor().newInstance();
      databaseIdProvider.setProperties(properties);
    }
    Environment environment = configuration.getEnvironment();
    if (environment != null && databaseIdProvider != null) {
      String databaseId = databaseIdProvider.getDatabaseId(environment.getDataSource());
      configuration.setDatabaseId(databaseId);
    }
  }
  
  /**
   * 解析事务管理工厂
   *            <transactionManager type="JDBC">
   *                 <property name="..." value="..."/>
   *             </transactionManager>
   * @param context <transactionManager></transactionManager> 节点
   * @return 事务工厂实例
   * @throws Exception exception
   */
  private TransactionFactory transactionManagerElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type"); //事务管理器，默认注册了JDBC，MANAGED两个别名实例 org.apache.ibatis.session.Configuration.Configuration()
      Properties props = context.getChildrenAsProperties();   //属性配置
      TransactionFactory factory = (TransactionFactory) resolveClass(type).getDeclaredConstructor().newInstance();  //创建实例
      factory.setProperties(props); //指定属性配置
      return factory;
    }
    throw new BuilderException("Environment declaration requires a TransactionFactory.");
  }
  
  /**
   * 解析数据源信息
   * @param context <dataSource></dataSource> 节点
   *     <dataSource type="POOLED">
   *       <property name="driver" value="${driver}"/>
   *       <property name="url" value="${url}"/>
   *       <property name="username" value="${username}"/>
   *       <property name="password" value="${password}"/>
   *     </dataSource>
   * @return 数据源工厂实例
   * @throws Exception exception
   */
  private DataSourceFactory dataSourceElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type"); //数据源工厂，默认注册了JNDI，POOLED，UNPOOLED三个别名实例 org.apache.ibatis.session.Configuration.Configuration()
      Properties props = context.getChildrenAsProperties(); //读取属性配置
      DataSourceFactory factory = (DataSourceFactory) resolveClass(type).getDeclaredConstructor().newInstance(); //创建实例
      factory.setProperties(props); //指定属性配置
      return factory;
    }
    throw new BuilderException("Environment declaration requires a DataSourceFactory.");
  }

  private void typeHandlerElement(XNode parent) {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        if ("package".equals(child.getName())) {
          String typeHandlerPackage = child.getStringAttribute("name");
          typeHandlerRegistry.register(typeHandlerPackage);
        } else {
          String javaTypeName = child.getStringAttribute("javaType");
          String jdbcTypeName = child.getStringAttribute("jdbcType");
          String handlerTypeName = child.getStringAttribute("handler");
          Class<?> javaTypeClass = resolveClass(javaTypeName);
          JdbcType jdbcType = resolveJdbcType(jdbcTypeName);
          Class<?> typeHandlerClass = resolveClass(handlerTypeName);
          if (javaTypeClass != null) {
            if (jdbcType == null) {
              typeHandlerRegistry.register(javaTypeClass, typeHandlerClass);
            } else {
              typeHandlerRegistry.register(javaTypeClass, jdbcType, typeHandlerClass);
            }
          } else {
            typeHandlerRegistry.register(typeHandlerClass);
          }
        }
      }
    }
  }

  /**
   * 解析<mappers></mappers> 节点
   *
   * @param parent mappers节点
   * @throws Exception exception
   */
  private void mapperElement(XNode parent) throws Exception {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        if ("package".equals(child.getName())) {
          //处理按包注册的 <package name="org.mybatis.builder"/>
          String mapperPackage = child.getStringAttribute("name");
          configuration.addMappers(mapperPackage);
        } else {
          String resource = child.getStringAttribute("resource");
          String url = child.getStringAttribute("url");
          String mapperClass = child.getStringAttribute("class");
          if (resource != null && url == null && mapperClass == null) {
            //<mapper resource="org/mybatis/builder/AuthorMapper.xml"/> 按相对类路径进行注册的
            ErrorContext.instance().resource(resource);
            InputStream inputStream = Resources.getResourceAsStream(resource);
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments());
            mapperParser.parse();
          } else if (resource == null && url != null && mapperClass == null) {
            //<mapper url="file:///var/mappers/AuthorMapper.xml"/> 按资源定位符进行注册的
            ErrorContext.instance().resource(url);
            InputStream inputStream = Resources.getUrlAsStream(url);
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url, configuration.getSqlFragments());
            mapperParser.parse();
          } else if (resource == null && url == null && mapperClass != null) {
            //<mapper class="org.mybatis.builder.AuthorMapper"/> 按class进行注册的
            Class<?> mapperInterface = Resources.classForName(mapperClass);
            configuration.addMapper(mapperInterface);
          } else {
            throw new BuilderException("A mapper element may only specify a url, resource or class, but not more than one.");
          }
        }
      }
    }
  }
  
  /**
   * 判断是否匹配当前环境
   *
   * @param id 环境id
   * @return 是否匹配
   */
  private boolean isSpecifiedEnvironment(String id) {
    if (environment == null) {
      throw new BuilderException("No environment specified.");
    } else if (id == null) {
      throw new BuilderException("Environment requires an id attribute.");
    } else if (environment.equals(id)) {
      return true;
    }
    return false;
  }

}
