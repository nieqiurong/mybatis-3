package org.apache.ibatis.reflection;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;


/**
 * @author nieqiurong 2020/7/21.
 */
@RunWith(MockitoJUnitRunner.class)
class ParamNameResolverTest {
  
  interface TestMapper {
    
    String test1(String name,int age);
  
    String test2(@Param(value = "name") String name, int age);
  
    String test3(@Param(value = "name") String name, @Param(value = "age") int age);
  }
  
  @Test
  void test1() throws NoSuchMethodException {
    Configuration configuration = Mockito.mock(Configuration.class);
    Mockito.when(configuration.isUseActualParamName()).thenReturn(true);
    new ParamNameResolver(configuration,TestMapper.class.getDeclaredMethod("test1",String.class,int.class));
  }
  
  @Test
  void test2() throws NoSuchMethodException {
    Configuration configuration = Mockito.mock(Configuration.class);
    Mockito.when(configuration.isUseActualParamName()).thenReturn(true);
    new ParamNameResolver(configuration,TestMapper.class.getDeclaredMethod("test2",String.class,int.class));
  }
  
  @Test
  void test3() throws NoSuchMethodException {
    Configuration configuration = Mockito.mock(Configuration.class);
    Mockito.when(configuration.isUseActualParamName()).thenReturn(true);
    new ParamNameResolver(configuration,TestMapper.class.getDeclaredMethod("test3",String.class,int.class));
  }
  
}
