package com.jwebmp.angular.graphql.annotations;

import com.jwebmp.core.base.angular.client.services.interfaces.INgDataType;

import java.lang.annotation.*;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

/**
 * Marks a class as an Apollo GraphQL client service for a single operation
 * (query, mutation or subscription).
 * <p>
 * Each annotated class generates a standalone {@code @Injectable} Angular service
 * built on top of <a href="https://the-guild.dev/graphql/apollo-angular">apollo-angular</a>.
 * The generated service:
 * <ul>
 *   <li>Wraps the GraphQL document in a {@code gql} template literal</li>
 *   <li>Exposes the result through an Angular {@code WritableSignal}</li>
 *   <li>Exposes {@code loading}, {@code error} and {@code success} state signals</li>
 *   <li>Provides an {@code execute()} / {@code mutate()} / {@code subscribe()} trigger
 *       depending on the {@link #operationType()}</li>
 *   <li>Supports optional polling (queries only) and one-shot refetch</li>
 *   <li>Applies a configurable Apollo {@link FetchPolicy} and {@link ErrorPolicy}</li>
 * </ul>
 * <p>
 * Default operation variables can be declared with {@code @NgGraphQLVariable}.
 */
@Target({TYPE})
@Retention(RUNTIME)
@Inherited
public @interface NgGraphQL
{
    /**
     * A friendly name for this client service – used as the base for the generated
     * TypeScript class name when not derived from the Java class name.
     *
     * @return the service name
     */
    String value() default "";

    /**
     * The GraphQL operation document. This is the raw GraphQL text (query, mutation
     * or subscription) that is embedded into a {@code gql} template literal.
     * <p>
     * Example:
     * <pre>{@code
     * query Users($active: Boolean) {
     *   users(active: $active) { id name email }
     * }
     * }</pre>
     *
     * @return the GraphQL operation document
     */
    String operation();

    /**
     * The kind of GraphQL operation – determines whether the generated service
     * exposes {@code execute()} (query), {@code mutate()} (mutation) or
     * {@code subscribe()} (subscription).
     *
     * @return the operation type
     */
    OperationType operationType() default OperationType.QUERY;

    // ── Response type ──────────────────────────────────────────────────

    /**
     * The data-type class that represents the operation result.
     * Defaults to {@code Object} / {@code any} when not specified.
     *
     * @return the response data type
     */
    Class<? extends INgDataType> responseType() default INgDataType.class;

    /**
     * Whether the result data is an array of {@link #responseType()}.
     *
     * @return true if the response is an array
     */
    boolean responseArray() default false;

    // ── Provided-in ────────────────────────────────────────────────────

    /**
     * Whether the service is a singleton ({@code providedIn: 'root'})
     * or transient ({@code providedIn: 'any'}).
     *
     * @return true for singleton
     */
    boolean singleton() default true;

    // ── Fetch on create ────────────────────────────────────────────────

    /**
     * Whether to automatically fire the operation when the service is first injected.
     * Only applies to {@link OperationType#QUERY} and {@link OperationType#SUBSCRIPTION}.
     *
     * @return true to fetch on creation
     */
    boolean fetchOnCreate() default false;

    // ── Polling ────────────────────────────────────────────────────────

    /**
     * Enable polling for queries – the watch query re-issues at a fixed interval.
     *
     * @return true to enable polling
     */
    boolean pollingEnabled() default false;

    /**
     * Polling interval in milliseconds. Only used when {@link #pollingEnabled()} is true.
     *
     * @return interval in ms
     */
    int pollingIntervalMs() default 30_000;

    // ── Apollo policies ────────────────────────────────────────────────

    /**
     * The Apollo fetch policy applied to the operation.
     *
     * @return the fetch policy
     */
    FetchPolicy fetchPolicy() default FetchPolicy.CACHE_FIRST;

    /**
     * The Apollo error policy applied to the operation.
     *
     * @return the error policy
     */
    ErrorPolicy errorPolicy() default ErrorPolicy.NONE;

    /**
     * The GraphQL operation type.
     */
    enum OperationType
    {
        /**
         * A read-only query operation – generates {@code execute()} / {@code refetch()}.
         */
        QUERY,
        /**
         * A mutation operation – generates {@code mutate(variables)}.
         */
        MUTATION,
        /**
         * A subscription operation – generates {@code subscribe()} over a streamed result.
         */
        SUBSCRIPTION
    }

    /**
     * Apollo fetch policy enum. Maps directly to apollo-client fetch policy strings.
     */
    enum FetchPolicy
    {
        CACHE_FIRST("cache-first"),
        CACHE_AND_NETWORK("cache-and-network"),
        NETWORK_ONLY("network-only"),
        CACHE_ONLY("cache-only"),
        NO_CACHE("no-cache"),
        STANDBY("standby");

        private final String policy;

        FetchPolicy(String policy)
        {
            this.policy = policy;
        }

        /**
         * The apollo-client fetch policy string.
         *
         * @return the policy string
         */
        public String policy()
        {
            return policy;
        }
    }

    /**
     * Apollo error policy enum. Maps directly to apollo-client error policy strings.
     */
    enum ErrorPolicy
    {
        NONE("none"),
        IGNORE("ignore"),
        ALL("all");

        private final String policy;

        ErrorPolicy(String policy)
        {
            this.policy = policy;
        }

        /**
         * The apollo-client error policy string.
         *
         * @return the policy string
         */
        public String policy()
        {
            return policy;
        }
    }
}

