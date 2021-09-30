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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.builder.CacheRefResolver;
import org.apache.ibatis.builder.IncompleteElementException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.builder.ResultMapResolver;
import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Discriminator;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.ResultFlag;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XMLMapperBuilder extends BaseBuilder {

  private final XPathParser parser;
  private final MapperBuilderAssistant builderAssistant;
  private final Map<String, XNode> sqlFragments;
  private final String resource;

  @Deprecated
  public XMLMapperBuilder(Reader reader, Configuration configuration, String resource, Map<String, XNode> sqlFragments, String namespace) {
    this(reader, configuration, resource, sqlFragments);
    this.builderAssistant.setCurrentNamespace(namespace);
  }

  @Deprecated
  public XMLMapperBuilder(Reader reader, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    this(new XPathParser(reader, true, configuration.getVariables(), new XMLMapperEntityResolver()),
        configuration, resource, sqlFragments);
  }

  public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments, String namespace) {
    this(inputStream, configuration, resource, sqlFragments);
    this.builderAssistant.setCurrentNamespace(namespace);
  }

  public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    this(new XPathParser(inputStream, true, configuration.getVariables(), new XMLMapperEntityResolver()),
        configuration, resource, sqlFragments);
  }

  private XMLMapperBuilder(XPathParser parser, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    super(configuration);
    this.builderAssistant = new MapperBuilderAssistant(configuration, resource);
    this.parser = parser;
    this.sqlFragments = sqlFragments;
    this.resource = resource;
  }

  public void parse() {
    //检查资源是否已经加载
    if (!configuration.isResourceLoaded(resource)) {
      configurationElement(parser.evalNode("/mapper"));
      configuration.addLoadedResource(resource);
      bindMapperForNamespace();
    }
    //这里就是用来处理解析失败的xml，好比如，解析mapper1.xml的时候，引用到了mapper2.xml，那就会解析失败
    //所以，每解析成功一次mapper.xml都来尝试修复这些解析未成功的数据
    parsePendingResultMaps();
    parsePendingCacheRefs();
    parsePendingStatements();
  }

  public XNode getSqlFragment(String refid) {
    return sqlFragments.get(refid);
  }

  /**
   * 解析配置元素
   * @param context <mapper></mapper>节点
   */
  private void configurationElement(XNode context) {
    try {
      String namespace = context.getStringAttribute("namespace");
      //检查命名空间是不是空了
      if (namespace == null || namespace.isEmpty()) {
        throw new BuilderException("Mapper's namespace cannot be empty");
      }
      builderAssistant.setCurrentNamespace(namespace);
      cacheRefElement(context.evalNode("cache-ref")); //解析缓存引用
      cacheElement(context.evalNode("cache"));  //解析缓存
      parameterMapElement(context.evalNodes("/mapper/parameterMap")); //解析附加参数
      resultMapElements(context.evalNodes("/mapper/resultMap"));  //解析resultMap
      sqlElement(context.evalNodes("/mapper/sql")); //解析sql节点
      buildStatementFromContext(context.evalNodes("select|insert|update|delete"));  //解析select，insert，update，delete节点
    } catch (Exception e) {
      throw new BuilderException("Error parsing Mapper XML. The XML location is '" + resource + "'. Cause: " + e, e);
    }
  }

  /**
   * 解析statement节点（insert|select|update|delete）
   *
   * @param list xml节点集合
   */
  private void buildStatementFromContext(List<XNode> list) {
    //当配置了数据库厂商时，加载指定厂商映射。
    if (configuration.getDatabaseId() != null) {
      buildStatementFromContext(list, configuration.getDatabaseId());
    }
    //可能会存在语句通用的情况下，节点里面就不配置databaseId了，需要加载一份未配置databaseId的数据。
    //当然，这里也跳过了当加载到了一个匹配databaseId的映射时，未配置databaseId的数据就不加载了。org.apache.ibatis.builder.xml.XMLStatementBuilder.databaseIdMatchesCurrent
    buildStatementFromContext(list, null);
  }

  /**
   * 解析statement节点
   *
   * @param list               xml节点集合
   * @param requiredDatabaseId 数据库厂商标识
   */
  private void buildStatementFromContext(List<XNode> list, String requiredDatabaseId) {
    for (XNode context : list) {
      final XMLStatementBuilder statementParser = new XMLStatementBuilder(configuration, builderAssistant, context, requiredDatabaseId);
      try {
        statementParser.parseStatementNode();
      } catch (IncompleteElementException e) {
        //解析加入解析失败的Statement
        configuration.addIncompleteStatement(statementParser);
      }
    }
  }

  /**
   * 解析失败的resultMap引用
   */
  private void parsePendingResultMaps() {
    Collection<ResultMapResolver> incompleteResultMaps = configuration.getIncompleteResultMaps();
    synchronized (incompleteResultMaps) {
      Iterator<ResultMapResolver> iter = incompleteResultMaps.iterator();
      while (iter.hasNext()) {
        try {
          //尝试再次解析resultMap引用
          iter.next().resolve();
          iter.remove();
        } catch (IncompleteElementException e) {
          // ResultMap is still missing a resource...
        }
      }
    }
  }

  /**
   * 解析失败的缓存引用
   */
  private void parsePendingCacheRefs() {
    Collection<CacheRefResolver> incompleteCacheRefs = configuration.getIncompleteCacheRefs();
    synchronized (incompleteCacheRefs) {
      //遍历所有失败的缓存引用列表
      Iterator<CacheRefResolver> iter = incompleteCacheRefs.iterator();
      while (iter.hasNext()) {
        try {
          //因为成功解析过一个新的mapper.xml了，再一次尝试解析
          iter.next().resolveCacheRef();
          //解析成功之后将当前解析失败的引用移除列表
          iter.remove();
        } catch (IncompleteElementException e) {
          // Cache ref is still missing a resource...
        }
      }
    }
  }

  private void parsePendingStatements() {
    Collection<XMLStatementBuilder> incompleteStatements = configuration.getIncompleteStatements();
    synchronized (incompleteStatements) {
      Iterator<XMLStatementBuilder> iter = incompleteStatements.iterator();
      while (iter.hasNext()) {
        try {
          iter.next().parseStatementNode();
          iter.remove();
        } catch (IncompleteElementException e) {
          // Statement is still missing a resource...
        }
      }
    }
  }

  /**
   * 解析缓存引用
   *
   * @param context <cache-ref namespace="xxx.xxx"/> 节点
   */
  private void cacheRefElement(XNode context) {
    if (context != null) {
      //写入引用缓存映射关系，但也没看到哪在用，可能是预留的吧。
      configuration.addCacheRef(builderAssistant.getCurrentNamespace(), context.getStringAttribute("namespace"));
      CacheRefResolver cacheRefResolver = new CacheRefResolver(builderAssistant, context.getStringAttribute("namespace"));
      try {
        //解析缓存引用
        cacheRefResolver.resolveCacheRef();
      } catch (IncompleteElementException e) {
        //因为可能存在被引用的命名空间还未解析，加入缓存引用失败列表
        configuration.addIncompleteCacheRef(cacheRefResolver);
      }
    }
  }

  /**
   * 解析cache节点
   * <cache type="com.domain.something.MyCustomCache"/>或<cache type="PERPETUAL"/>
   * @param context xml节点
   */
  private void cacheElement(XNode context) {
    if (context != null) {
      //因为在org.apache.ibatis.session.Configuration构造初始化的时候注册了别名，所以这里会通过typeAliasRegistry去查找
      String type = context.getStringAttribute("type", "PERPETUAL");
      Class<? extends Cache> typeClass = typeAliasRegistry.resolveAlias(type);
      String eviction = context.getStringAttribute("eviction", "LRU");
      Class<? extends Cache> evictionClass = typeAliasRegistry.resolveAlias(eviction);
      Long flushInterval = context.getLongAttribute("flushInterval");
      Integer size = context.getIntAttribute("size");
      //CacheBuilder里面是读写，正好相反，所以这里取反.
      boolean readWrite = !context.getBooleanAttribute("readOnly", false);
      boolean blocking = context.getBooleanAttribute("blocking", false);
      Properties props = context.getChildrenAsProperties();
      builderAssistant.useNewCache(typeClass, evictionClass, flushInterval, size, readWrite, blocking, props);
    }
  }

  @Deprecated
  private void parameterMapElement(List<XNode> list) {
    for (XNode parameterMapNode : list) {
      String id = parameterMapNode.getStringAttribute("id");
      String type = parameterMapNode.getStringAttribute("type");
      Class<?> parameterClass = resolveClass(type);
      List<XNode> parameterNodes = parameterMapNode.evalNodes("parameter");
      List<ParameterMapping> parameterMappings = new ArrayList<>();
      for (XNode parameterNode : parameterNodes) {
        String property = parameterNode.getStringAttribute("property");
        String javaType = parameterNode.getStringAttribute("javaType");
        String jdbcType = parameterNode.getStringAttribute("jdbcType");
        String resultMap = parameterNode.getStringAttribute("resultMap");
        String mode = parameterNode.getStringAttribute("mode");
        String typeHandler = parameterNode.getStringAttribute("typeHandler");
        Integer numericScale = parameterNode.getIntAttribute("numericScale");
        ParameterMode modeEnum = resolveParameterMode(mode);
        Class<?> javaTypeClass = resolveClass(javaType);
        JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
        Class<? extends TypeHandler<?>> typeHandlerClass = resolveClass(typeHandler);
        ParameterMapping parameterMapping = builderAssistant.buildParameterMapping(parameterClass, property, javaTypeClass, jdbcTypeEnum, resultMap, modeEnum, typeHandlerClass, numericScale);
        parameterMappings.add(parameterMapping);
      }
      builderAssistant.addParameterMap(id, parameterClass, parameterMappings);
    }
  }

  /**
   * 解析当前mapper.xml中的所有resultMap节点
   *
   * @param list <resultMap></resultMap>节点集合
   */
  private void resultMapElements(List<XNode> list) {
    for (XNode resultMapNode : list) {
      try {
        resultMapElement(resultMapNode);
      } catch (IncompleteElementException e) {
        // ignore, it will be retried
      }
    }
  }

  /**
   * 解析resultMap节点
   *
   * @param resultMapNode <resultMap></resultMap>节点
   * @return resultMap
   */
  private ResultMap resultMapElement(XNode resultMapNode) {
    return resultMapElement(resultMapNode, Collections.emptyList(), null);
  }

  /**
   * 解析resultMap节点
   *
   * @param resultMapNode            <resultMap></resultMap>节点
   * @param additionalResultMappings 附加参数
   * @param enclosingType 等同于返回值类型
   * @return resultMap
   */
  private ResultMap resultMapElement(XNode resultMapNode, List<ResultMapping> additionalResultMappings, Class<?> enclosingType) {
    ErrorContext.instance().activity("processing " + resultMapNode.getValueBasedIdentifier());
    //有可能是resultMap套了那些association或collection或discriminator
    //按简单最外层的resultMap来说，type不是为空的，所以能得到一个返回值类型
    /*
       这里解析就有几种情况了
       情况一:<resultMap id="blogResult" type="Blog">
              当前节点就是根节点，所以type属性是不会为空的.
       情况二:<association property="coAuthor"  resultMap="authorResult" columnPrefix="co_" />
              当前节点是<association>节点，那感兴趣的就是javaType属性了，不过都能推断出来，毕竟就是一个普通的属性字段.
       情况三:<collection property="posts" javaType="ArrayList" column="id" ofType="Post" select="selectPostsForBlog"/>
              当前节点是<collection>节点，那感兴趣的就是javaType或ofType节点了，javaType等同于List,ofType等同于就是泛型了.
       情况四:<discriminator javaType="int" column="draft">
              当前节点discriminator节点，那么感兴趣的就是javaType，因为javaType就是必填的了
       情况五:<case resultType="org.apache.ibatis.submitted.discriminator.Truck" value="2"> <result column="carrying_capacity" property="carryingCapacity"/></case>
              默认还是以原返回值类型为准，当case里面控制过返回值了，才会动态修改
     */
    String type = resultMapNode.getStringAttribute("type",  //resultMap中的type属性
      resultMapNode.getStringAttribute("ofType",  //collection中的ofType
        resultMapNode.getStringAttribute("resultType",  //case中的resultType
          resultMapNode.getStringAttribute("javaType"))));  //collection或association中的javaType
    //解析出返回值的class
    Class<?> typeClass = resolveClass(type);
    if (typeClass == null) {
      typeClass = inheritEnclosingType(resultMapNode, enclosingType);
    }
    Discriminator discriminator = null;
    List<ResultMapping> resultMappings = new ArrayList<>(additionalResultMappings);
    List<XNode> resultChildren = resultMapNode.getChildren();
    for (XNode resultChild : resultChildren) {
      if ("constructor".equals(resultChild.getName())) {
        processConstructorElement(resultChild, typeClass, resultMappings);
      } else if ("discriminator".equals(resultChild.getName())) {
        discriminator = processDiscriminatorElement(resultChild, typeClass, resultMappings);
      } else {
        List<ResultFlag> flags = new ArrayList<>();
        //解析<id property="id" column="id" />节点
        if ("id".equals(resultChild.getName())) {
          flags.add(ResultFlag.ID);
        }
        //添加返回值映射
        resultMappings.add(buildResultMappingFromContext(resultChild, typeClass, flags));
      }
    }
    String id = resultMapNode.getStringAttribute("id",
            resultMapNode.getValueBasedIdentifier());
    String extend = resultMapNode.getStringAttribute("extends");
    Boolean autoMapping = resultMapNode.getBooleanAttribute("autoMapping");
    ResultMapResolver resultMapResolver = new ResultMapResolver(builderAssistant, id, typeClass, extend, discriminator, resultMappings, autoMapping);
    try {
      return resultMapResolver.resolve();
    } catch (IncompleteElementException e) {
      configuration.addIncompleteResultMap(resultMapResolver);
      throw e;
    }
  }

  /**
   * 获取继承的类型
   *
   * @param resultMapNode xml节点
   * @param enclosingType 返回值类型
   * @return 推测属性类型
   */
  protected Class<?> inheritEnclosingType(XNode resultMapNode, Class<?> enclosingType) {
    //一对一的情况,如果没配置resultMap返回的话，就要尝试解析出目标类型
    if ("association".equals(resultMapNode.getName()) && resultMapNode.getStringAttribute("resultMap") == null) {
      //<association property="coAuthor"  resultMap="authorResult" columnPrefix="co_" />
      //property属性为必填属性，enclosingType就是当前实体类
      String property = resultMapNode.getStringAttribute("property");
      if (property != null && enclosingType != null) {
        MetaClass metaResultType = MetaClass.forClass(enclosingType, configuration.getReflectorFactory());
        //获取目标类型
        return metaResultType.getSetterType(property);
      }
    } else if ("case".equals(resultMapNode.getName()) && resultMapNode.getStringAttribute("resultMap") == null) {
      /*
      case未配置resultMap的情况，那就是上层类型
          <discriminator javaType="int" column="draft">
             <case value="1" resultType="DraftPost"/>
          </discriminator>
       */
      return enclosingType;
    }
    return null;
  }

  /**
   * 处理构造节点
   * <constructor>
   * <idArg column="id" javaType="int"/>
   * <arg column="username" javaType="String"/>
   * <arg column="age" javaType="_int"/>
   * </constructor>
   *
   * @param resultChild    <constructor>节点
   * @param resultType     返回值类型
   * @param resultMappings 结果集映射
   */
  private void processConstructorElement(XNode resultChild, Class<?> resultType, List<ResultMapping> resultMappings) {
    List<XNode> argChildren = resultChild.getChildren();
    for (XNode argChild : argChildren) {
      List<ResultFlag> flags = new ArrayList<>();
      flags.add(ResultFlag.CONSTRUCTOR);
      if ("idArg".equals(argChild.getName())) {
        flags.add(ResultFlag.ID);
      }
      resultMappings.add(buildResultMappingFromContext(argChild, resultType, flags));
    }
  }

  /**
   * 解析discriminator节点
   * <discriminator column="vehicle_type" javaType="int">
   *     <case resultType="org.apache.ibatis.submitted.discriminator.Car" value="1">
   *         <result column="door_count" property="doorCount"/>
   *     </case>
   *     <case resultType="org.apache.ibatis.submitted.discriminator.Truck" value="2">
   *         <result column="carrying_capacity" property="carryingCapacity"/>
   *     </case>
   * </discriminator>
   *
   * @param context        <discriminator/>节点
   * @param resultType     返回值类型（等同于当前属性所在类）
   * @param resultMappings 结果集映射
   * @return Discriminator
   */
  private Discriminator processDiscriminatorElement(XNode context, Class<?> resultType, List<ResultMapping> resultMappings) {
    String column = context.getStringAttribute("column");
    String javaType = context.getStringAttribute("javaType");
    String jdbcType = context.getStringAttribute("jdbcType");
    String typeHandler = context.getStringAttribute("typeHandler");
    Class<?> javaTypeClass = resolveClass(javaType);
    Class<? extends TypeHandler<?>> typeHandlerClass = resolveClass(typeHandler);
    JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
    Map<String, String> discriminatorMap = new HashMap<>();
    for (XNode caseChild : context.getChildren()) {
      String value = caseChild.getStringAttribute("value");
      //如果未配置resultMap，解析嵌套的resultMap
      String resultMap = caseChild.getStringAttribute("resultMap", processNestedResultMappings(caseChild, resultMappings, resultType));
      discriminatorMap.put(value, resultMap);
    }
    return builderAssistant.buildDiscriminator(resultType, column, javaTypeClass, jdbcTypeEnum, typeHandlerClass, discriminatorMap);
  }

  private void sqlElement(List<XNode> list) {
    if (configuration.getDatabaseId() != null) {
      sqlElement(list, configuration.getDatabaseId());
    }
    sqlElement(list, null);
  }

  private void sqlElement(List<XNode> list, String requiredDatabaseId) {
    for (XNode context : list) {
      String databaseId = context.getStringAttribute("databaseId");
      String id = context.getStringAttribute("id");
      id = builderAssistant.applyCurrentNamespace(id, false);
      if (databaseIdMatchesCurrent(id, databaseId, requiredDatabaseId)) {
        sqlFragments.put(id, context);
      }
    }
  }

  private boolean databaseIdMatchesCurrent(String id, String databaseId, String requiredDatabaseId) {
    if (requiredDatabaseId != null) {
      return requiredDatabaseId.equals(databaseId);
    }
    if (databaseId != null) {
      return false;
    }
    if (!this.sqlFragments.containsKey(id)) {
      return true;
    }
    // skip this fragment if there is a previous one with a not null databaseId
    XNode context = this.sqlFragments.get(id);
    return context.getStringAttribute("databaseId") == null;
  }

  /**
   * 构建返回值映射
   *
   * @param context    节点
   * @param resultType 返回值
   * @param flags      标志位
   * @return 结果集映射
   */
  private ResultMapping buildResultMappingFromContext(XNode context, Class<?> resultType, List<ResultFlag> flags) {
    String property;
    if (flags.contains(ResultFlag.CONSTRUCTOR)) {   //构造参数的时候,参数声明时在name属性上,如果没有则靠推测. <idArg column="id" javaType="int" name="id" /> <arg column="age" javaType="_int" name="age" />
      property = context.getStringAttribute("name");
    } else {
      //普通属性或注解属性是取property属性,<result property="username" column="author_username"/> <id property="id" column="author_id"/>
      property = context.getStringAttribute("property");
    }
    String column = context.getStringAttribute("column");
    String javaType = context.getStringAttribute("javaType");
    String jdbcType = context.getStringAttribute("jdbcType");
    String nestedSelect = context.getStringAttribute("select");
    //可能嵌套resultMap
    String nestedResultMap = context.getStringAttribute("resultMap", () ->
        processNestedResultMappings(context, Collections.emptyList(), resultType));
    String notNullColumn = context.getStringAttribute("notNullColumn");
    String columnPrefix = context.getStringAttribute("columnPrefix");
    String typeHandler = context.getStringAttribute("typeHandler");
    String resultSet = context.getStringAttribute("resultSet");
    String foreignColumn = context.getStringAttribute("foreignColumn");
    boolean lazy = "lazy".equals(context.getStringAttribute("fetchType", configuration.isLazyLoadingEnabled() ? "lazy" : "eager"));
    Class<?> javaTypeClass = resolveClass(javaType);
    Class<? extends TypeHandler<?>> typeHandlerClass = resolveClass(typeHandler);
    JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
    return builderAssistant.buildResultMapping(resultType, property, column, javaTypeClass, jdbcTypeEnum, nestedSelect, nestedResultMap, notNullColumn, columnPrefix, typeHandlerClass, flags, resultSet, foreignColumn, lazy);
  }

  /**
   * 嵌套结果集解析
   * <resultMap type="org.apache.ibatis.submitted.ancestor_ref.User"
   * id="userMapAssociation">
   * <id property="id" column="id" />
   * <result property="name" column="name" />
   * <association property="friend" resultMap="userMapAssociation"
   * columnPrefix="friend_" />
   * </resultMap>
   *
   * @param context        association或collection或case节点
   * @param resultMappings 返回值映射
   * @param enclosingType  返回值类型
   * @return resultMapId
   */
  private String processNestedResultMappings(XNode context, List<ResultMapping> resultMappings, Class<?> enclosingType) {
    if (Arrays.asList("association", "collection", "case").contains(context.getName())
      && context.getStringAttribute("select") == null) {
      validateCollection(context, enclosingType);
      ResultMap resultMap = resultMapElement(context, resultMappings, enclosingType);
      return resultMap.getId();
    }
    return null;
  }

  /**
   * 验证集合数据节点
   *
   * @param context       节点内容
   * @param enclosingType 返回值class
   */
  protected void validateCollection(XNode context, Class<?> enclosingType) {
    //当collection未配置javaType或resultMap，判断是否有set方法。
    if ("collection".equals(context.getName()) && context.getStringAttribute("resultMap") == null
        && context.getStringAttribute("javaType") == null) {
      MetaClass metaResultType = MetaClass.forClass(enclosingType, configuration.getReflectorFactory());
      String property = context.getStringAttribute("property");
      if (!metaResultType.hasSetter(property)) {
        throw new BuilderException(
            "Ambiguous collection type for property '" + property + "'. You must specify 'javaType' or 'resultMap'.");
      }
    }
  }

  /**
   * 按命名空间绑定mapper
   * <mapper namespace="org.apache.ibatis.submitted.discriminator.Mapper">
   */
  private void bindMapperForNamespace() {
    String namespace = builderAssistant.getCurrentNamespace();
    if (namespace != null) {
      Class<?> boundType = null;
      try {
        //加载xml中配置的namespace
        boundType = Resources.classForName(namespace);
      } catch (ClassNotFoundException e) {
        // ignore, bound type is not required
        // 这里可能有种情况，就是只想声明个空间，而不想写mapper.class的情况，就简单声明了一个命名空间，然后别的xml就用当前的命名空间调用本xml里面的方法。
      }
      if (boundType != null && !configuration.hasMapper(boundType)) {
        // Spring may not know the real resource name so we set a flag
        // to prevent loading again this resource from the mapper interface
        // look at MapperAnnotationBuilder#loadXmlResource
        //标记当前命名空间已经加载完成.
        configuration.addLoadedResource("namespace:" + namespace);
        //注册mapper
        configuration.addMapper(boundType);
      }
    }
  }

}
