package org.apache.ibatis.binding;

import org.apache.ibatis.scripting.xmltags.DynamicContext;
import org.apache.ibatis.scripting.xmltags.IfSqlNode;
import org.apache.ibatis.scripting.xmltags.MixedSqlNode;
import org.apache.ibatis.scripting.xmltags.SqlNode;
import org.apache.ibatis.scripting.xmltags.StaticTextSqlNode;
import org.apache.ibatis.session.Configuration;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;

public class SqlNodeTest {

  @Test
  public void mixedSqlNodeTest() {
    DynamicContext context = new DynamicContext(new Configuration(), null);
    ArrayList<SqlNode> sqlNodes = new ArrayList<>();
    sqlNodes.add(new StaticTextSqlNode("SELECT * FROM BLOG WHERE state = ‘ACTIVE’"));
    sqlNodes.add(new IfSqlNode(new MixedSqlNode(Collections.singletonList(new StaticTextSqlNode("AND title like #{title}"))), "true"));
    MixedSqlNode mixedSqlNode = new MixedSqlNode(sqlNodes);
    mixedSqlNode.apply(context);
    Assert.assertEquals("SELECT * FROM BLOG WHERE state = ‘ACTIVE’ AND title like #{title}", context.getSql());
  }

}
