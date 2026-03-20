package dev.pradeep.dockerbackend.resource.annotation;

import java.lang.annotation.*;

/**
 * Marks a controller method (or class) as requiring specific fine-grained permissions.
 * Evaluated by {@link dev.pradeep.dockerbackend.resource.interceptor.PermissionInterceptor}.
 *
 * Usage:
 *   @RequiresPermission("READ_ORDERS")                            // needs READ_ORDERS
 *   @RequiresPermission({"READ_ORDERS", "WRITE_ORDERS"})          // needs READ_ORDERS OR WRITE_ORDERS
 *   @RequiresPermission(value = {"READ_ORDERS", "WRITE_ORDERS"}, requireAll = true)  // needs BOTH
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresPermission {
    /** The permission name(s) required. */
    String[] value();

    /** If true, ALL permissions must be present. If false (default), ANY one is sufficient. */
    boolean requireAll() default false;
}
