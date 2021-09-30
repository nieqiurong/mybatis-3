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
package org.apache.ibatis.mapping;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.reflection.ParamNameUtil;
import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 */
public class ResultMap {
  /**
   * 全局配置对象
   */
  private Configuration configuration;
  /**
   * resultMapId
   */
  private String id;
  /**
   * 返回值类型(<resultMap id="detailedBlogResultMap" type="Blog">)
   */
  private Class<?> type;
  /**
   * 结果集映射
   * 含{@link #propertyResultMappings} + {@link #idResultMappings} + {@link #constructorResultMappings}
   */
  private List<ResultMapping> resultMappings;
  /**
   * 主键映射(<id property="id" column="post_id"/>)
   */
  private List<ResultMapping> idResultMappings;
  /**
   * 构造映射
   * <constructor>
   *    <idArg column="blog_id" javaType="int"/>
   * </constructor>
   */
  private List<ResultMapping> constructorResultMappings;
  /**
   * 属性映射(<result property="title" column="blog_title"/>)
   */
  private List<ResultMapping> propertyResultMappings;
  /**
   * 映射字段列表
   */
  private Set<String> mappedColumns;
  /**
   * 映射属性列表
   */
  private Set<String> mappedProperties;
  /**
   * 鉴别器
   * <discriminator javaType="int" column="draft">
   *    <case value="1" resultType="DraftPost"/>
   * </discriminator>
   */
  private Discriminator discriminator;
  /**
   * 是否嵌套查询
   */
  private boolean hasNestedResultMaps;
  private boolean hasNestedQueries;
  /**
   * 是否启用自动映射
   */
  private Boolean autoMapping;

  private ResultMap() {
  }

  public static class Builder {
    private static final Log log = LogFactory.getLog(Builder.class);

    private ResultMap resultMap = new ResultMap();

    public Builder(Configuration configuration, String id, Class<?> type, List<ResultMapping> resultMappings) {
      this(configuration, id, type, resultMappings, null);
    }

    public Builder(Configuration configuration, String id, Class<?> type, List<ResultMapping> resultMappings, Boolean autoMapping) {
      resultMap.configuration = configuration;
      resultMap.id = id;
      resultMap.type = type;
      resultMap.resultMappings = resultMappings;
      resultMap.autoMapping = autoMapping;
    }

    public Builder discriminator(Discriminator discriminator) {
      resultMap.discriminator = discriminator;
      return this;
    }

    public Class<?> type() {
      return resultMap.type;
    }

    public ResultMap build() {
      if (resultMap.id == null) {
        throw new IllegalArgumentException("ResultMaps must have an id");
      }
      resultMap.mappedColumns = new HashSet<>();
      resultMap.mappedProperties = new HashSet<>();
      //----分离resultMappings开始-------
      resultMap.idResultMappings = new ArrayList<>(); //主键标识
      resultMap.constructorResultMappings = new ArrayList<>();  //构造标志
      resultMap.propertyResultMappings = new ArrayList<>(); //属性映射
      //----分离resultMappings结束-------
      final List<String> constructorArgNames = new ArrayList<>();
      //遍历结果集映射分组
      for (ResultMapping resultMapping : resultMap.resultMappings) {
        resultMap.hasNestedQueries = resultMap.hasNestedQueries || resultMapping.getNestedQueryId() != null;  //嵌套select的情况
        resultMap.hasNestedResultMaps = resultMap.hasNestedResultMaps || (resultMapping.getNestedResultMapId() != null && resultMapping.getResultSet() == null);  //嵌套resultMap情况
        final String column = resultMapping.getColumn();
        if (column != null) {
          resultMap.mappedColumns.add(column.toUpperCase(Locale.ENGLISH));
        } else if (resultMapping.isCompositeResult()) { //TODO 这里好像有点迷啊
          /*
            在org.apache.ibatis.builder.MapperBuilderAssistant#buildResultMapping的时候,推测composites是根据column来的,如果column为空,那自然就没有composites了
            解析composites: org.apache.ibatis.builder.MapperBuilderAssistant.parseCompositeColumnName
           */
          for (ResultMapping compositeResultMapping : resultMapping.getComposites()) {
            final String compositeColumn = compositeResultMapping.getColumn();
            if (compositeColumn != null) {
              resultMap.mappedColumns.add(compositeColumn.toUpperCase(Locale.ENGLISH));
            }
          }
        }
        final String property = resultMapping.getProperty();
        if (property != null) { //属性节点
          resultMap.mappedProperties.add(property);
        }
        //----------根据标志位分组数据开始---------
        if (resultMapping.getFlags().contains(ResultFlag.CONSTRUCTOR)) {  //构造
          resultMap.constructorResultMappings.add(resultMapping);
          if (resultMapping.getProperty() != null) {  //记录构造参数名
            constructorArgNames.add(resultMapping.getProperty());
          }
        } else {
          resultMap.propertyResultMappings.add(resultMapping);
        }
        if (resultMapping.getFlags().contains(ResultFlag.ID)) { //主键
          resultMap.idResultMappings.add(resultMapping);
        }
      }
      if (resultMap.idResultMappings.isEmpty()) {
        resultMap.idResultMappings.addAll(resultMap.resultMappings);
      }
      //----------根据标志位分组数据结束---------
      if (!constructorArgNames.isEmpty()) { //处理构造参数
        final List<String> actualArgNames = argNamesOfMatchingConstructor(constructorArgNames);
        if (actualArgNames == null) {
          throw new BuilderException("Error in result map '" + resultMap.id
              + "'. Failed to find a constructor in '"
              + resultMap.getType().getName() + "' by arg names " + constructorArgNames
              + ". There might be more info in debug log.");
        }
        //重排序一次构造参数映射,排序成匹配的构造参数顺序
        resultMap.constructorResultMappings.sort((o1, o2) -> {
          int paramIdx1 = actualArgNames.indexOf(o1.getProperty());
          int paramIdx2 = actualArgNames.indexOf(o2.getProperty());
          return paramIdx1 - paramIdx2;
        });
      }
      // lock down collections
      resultMap.resultMappings = Collections.unmodifiableList(resultMap.resultMappings);
      resultMap.idResultMappings = Collections.unmodifiableList(resultMap.idResultMappings);
      resultMap.constructorResultMappings = Collections.unmodifiableList(resultMap.constructorResultMappings);
      resultMap.propertyResultMappings = Collections.unmodifiableList(resultMap.propertyResultMappings);
      resultMap.mappedColumns = Collections.unmodifiableSet(resultMap.mappedColumns);
      return resultMap;
    }

    /**
     * 匹配构造
     *
     * @param constructorArgNames 构造参数名称
     * @return 构造
     */
    private List<String> argNamesOfMatchingConstructor(List<String> constructorArgNames) {
      Constructor<?>[] constructors = resultMap.type.getDeclaredConstructors();
      for (Constructor<?> constructor : constructors) { //获取返回类构造方法列表
        Class<?>[] paramTypes = constructor.getParameterTypes();
        if (constructorArgNames.size() == paramTypes.length) {  //匹配长度等于当前标记的构造列表
          List<String> paramNames = getArgNames(constructor);
          if (constructorArgNames.containsAll(paramNames)  //参数名称相同且参数类型匹配上(顺序不一定一致)
            && argTypesMatch(constructorArgNames, paramTypes, paramNames)) {
            return paramNames;
          }
        }
      }
      return null;
    }

    /**
     * 构造参数类型匹配
     *
     * @param constructorArgNames 待匹配的参数名列表
     * @param paramTypes          构造参数类型
     * @param paramNames          构造参数名列表
     * @return 参数类型是否匹配
     */
    private boolean argTypesMatch(final List<String> constructorArgNames,
                                  Class<?>[] paramTypes, List<String> paramNames) {
      for (int i = 0; i < constructorArgNames.size(); i++) {
        Class<?> actualType = paramTypes[paramNames.indexOf(constructorArgNames.get(i))];   //查找当前指定的参数名所在真实参数位置的参数类型
        Class<?> specifiedType = resultMap.constructorResultMappings.get(i).getJavaType();  //获取手动指定的参数列表
        if (!actualType.equals(specifiedType)) {
          if (log.isDebugEnabled()) {
            log.debug("While building result map '" + resultMap.id
              + "', found a constructor with arg names " + constructorArgNames
              + ", but the type of '" + constructorArgNames.get(i)
              + "' did not match. Specified: [" + specifiedType.getName() + "] Declared: ["
              + actualType.getName() + "]");
          }
          return false;
        }
      }
      return true;
    }

    /**
     * 读取构造参数
     *
     * @param constructor 构造方法
     * @return 构造参数列表
     */
    private List<String> getArgNames(Constructor<?> constructor) {
      List<String> paramNames = new ArrayList<>();
      List<String> actualParamNames = null;
      final Annotation[][] paramAnnotations = constructor.getParameterAnnotations();
      int paramCount = paramAnnotations.length;
      for (int paramIndex = 0; paramIndex < paramCount; paramIndex++) {
        String name = null;
        for (Annotation annotation : paramAnnotations[paramIndex]) {
          if (annotation instanceof Param) {  //处理参数注解
            name = ((Param) annotation).value();
            break;
          }
        }
        if (name == null && resultMap.configuration.isUseActualParamName()) { //是否保留编译参数名
          if (actualParamNames == null) { //初始化编译构造参数名列表
            actualParamNames = ParamNameUtil.getParamNames(constructor);
          }
          if (actualParamNames.size() > paramIndex) {
            name = actualParamNames.get(paramIndex);  //取出下标所在的变量参数名.
          }
        }
        paramNames.add(name != null ? name : "arg" + paramIndex);
      }
      return paramNames;
    }
  }

  public String getId() {
    return id;
  }

  public boolean hasNestedResultMaps() {
    return hasNestedResultMaps;
  }

  public boolean hasNestedQueries() {
    return hasNestedQueries;
  }

  public Class<?> getType() {
    return type;
  }

  public List<ResultMapping> getResultMappings() {
    return resultMappings;
  }

  public List<ResultMapping> getConstructorResultMappings() {
    return constructorResultMappings;
  }

  public List<ResultMapping> getPropertyResultMappings() {
    return propertyResultMappings;
  }

  public List<ResultMapping> getIdResultMappings() {
    return idResultMappings;
  }

  public Set<String> getMappedColumns() {
    return mappedColumns;
  }

  public Set<String> getMappedProperties() {
    return mappedProperties;
  }

  public Discriminator getDiscriminator() {
    return discriminator;
  }

  public void forceNestedResultMaps() {
    hasNestedResultMaps = true;
  }

  public Boolean getAutoMapping() {
    return autoMapping;
  }

}
