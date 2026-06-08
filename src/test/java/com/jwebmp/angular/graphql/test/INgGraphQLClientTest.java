package com.jwebmp.angular.graphql.test;

import com.jwebmp.angular.graphql.INgGraphQLClient;
import com.jwebmp.angular.graphql.annotations.NgGraphQL;
import com.jwebmp.angular.graphql.annotations.NgGraphQLVariable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the {@link INgGraphQLClient} interface to ensure the apollo-angular
 * TypeScript generation produces correct output for queries, mutations and subscriptions.
 */
class INgGraphQLClientTest
{
    // ── Test fixtures ──────────────────────────────────────────────────

    @NgGraphQL(
            operation = """
                    query Users($active: Boolean) {
                      users(active: $active) { id name email }
                    }
                    """,
            operationType = NgGraphQL.OperationType.QUERY,
            fetchOnCreate = true,
            pollingEnabled = true,
            pollingIntervalMs = 10_000,
            fetchPolicy = NgGraphQL.FetchPolicy.CACHE_AND_NETWORK
    )
    @NgGraphQLVariable(name = "active", value = "true")
    static class UsersQuery implements INgGraphQLClient<UsersQuery>
    {
    }

    @NgGraphQL(
            operation = """
                    mutation CreateOrder($input: OrderInput!) {
                      createOrder(input: $input) { id status }
                    }
                    """,
            operationType = NgGraphQL.OperationType.MUTATION,
            singleton = false,
            errorPolicy = NgGraphQL.ErrorPolicy.ALL
    )
    static class CreateOrderMutation implements INgGraphQLClient<CreateOrderMutation>
    {
    }

    @NgGraphQL(
            operation = """
                    subscription OnNotification {
                      notificationAdded { id message }
                    }
                    """,
            operationType = NgGraphQL.OperationType.SUBSCRIPTION,
            responseArray = true,
            fetchOnCreate = true
    )
    static class NotificationSubscription implements INgGraphQLClient<NotificationSubscription>
    {
    }

    // ── Annotation accessor ────────────────────────────────────────────

    @Test
    void testAnnotationAccessor()
    {
        UsersQuery client = new UsersQuery();
        NgGraphQL annotation = client.getAnnotation();
        assertNotNull(annotation);
        assertEquals(NgGraphQL.OperationType.QUERY, annotation.operationType());
        assertTrue(annotation.singleton());
    }

    // ── Decorators ─────────────────────────────────────────────────────

    @Test
    void testDecorators_Singleton()
    {
        assertTrue(new UsersQuery().decorators().stream()
                                   .anyMatch(d -> d.contains("providedIn: 'root'")));
    }

    @Test
    void testDecorators_NonSingleton()
    {
        assertTrue(new CreateOrderMutation().decorators().stream()
                                            .anyMatch(d -> d.contains("providedIn: 'any'")));
    }

    // ── Fields ─────────────────────────────────────────────────────────

    @Test
    void testFields_Query_CoreFields()
    {
        String joined = String.join("\n", new UsersQuery().fields());
        assertTrue(joined.contains("inject(Apollo)"));
        assertTrue(joined.contains("data: WritableSignal<any | undefined>"));
        assertTrue(joined.contains("loading: WritableSignal<boolean>"));
        assertTrue(joined.contains("gql`"));
        assertTrue(joined.contains("query Users"));
        assertTrue(joined.contains("pollingIntervalMs = 10000"));
        assertTrue(joined.contains("queryRef"));
    }

    @Test
    void testFields_Subscription_ArrayResponse()
    {
        String joined = String.join("\n", new NotificationSubscription().fields());
        assertTrue(joined.contains("data: WritableSignal<any[]>"));
        assertTrue(joined.contains("signal<any[]>([])"));
        // subscriptions are not queries -> no queryRef / polling fields
        assertFalse(joined.contains("queryRef"));
    }

    // ── Constructor body ───────────────────────────────────────────────

    @Test
    void testConstructorBody_Query_FetchOnCreate()
    {
        String joined = String.join("\n", new UsersQuery().constructorBody());
        assertTrue(joined.contains("this.execute();"));
        assertTrue(joined.contains("this.destroyRef.onDestroy"));
    }

    @Test
    void testConstructorBody_Subscription_FetchOnCreate()
    {
        String joined = String.join("\n", new NotificationSubscription().constructorBody());
        assertTrue(joined.contains("this.subscribe();"));
    }

    @Test
    void testConstructorBody_Mutation_NeverAutoExecutes()
    {
        String joined = String.join("\n", new CreateOrderMutation().constructorBody());
        assertFalse(joined.contains("this.execute();"));
        assertFalse(joined.contains("this.mutate("));
    }

    // ── Methods ────────────────────────────────────────────────────────

    @Test
    void testMethods_Query_HasExecuteRefetchPolling()
    {
        String joined = String.join("\n", new UsersQuery().methods());
        assertTrue(joined.contains("execute(variables?: Record<string, any>): void"));
        assertTrue(joined.contains("this.apollo.watchQuery<"));
        assertTrue(joined.contains("fetchPolicy: 'cache-and-network'"));
        assertTrue(joined.contains("pollInterval: this.pollingIntervalMs"));
        assertTrue(joined.contains("refetch(variables?:"));
        assertTrue(joined.contains("startPolling("));
        assertTrue(joined.contains("stopPolling("));
        assertFalse(joined.contains("this.apollo.mutate"));
    }

    @Test
    void testMethods_Query_MergesDefaultVariables()
    {
        String varsMethod = new UsersQuery().methods().stream()
                                            .filter(m -> m.startsWith("private mergeVariables"))
                                            .findFirst().orElse("");
        assertTrue(varsMethod.contains("'active': true"));
        assertTrue(varsMethod.contains("{ ...defaults, ...(variables ?? {}) }"));
    }

    @Test
    void testMethods_Mutation_HasMutate()
    {
        String joined = String.join("\n", new CreateOrderMutation().methods());
        assertTrue(joined.contains("mutate(variables?: Record<string, any>): void"));
        assertTrue(joined.contains("this.apollo.mutate<"));
        assertTrue(joined.contains("errorPolicy: 'all'"));
        assertFalse(joined.contains("this.apollo.watchQuery"));
        assertFalse(joined.contains("startPolling("));
    }

    @Test
    void testMethods_Subscription_HasSubscribe()
    {
        String joined = String.join("\n", new NotificationSubscription().methods());
        assertTrue(joined.contains("subscribe(variables?: Record<string, any>): void"));
        assertTrue(joined.contains("this.apollo.subscribe<"));
        assertFalse(joined.contains("this.apollo.mutate"));
    }

    @Test
    void testMethods_All_HaveResetAndNgOnDestroy()
    {
        for (INgGraphQLClient<?> client : List.of(new UsersQuery(), new CreateOrderMutation(), new NotificationSubscription()))
        {
            String joined = String.join("\n", client.methods());
            assertTrue(joined.contains("reset(): void"), client.getClass().getSimpleName());
            assertTrue(joined.contains("ngOnDestroy(): void"), client.getClass().getSimpleName());
            assertTrue(joined.contains("handleResult(result: any): void"), client.getClass().getSimpleName());
        }
    }

    @Test
    void testInterfaces_IncludesOnDestroy()
    {
        assertTrue(new UsersQuery().interfaces().contains("OnDestroy"));
    }

    @Test
    void testRenderOnDestroyMethod_ReturnsEmpty()
    {
        assertEquals("", new UsersQuery().renderOnDestroyMethod());
    }

    // ── Layout / structural correctness ────────────────────────────────

    @Test
    void testLayout_AllMethods_HaveBalancedBraces()
    {
        for (INgGraphQLClient<?> client : List.of(new UsersQuery(), new CreateOrderMutation(), new NotificationSubscription()))
        {
            for (String method : client.methods())
            {
                assertEquals(count(method, '{'), count(method, '}'),
                        () -> client.getClass().getSimpleName() + " unbalanced braces in:\n" + method);
                assertEquals(count(method, '('), count(method, ')'),
                        () -> client.getClass().getSimpleName() + " unbalanced parentheses in:\n" + method);
            }
        }
    }

    @Test
    void testLayout_AllFields_AreTerminated()
    {
        for (INgGraphQLClient<?> client : List.of(new UsersQuery(), new CreateOrderMutation(), new NotificationSubscription()))
        {
            for (String field : client.fields())
            {
                assertTrue(field.strip().endsWith(";"),
                        () -> client.getClass().getSimpleName() + " field not terminated: " + field);
            }
        }
    }

    @Test
    void testLayout_DocumentField_IsWellFormedGqlTemplate()
    {
        String document = new UsersQuery().fields().stream()
                                          .filter(f -> f.contains("gql`"))
                                          .findFirst().orElse("");
        assertFalse(document.isBlank(), "Should emit a gql document field");
        // exactly one opening and one closing backtick
        assertEquals(2, count(document, '`'), "gql template should be delimited by a single pair of backticks");
        assertTrue(document.contains("query Users"), "gql template should contain the operation text");
        assertTrue(document.strip().endsWith("`;"), "document field should end with the closing backtick + semicolon");
    }

    @Test
    void testLayout_QueryService_AssemblesInOrder()
    {
        UsersQuery client = new UsersQuery();
        // Decorator precedes the fields/methods; the service body parts are non-empty and balanced overall.
        String decorators = String.join("\n", client.decorators());
        String body = String.join("\n", client.fields()) + "\n" + String.join("\n", client.methods());

        assertTrue(decorators.contains("@Injectable"));
        assertEquals(count(body, '{'), count(body, '}'), "Assembled service body should have balanced braces");
        // The trigger method appears once
        assertEquals(1, client.methods().stream().filter(m -> m.startsWith("execute(")).count());
    }

    private static long count(String s, char c)
    {
        return s.chars().filter(ch -> ch == c).count();
    }
}


