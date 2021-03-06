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

package org.jboss.errai.ioc.client;

import static org.jboss.errai.common.client.util.LogUtil.displayDebuggerUtilityTitle;
import static org.jboss.errai.common.client.util.LogUtil.displaySeparator;
import static org.jboss.errai.common.client.util.LogUtil.log;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Timer;
import org.jboss.errai.common.client.api.extension.InitVotes;
import org.jboss.errai.ioc.client.container.BeanRef;
import org.jboss.errai.ioc.client.container.CreationalContext;
import org.jboss.errai.ioc.client.container.IOCBeanManagerLifecycle;
import org.jboss.errai.ioc.client.container.IOCEnvironment;
import org.jboss.errai.ioc.client.container.SimpleCreationalContext;
import org.jboss.errai.ioc.client.container.async.AsyncCreationalContext;
import org.jboss.errai.ioc.rebind.ioc.injector.api.InjectionContext;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Container implements EntryPoint {
  @Override
  public void onModuleLoad() {
    bootstrapContainer();
  }

  // stored for debugging purposes only. overwritten every time the container is bootstrapped.
  private static BootstrapInjectionContext injectionContext;

  public void bootstrapContainer() {
    try {
      init = false;

      QualifierUtil.initFromFactoryProvider(new QualifierEqualityFactoryProvider() {
        @Override
        public QualifierEqualityFactory provide() {
          return GWT.create(QualifierEqualityFactory.class);
        }
      });

      log("IOC bootstrapper successfully initialized.");

      if (GWT.<IOCEnvironment>create(IOCEnvironment.class).isAsync()) {
        log("bean manager initialized in async mode.");
      }

      injectionContext = ((Bootstrapper) GWT.create(Bootstrapper.class)).bootstrapContainer();

      final CreationalContext rootContext = injectionContext.getRootContext();

      if (rootContext instanceof AsyncCreationalContext) {
        ((AsyncCreationalContext) rootContext).finish(new Runnable() {
          @Override
          public void run() {
            finishInit();
          }
        });
      }
      else {
        ((SimpleCreationalContext) rootContext).finish();
        finishInit();
      }
    }
    catch (Throwable t) {
      t.printStackTrace();
      throw new RuntimeException("critical error in IOC container bootstrap: " + t.getClass().getName() + ": "
          + t.getMessage());
    }
  }

  private static final List<Runnable> afterInit = new ArrayList<Runnable>();
  private static boolean init = false;

  private void finishInit() {
    init = true;
    log(injectionContext.getRootContext().getAllCreatedBeans().size() + " beans successfully deployed.");
    declareDebugFunction();
    new CallbacksRunnable().run();

    log("bean manager now in service.");

  }

  private static class CallbacksRunnable implements Runnable {
    @Override
    public void run() {
      final Iterator<Runnable> runnableIterator = afterInit.iterator();
      while (runnableIterator.hasNext()) {
        runnableIterator.next().run();
        runnableIterator.remove();
      }
    }
  }

  /**
   * Runs the specified {@link Runnable} only after the bean manager has fully initialized. It is generally not
   * necessary to use this method from within beans themselves. But if you are generated out-of-container calls
   * into the bean manager (such as for testing), it may be necessary to use this method to ensure that the beans
   * you wish to lookup have been loaded.
   * <p/>
   * Use of this method is really only necessary when using the bean manager in asynchronous mode as wiring of the
   * container synchronously does not yield during bootstrapping operations.
   * <p/>
   * If the bean manager is already initialized when you call this method, the <tt>Runnable</tt> is invoked immediately.
   *
   * @param runnable
   *     the {@link Runnable} to execute after bean manager initialization.
   */
  public static void runAfterInit(final Runnable runnable) {
    if (init) {
      runnable.run();
    }

    afterInit.add(runnable);
  }

  /**
   * Short-alias method for {@link #runAfterInit(Runnable)}.
   *
   * @param runnable
   */
  public static void $(final Runnable runnable) {
    runAfterInit(runnable);
  }

  /**
   * Declares the JavaScript-accessible debugging function to query the status of the bean manager at runtime. The
   * JSNI method internally calls {@link #displayBeanManagerStatus()}.
   */
  private static native void declareDebugFunction() /*-{
    $wnd.errai_bean_manager_status = function () {
      @org.jboss.errai.ioc.client.Container::displayBeanManagerStatus()();
    }
  }-*/;

  /**
   * Displays the the bean manager status to the JavaScript debugging console in the browser.
   */
  private static void displayBeanManagerStatus() {
    displayDebuggerUtilityTitle("BeanManager Status");

    log("[WIRED BEANS]");
    for (final BeanRef ref : injectionContext.getRootContext().getAllCreatedBeans()) {
      log(" -> " + ref.getClazz().getName());
      log("     qualifiers: " + annotationsToString(ref.getAnnotations()) + ")");
    }
    log("Total: " + injectionContext.getRootContext().getAllCreatedBeans().size());
    displaySeparator();
  }

  /**
   * Converts the specified annotation array to a string representation. Used to display the bean manager status.
   *
   * @param annotations
   *
   * @return
   */
  private static String annotationsToString(final Annotation[] annotations) {
    final StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < annotations.length; i++) {
      sb.append(annotations[i].annotationType().getName());

      if (i + 1 < annotations.length) sb.append(", ");
    }
    return sb.toString();
  }
}
