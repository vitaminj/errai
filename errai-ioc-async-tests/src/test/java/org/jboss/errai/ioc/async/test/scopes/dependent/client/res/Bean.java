/*
 * Copyright 2011 JBoss, by Red Hat, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.errai.ioc.async.test.scopes.dependent.client.res;

import javax.inject.Inject;
import javax.inject.Singleton;


/**
 * @author Mike Brock
 */
@Singleton
public class Bean {
  @Inject private ServiceA serviceA;
  @Inject private ServiceB serviceB;
  @Inject private ServiceC serviceC;

  public ServiceA getServiceA() {
    return serviceA;
  }

  public ServiceB getServiceB() {
    return serviceB;
  }

  public ServiceC getServiceC() {
    return serviceC;
  }
}
