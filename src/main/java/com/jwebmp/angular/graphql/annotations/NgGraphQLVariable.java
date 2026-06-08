package com.jwebmp.angular.graphql.annotations;

import java.lang.annotation.*;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

/**
 * Declares a default GraphQL operation variable for an {@link NgGraphQL} client.
 * <p>
 * Default variables are merged with any variables passed at call-time, with the
 * runtime variables taking precedence.
 * <p>
 * The {@link #value()} is emitted verbatim as a TypeScript expression, so it may be
 * a JSON literal ({@code "true"}, {@code "42"}, {@code "'active'"}) or a reference to
 * a field on the service.
 */
@Target({TYPE})
@Retention(RUNTIME)
@Inherited
@Repeatable(NgGraphQLVariables.class)
public @interface NgGraphQLVariable
{
    /**
     * The variable name (without the leading {@code $}).
     *
     * @return the variable name
     */
    String name();

    /**
     * The default value as a TypeScript expression.
     *
     * @return the default value expression
     */
    String value();
}

