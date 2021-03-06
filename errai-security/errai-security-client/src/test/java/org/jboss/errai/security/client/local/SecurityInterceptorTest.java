package org.jboss.errai.security.client.local;

import static org.jboss.errai.bus.client.api.base.MessageBuilder.createCall;
import junit.framework.AssertionFailedError;

import org.jboss.errai.bus.client.ErraiBus;
import org.jboss.errai.bus.client.api.BusErrorCallback;
import org.jboss.errai.bus.client.api.base.MessageBuilder;
import org.jboss.errai.bus.client.api.messaging.Message;
import org.jboss.errai.bus.client.framework.ClientMessageBusImpl;
import org.jboss.errai.common.client.api.RemoteCallback;
import org.jboss.errai.common.client.api.extension.InitVotes;
import org.jboss.errai.enterprise.client.cdi.AbstractErraiCDITest;
import org.jboss.errai.enterprise.client.cdi.api.CDI;
import org.jboss.errai.ioc.client.container.IOC;
import org.jboss.errai.security.client.local.identity.ActiveUserProvider;
import org.jboss.errai.security.client.shared.AdminService;
import org.jboss.errai.security.client.shared.AuthenticatedService;
import org.jboss.errai.security.client.shared.DiverseService;
import org.jboss.errai.security.shared.AuthenticationService;
import org.jboss.errai.security.shared.User;
import org.jboss.errai.security.shared.exception.SecurityException;
import org.jboss.errai.security.shared.exception.UnauthenticatedException;
import org.jboss.errai.security.shared.exception.UnauthorizedException;
import org.jboss.errai.ui.nav.client.local.DefaultPage;
import org.jboss.errai.ui.nav.client.local.Navigation;
import org.junit.Test;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.GWT.UncaughtExceptionHandler;
import com.google.gwt.user.client.Timer;

public class SecurityInterceptorTest extends AbstractErraiCDITest {

  public static long TIME_LIMIT = 60000;

  private static class Counter {
    private int count = 0;

    public void increment() {
      count += 1;
    }

    public int getCount() {
      return count;
    }
  }

  private class ErrorCountingCallback extends BusErrorCallback {
    private final Counter counter;
    private final Class<? extends SecurityException> throwType;

    public ErrorCountingCallback(final Counter counter, final Class<? extends SecurityException> throwType) {
      this.counter = counter;
      this.throwType = throwType;
    }

    @Override
    public boolean error(Message message, Throwable throwable) {
      if (throwable.getClass().equals(throwType)) {
        counter.increment();
        return false;
      }
      else {
        return true;
      }
    }
  }

  @Override
  protected void gwtSetUp() throws Exception {
    final UncaughtExceptionHandler oldHandler = GWT.getUncaughtExceptionHandler();
    GWT.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
      @Override
      public void onUncaughtException(Throwable t) {
        if (!(t instanceof SecurityException)) {
          // let's not swallow assertion errors
          oldHandler.onUncaughtException(t);
        }
      }
    });

    super.gwtSetUp();

    CDI.addPostInitTask(new Runnable() {

      @Override
      public void run() {
        final Navigation nav = IOC.getBeanManager().lookupBean(Navigation.class).getInstance();
        nav.goToWithRole(DefaultPage.class);
      }
    });
  }

  @Override
  protected void gwtTearDown() throws Exception {
    ((ClientMessageBusImpl) ErraiBus.get()).removeAllUncaughtExceptionHandlers();
    super.gwtTearDown();
  }

  @Override
  public String getModuleName() {
    return "org.jboss.errai.security.SecurityInterceptorTest";
  }

  @Test
  public void testAuthInterceptorNotLoggedInHomogenous() throws Exception {
    asyncTest();
    final Counter errorCounter = new Counter();
    CDI.addPostInitTask(new Runnable() {
      @Override
      public void run() {
        // Explicitly set active user to null to validate cache.
        final ActiveUserProvider provider =
                IOC.getBeanManager().lookupBean(ActiveUserProvider.class).getInstance();
        provider.setActiveUser(null);
        assertTrue(provider.isCacheValid());
        createCall(new RemoteCallback<Void>() {
          @Override
          public void callback(Void response) {
            fail();
          }
        }, new ErrorCountingCallback(errorCounter, UnauthenticatedException.class),
                AuthenticatedService.class)
                .userStuff();
        testUntil(TIME_LIMIT, new Runnable() {
          @Override
          public void run() {
            assertEquals(1, errorCounter.getCount());
          }
        });
      }
    });
  }

  @Test
  public void testAuthInterceptorRedirectsWithNoErrorHandler() throws
          Exception {
    asyncTest();
    runNavTest(new Runnable() {
      @Override
      public void run() {
        final TestLoginPage page =
                IOC.getBeanManager().lookupBean(TestLoginPage.class).getInstance();
        assertEquals(0, page.getPageLoadCounter());

        // Explicitly set active user to null to validate cache.
        final ActiveUserProvider provider =
                IOC.getBeanManager().lookupBean(ActiveUserProvider.class).getInstance();
        provider.setActiveUser(null);
        assertTrue(provider.isCacheValid());
        createCall(new RemoteCallback<Void>() {
          @Override
          public void callback(Void response) {
            fail();
          }
        }, AuthenticatedService.class)
                .userStuff();
        testUntil(TIME_LIMIT, new Runnable() {
          @Override
          public void run() {
            assertEquals(1, page.getPageLoadCounter());
          }
        });
      }
    });
  }

  @Test
  public void testAuthInterceptorNotLoggedInHeterogenous() throws Exception {
    asyncTest();
    final Counter errorCounter = new Counter();
    CDI.addPostInitTask(new Runnable() {
      @Override
      public void run() {
        // Explicitly set active user to null to validate cache.
        final ActiveUserProvider provider =
                IOC.getBeanManager().lookupBean(ActiveUserProvider.class).getInstance();
        provider.setActiveUser(null);
        assertTrue(provider.isCacheValid());

        createCall(new RemoteCallback<Void>() {
          @Override
          public void callback(Void response) {
            fail();
          }
        }, new ErrorCountingCallback(errorCounter, UnauthenticatedException.class),
                DiverseService.class)
                .needsAuthentication();
        testUntil(TIME_LIMIT, new Runnable() {
          @Override
          public void run() {
            assertEquals(1, errorCounter.getCount());
          }
        });
      }
    });
  }

  @Test
  public void testAuthInterceptorLoggedInHeterogenous() throws Exception {
    asyncTest();
    final Counter counter = new Counter();
    final Counter errorCounter = new Counter();
    CDI.addPostInitTask(new Runnable() {
      @Override
      public void run() {
        createCall(new RemoteCallback<User>() {
          @Override
          public void callback(User response) {
            assertEquals(0, counter.getCount());
            createCall(new RemoteCallback<Void>() {
              @Override
              public void callback(Void response) {
                counter.increment();
              }
            }, new ErrorCountingCallback(errorCounter, UnauthenticatedException.class),
                    DiverseService.class)
                    .needsAuthentication();
            testUntil(TIME_LIMIT, new Runnable() {
              @Override
              public void run() {
                assertEquals(1, counter.getCount());
                assertEquals(0, errorCounter.getCount());
              }
            });
          }
        }, AuthenticationService.class).login("john", "123");
      }
    });
  }

  @Test
  public void testAuthInterceptorLoggedInHomogenous() throws Exception {
    asyncTest();
    final Counter counter = new Counter();
    final Counter errorCounter = new Counter();
    CDI.addPostInitTask(new Runnable() {
      @Override
      public void run() {
        createCall(new RemoteCallback<User>() {
          @Override
          public void callback(User response) {
            assertEquals(0, counter.getCount());
            createCall(new RemoteCallback<Void>() {
              @Override
              public void callback(Void response) {
                counter.increment();
              }
            }, new ErrorCountingCallback(errorCounter, UnauthenticatedException.class),
                    AuthenticatedService.class)
                    .userStuff();
            testUntil(TIME_LIMIT, new Runnable() {
              @Override
              public void run() {
                assertEquals(1, counter.getCount());
                assertEquals(0, errorCounter.getCount());
              }
            });
          }
        }, AuthenticationService.class).login("john", "123");
      }
    });
  }

  @Test
  public void testRoleInterceptorNotLoggedInHomogenous() throws Exception {
    asyncTest();
    final Counter counter = new Counter();
    final Counter errorCounter = new Counter();
    CDI.addPostInitTask(new Runnable() {
      @Override
      public void run() {
        // Explicitly set active user to null to validate cache.
        final ActiveUserProvider provider =
                IOC.getBeanManager().lookupBean(ActiveUserProvider.class).getInstance();
        provider.setActiveUser(null);
        assertTrue(provider.isCacheValid());

        assertEquals(0, counter.getCount());
        assertEquals(0, errorCounter.getCount());
        createCall(new RemoteCallback<Void>() {
          @Override
          public void callback(Void response) {
            counter.increment();
          }
        }, new ErrorCountingCallback(errorCounter, UnauthenticatedException.class),
                AdminService.class).adminStuff();
        testUntil(TIME_LIMIT, new Runnable() {
          @Override
          public void run() {
            assertEquals(0, counter.getCount());
            assertEquals(1, errorCounter.getCount());
          }
        });
      }
    });
  }

  @Test
  public void
          testRoleInterceptorRedirectsToLoginWhenNotLoggedInAndNoErrorHandler()
                  throws Exception {
    asyncTest();
    final Counter counter = new Counter();
    runNavTest(new Runnable() {
      @Override
      public void run() {
        final TestLoginPage page =
                IOC.getBeanManager().lookupBean(TestLoginPage.class).getInstance();
        assertEquals(0, page.getPageLoadCounter());

        // Explicitly set active user to null to validate cache.
        final ActiveUserProvider provider =
                IOC.getBeanManager().lookupBean(ActiveUserProvider.class).getInstance();
        provider.setActiveUser(null);
        assertTrue(provider.isCacheValid());

        assertEquals(0, counter.getCount());
        createCall(new RemoteCallback<Void>() {
          @Override
          public void callback(Void response) {
            counter.increment();
          }
        }, AdminService.class).adminStuff();
        testUntil(TIME_LIMIT, new Runnable() {
          @Override
          public void run() {
            assertEquals(0, counter.getCount());
            assertEquals(1, page.getPageLoadCounter());
          }
        });
      }
    });
  }

  @Test
  public void testRoleInterceptorNotLoggedInHeterogenous() throws Exception {
    asyncTest();
    final Counter counter = new Counter();
    final Counter errorCounter = new Counter();
    CDI.addPostInitTask(new Runnable() {
      @Override
      public void run() {
        // Explicitly set active user to null to validate cache.
        final ActiveUserProvider provider =
                IOC.getBeanManager().lookupBean(ActiveUserProvider.class).getInstance();
        provider.setActiveUser(null);
        assertTrue(provider.isCacheValid());

        assertEquals(0, counter.getCount());
        assertEquals(0, errorCounter.getCount());
        createCall(new RemoteCallback<Void>() {
          @Override
          public void callback(Void response) {
            counter.increment();
          }
        }, new ErrorCountingCallback(errorCounter, UnauthenticatedException.class),
                DiverseService.class).adminOnly();
        testUntil(TIME_LIMIT, new Runnable() {
          @Override
          public void run() {
            assertEquals(0, counter.getCount());
            assertEquals(1, errorCounter.getCount());
          }
        });
      }
    });
  }

  @Test
  public void testRoleInterceptorLoggedInUnprivelegedHeterogenous() throws
          Exception {
    asyncTest();
    final Counter counter = new Counter();
    final Counter errorCounter = new Counter();
    CDI.addPostInitTask(new Runnable() {
      @Override
      public void run() {
        MessageBuilder.createCall(new RemoteCallback<User>() {
          @Override
          public void callback(User response) {
            assertEquals(0, counter.getCount());
            assertEquals(0, errorCounter.getCount());
            createCall(new RemoteCallback<Void>() {
              @Override
              public void callback(Void response) {
                counter.increment();
              }
            }, new ErrorCountingCallback(errorCounter, UnauthorizedException.class),
                    DiverseService.class).adminOnly();
            testUntil(TIME_LIMIT, new Runnable() {
              @Override
              public void run() {
                assertEquals(0, counter.getCount());
                assertEquals(1, errorCounter.getCount());
              }
            });
          }
        }, AuthenticationService.class).login("john", "123");
      }
    });
  }

  @Test
  public void testRoleInterceptorLoggedInUnprivelegedRedirectsToSecurityErrorWithNoErrorCallback() throws Exception {
    asyncTest();
    final Counter counter = new Counter();
    runNavTest(new Runnable() {
      @Override
      public void run() {
        final TestSecurityErrorPage page = IOC.getBeanManager().lookupBean(TestSecurityErrorPage.class)
                .getInstance();
        assertEquals(0, page.getPageLoadCounter());

        MessageBuilder.createCall(new RemoteCallback<User>() {
          @Override
          public void callback(User response) {
            assertEquals(0, counter.getCount());
            createCall(new RemoteCallback<Void>() {
              @Override
              public void callback(Void response) {
                counter.increment();
              }
            }, DiverseService.class).adminOnly();
            testUntil(TIME_LIMIT, new Runnable() {
              @Override
              public void run() {
                assertEquals(0, counter.getCount());
                assertEquals(1, page.getPageLoadCounter());
              }
            });
          }
        }, AuthenticationService.class).login("john", "123");
      }
    });
  }

  @Test
  public void testRoleInterceptorLoggedInUnprivelegedHomogenous() throws
          Exception {
    asyncTest();
    final Counter counter = new Counter();
    final Counter errorCounter = new Counter();
    CDI.addPostInitTask(new Runnable() {
      @Override
      public void run() {
        MessageBuilder.createCall(new RemoteCallback<User>() {
          @Override
          public void callback(User response) {
            assertEquals(0, counter.getCount());
            assertEquals(0, errorCounter.getCount());
            createCall(new RemoteCallback<Void>() {
              @Override
              public void callback(Void response) {
                counter.increment();
              }
            }, new ErrorCountingCallback(errorCounter, UnauthorizedException.class),
                    AdminService.class).adminStuff();
            testUntil(TIME_LIMIT, new Runnable() {
              @Override
              public void run() {
                assertEquals(0, counter.getCount());
                assertEquals(1, errorCounter.getCount());
              }
            });
          }
        }, AuthenticationService.class).login("john", "123");
      }
    });
  }

  @Test
  public void testRoleInterceptorLoggedInPrivelegedHomogenous() throws
          Exception {
    asyncTest();
    final Counter counter = new Counter();
    final Counter errorCounter = new Counter();
    CDI.addPostInitTask(new Runnable() {
      @Override
      public void run() {
        MessageBuilder.createCall(new RemoteCallback<User>() {
          @Override
          public void callback(User response) {
            assertEquals(0, counter.getCount());
            assertEquals(0, errorCounter.getCount());
            createCall(new RemoteCallback<Void>() {
              @Override
              public void callback(Void response) {
                counter.increment();
              }
            }, new ErrorCountingCallback(errorCounter, UnauthorizedException.class),
                    AdminService.class).adminStuff();
            testUntil(TIME_LIMIT, new Runnable() {
              @Override
              public void run() {
                assertEquals(1, counter.getCount());
                assertEquals(0, errorCounter.getCount());
              }
            });
          }
        }, AuthenticationService.class).login("admin", "123");
      }
    });
  }

  @Test
  public void testRoleInterceptorLoggedInPrivelegedHeterogenous() throws
          Exception {
    asyncTest();
    final Counter counter = new Counter();
    final Counter errorCounter = new Counter();
    CDI.addPostInitTask(new Runnable() {
      @Override
      public void run() {
        MessageBuilder.createCall(new RemoteCallback<User>() {
          @Override
          public void callback(User response) {
            assertEquals(0, counter.getCount());
            assertEquals(0, errorCounter.getCount());
            createCall(new RemoteCallback<Void>() {
              @Override
              public void callback(Void response) {
                counter.increment();
              }
            }, new ErrorCountingCallback(errorCounter, UnauthorizedException.class),
                    DiverseService.class).adminOnly();
            testUntil(TIME_LIMIT, new Runnable() {
              @Override
              public void run() {
                assertEquals(1, counter.getCount());
                assertEquals(0, errorCounter.getCount());
              }
            });
          }
        }, AuthenticationService.class).login("admin", "123");
      }
    });
  }

  private void runNavTest(final Runnable runnable) {
    CDI.addPostInitTask(new Runnable() {

      @Override
      public void run() {
        InitVotes.registerOneTimeInitCallback(runnable);
      }
    });
  }

  private void testUntil(final long duration, final Runnable runnable) {
    delayTestFinish((int) (2 * TIME_LIMIT));
    final long startTime = System.currentTimeMillis();
    final int interval = 500;
    new Timer() {
      @Override
      public void run() {
        final long buffer = 4 * interval;
        if (System.currentTimeMillis() + buffer < startTime + duration) {
          boolean passed = true;
          try {
            runnable.run();
          }
          catch (AssertionFailedError e) {
            passed = false;
          }
          catch (Throwable t) {
            cancel();
          }
          finally {
            if (passed) {
              cancel();
              finishTest();
            }
          }
        }
        else {
          cancel();
          runnable.run();
          finishTest();
        }
      }
    }.scheduleRepeating(interval);
  }
}
