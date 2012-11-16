package org.jboss.errai.ioc.async.test.scopes.dependent.client.res;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;

/**
 * @author Mike Brock
 */
@Singleton
public class BeanInjectsNonModuleDependentBean {
  @Inject ArrayList<String> list;

  @PostConstruct
  private void postConstructBean() {
    list.add("foo");
    list.add("bar");
  }

  public ArrayList<String> getList() {
    return list;
  }
}
