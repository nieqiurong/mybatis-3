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
package org.apache.ibatis.cache.decorators;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

/**
 * The 2nd level cache transactional buffer.
 * <p>
 * This class holds all cache entries that are to be added to the 2nd level cache during a Session.
 * Entries are sent to the cache when commit is called or discarded if the Session is rolled back.
 * Blocking cache support has been added. Therefore any get() that returns a cache miss
 * will be followed by a put() so any lock associated with the key can be released.
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class TransactionalCache implements Cache {

  private static final Log log = LogFactory.getLog(TransactionalCache.class);

  /**
   * 缓存对象
   */
  private final Cache delegate;
  /**
   * 提交事务时清理缓存标志位
   */
  private boolean clearOnCommit;
  /**
   * 暂存未提交数据
   */
  private final Map<Object, Object> entriesToAddOnCommit;
  /**
   * 未命中缓存数据
   */
  private final Set<Object> entriesMissedInCache;

  public TransactionalCache(Cache delegate) {
    this.delegate = delegate;
    this.clearOnCommit = false;
    this.entriesToAddOnCommit = new HashMap<>();
    this.entriesMissedInCache = new HashSet<>();
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  @Override
  public Object getObject(Object key) {
    // issue #116
    Object object = delegate.getObject(key);
    if (object == null) {
      //添加缓存未命中的数据
      entriesMissedInCache.add(key);
    }
    // issue #146
    if (clearOnCommit) {
      //当触发过clear方法时，由于还未进行commit，当查询缓存获取到有数据时，如果返回的话，就是脏数据，所以这里会返回null
      return null;
    } else {
      return object;
    }
  }

  /**
   * 写入缓冲区
   *
   * @param key    {@link CacheKey}
   * @param object 值
   */
  @Override
  public void putObject(Object key, Object object) {
    entriesToAddOnCommit.put(key, object);
  }

  @Override
  public Object removeObject(Object key) {
    return null;
  }

  /**
   * 清理事务缓存
   * 当一次事务中，嵌套了更新或指定强制刷新缓存操作时，将会清理本地缓存数据
   */
  @Override
  public void clear() {
    clearOnCommit = true; //重置标志位
    entriesToAddOnCommit.clear(); //清空缓冲区
  }

  /**
   * 提交事务
   */
  public void commit() {
    if (clearOnCommit) {
      //当前命名空间发生过清理行为，提交事务时直接清理命名空间缓存数据
      delegate.clear();
    }
    //写入缓存
    flushPendingEntries();
    //重置事务缓存
    reset();
  }

  /**
   * 回滚事务
   */
  public void rollback() {
    //解锁
    unlockMissedEntries();
    //重置事务缓存
    reset();
  }

  /**
   * 当事务提交或回滚时重置事务缓存
   */
  private void reset() {
    clearOnCommit = false;  //清理标志位
    entriesToAddOnCommit.clear(); //清空缓存区数据
    entriesMissedInCache.clear(); //清空未命中缓存数据
  }

  /**
   * 将本地缓存区数据和未命中缓存数据写入二级缓存
   */
  private void flushPendingEntries() {
    //将缓冲区数据写入二级缓存
    for (Map.Entry<Object, Object> entry : entriesToAddOnCommit.entrySet()) {
      delegate.putObject(entry.getKey(), entry.getValue());
    }
    //将未命中二级缓存数据写入缓存
    for (Object entry : entriesMissedInCache) {
      if (!entriesToAddOnCommit.containsKey(entry)) {
        delegate.putObject(entry, null);
      }
    }
  }

  /**
   * 解锁未命中缓存数据
   *
   * @see BlockingCache#getObject(java.lang.Object)
   * @see BlockingCache#removeObject(java.lang.Object)
   */
  private void unlockMissedEntries() {
    for (Object entry : entriesMissedInCache) {
      try {
        delegate.removeObject(entry);
      } catch (Exception e) {
        log.warn("Unexpected exception while notifiying a rollback to the cache adapter. "
          + "Consider upgrading your cache adapter to the latest version. Cause: " + e);
      }
    }
  }

}
