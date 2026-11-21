package com.paddleup.support;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Counts the statements the application prepares, so a test can assert that a read costs the same
 * number of queries however much data it returns. Import it into the test that needs it.
 */
@TestConfiguration
public class QueryCounter {

  /** Statements prepared or created since the application started. */
  public static final AtomicInteger STATEMENTS = new AtomicInteger();

  @Bean
  static BeanPostProcessor countingDataSource() {
    return new BeanPostProcessor() {
      @Override
      public Object postProcessAfterInitialization(Object bean, String name) {
        if (bean instanceof DataSource ds && !Proxy.isProxyClass(bean.getClass())) {
          return Proxy.newProxyInstance(
              ds.getClass().getClassLoader(),
              new Class<?>[] {DataSource.class},
              (proxy, method, args) -> {
                Object result = call(ds, method, args);
                return result instanceof Connection c && method.getName().equals("getConnection")
                    ? counting(c)
                    : result;
              });
        }
        return bean;
      }
    };
  }

  private static Connection counting(Connection connection) {
    return (Connection)
        Proxy.newProxyInstance(
            connection.getClass().getClassLoader(),
            new Class<?>[] {Connection.class},
            (proxy, method, args) -> {
              if (method.getName().equals("prepareStatement")
                  || method.getName().equals("createStatement")) {
                STATEMENTS.incrementAndGet();
              }
              return call(connection, method, args);
            });
  }

  private static Object call(Object target, java.lang.reflect.Method method, Object[] args)
      throws Throwable {
    try {
      return method.invoke(target, args);
    } catch (InvocationTargetException e) {
      throw e.getCause();
    }
  }
}
