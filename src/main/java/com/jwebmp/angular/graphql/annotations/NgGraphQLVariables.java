package com.jwebmp.angular.graphql.annotations;

import java.lang.annotation.*;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

/**
 * Container annotation for repeatable {@link NgGraphQLVariable} declarations.
 */
@Target({TYPE})
@Retention(RUNTIME)
@Inherited
public @interface NgGraphQLVariables
{
    /**
     * The contained variables.
     *
     * @return the variables
     */
    NgGraphQLVariable[] value();
}

