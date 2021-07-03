package org.apache.ibatis.reflection.property;

import org.junit.Test;

public class PropertyTokenizerTest {

  @Test
  public void test() {
    PropertyTokenizer propertyTokenizer = new PropertyTokenizer("user.name.xx");
    while (propertyTokenizer != null && propertyTokenizer.hasNext()) {
      propertyTokenizer = propertyTokenizer.next();
    }
  }

  @Test
  public void test2(){
    new PropertyTokenizer("map[key].name");
//    new PropertyTokenizer("user[0].name");
  }

}
