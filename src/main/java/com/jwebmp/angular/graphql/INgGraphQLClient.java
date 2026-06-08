package com.jwebmp.angular.graphql;

import com.jwebmp.angular.graphql.annotations.NgGraphQL;
import com.jwebmp.angular.graphql.annotations.NgGraphQLVariable;
import com.jwebmp.core.base.angular.client.annotations.references.NgComponentReference;
import com.jwebmp.core.base.angular.client.annotations.references.NgImportReference;
import com.jwebmp.core.base.angular.client.annotations.typescript.TsDependency;
import com.jwebmp.core.base.angular.client.services.interfaces.AnnotationUtils;
import com.jwebmp.core.base.angular.client.services.interfaces.IComponent;
import com.jwebmp.core.base.angular.client.services.interfaces.INgDataType;

import java.util.List;

import static com.jwebmp.core.base.angular.client.services.interfaces.AnnotationUtils.getTsFilename;

/**
 * Interface for Apollo GraphQL client services.
 * <p>
 * Each implementation targets a <b>single</b> GraphQL operation (query, mutation or
 * subscription) declared through {@link NgGraphQL}. The generated TypeScript service is an
 * {@code @Injectable} that uses <a href="https://the-guild.dev/graphql/apollo-angular">apollo-angular</a>'s
 * {@code Apollo} client and exposes the result via Angular {@code signal()} /
 * {@code WritableSignal} so consumers can read it reactively.
 * <p>
 * Behaviours controlled through {@link NgGraphQL}:
 * <ul>
 *   <li><b>Operation type</b> – query, mutation or subscription</li>
 *   <li><b>Polling</b> – re-fetch a query at a fixed interval</li>
 *   <li><b>Fetch / error policy</b> – Apollo cache and error handling</li>
 *   <li><b>Default variables</b> – via {@link NgGraphQLVariable}</li>
 * </ul>
 *
 * @param <J> self-referencing generic
 */
@NgImportReference(value = "Injectable", reference = "@angular/core")
@NgImportReference(value = "inject", reference = "@angular/core")
@NgImportReference(value = "signal, WritableSignal", reference = "@angular/core")
@NgImportReference(value = "DestroyRef", reference = "@angular/core")
@NgImportReference(value = "OnDestroy", reference = "@angular/core")
@NgImportReference(value = "Apollo, gql", reference = "apollo-angular")
@NgImportReference(value = "Subscription", reference = "rxjs")

@TsDependency(value = "apollo-angular", version = "^11.0.0")
@TsDependency(value = "@apollo/client", version = "^4.0.0")
@TsDependency(value = "graphql", version = "^16.9.0")
public interface INgGraphQLClient<J extends INgGraphQLClient<J>> extends IComponent<J>
{
    // ── Annotation accessor ────────────────────────────────────────────

    /**
     * Retrieves the {@link NgGraphQL} annotation from the implementing class.
     *
     * @return the annotation
     */
    default NgGraphQL getAnnotation()
    {
        return getClass().getAnnotation(NgGraphQL.class);
    }

    @Override
    default List<String> interfaces()
    {
        List<String> out = IComponent.super.interfaces();
        out.add("OnDestroy");
        return out;
    }

    @Override
    default List<String> decorators()
    {
        List<String> out = IComponent.super.decorators();
        NgGraphQL gq = getAnnotation();
        String providedIn = gq.singleton() ? "root" : "any";
        out.add("@Injectable({\n  providedIn: '" + providedIn + "'\n})");
        return out;
    }

    // ── Fields ─────────────────────────────────────────────────────────

    @Override
    default List<String> fields()
    {
        List<String> fields = IComponent.super.fields();
        NgGraphQL gq = getAnnotation();

        String typeName = resolveResponseTypeName();
        String fullType = gq.responseArray() ? typeName + "[]" : typeName;
        String defaultVal = gq.responseArray() ? "[]" : "undefined";
        String signalType = gq.responseArray() ? fullType : fullType + " | undefined";

        fields.add("private readonly apollo = inject(Apollo);");
        fields.add("private readonly destroyRef = inject(DestroyRef);");

        // Result signal
        fields.add("readonly data: WritableSignal<" + signalType + "> = signal<" + signalType + ">(" + defaultVal + ");");

        // State signals
        fields.add("readonly loading: WritableSignal<boolean> = signal<boolean>(false);");
        fields.add("readonly error: WritableSignal<any> = signal<any>(undefined);");
        fields.add("readonly success: WritableSignal<boolean> = signal<boolean>(false);");

        // The GraphQL operation document
        fields.add("private readonly document = gql`\n" + escapeTemplate(gq.operation().strip()) + "\n`;");

        // Active subscription / query handle
        fields.add("private operationSubscription?: Subscription;");

        if (gq.operationType() == NgGraphQL.OperationType.QUERY)
        {
            fields.add("private queryRef?: any;");
            fields.add("private pollingIntervalMs = " + gq.pollingIntervalMs() + ";");
            fields.add("readonly polling: WritableSignal<boolean> = signal<boolean>(false);");
        }

        return fields;
    }

    // ── Constructor body ───────────────────────────────────────────────

    @Override
    default List<String> constructorBody()
    {
        List<String> body = IComponent.super.constructorBody();
        NgGraphQL gq = getAnnotation();

        body.add("""
                this.destroyRef.onDestroy(() => {
                    this.operationSubscription?.unsubscribe();
                });
                """);

        if (gq.fetchOnCreate())
        {
            switch (gq.operationType())
            {
                case QUERY -> body.add("this.execute();");
                case SUBSCRIPTION -> body.add("this.subscribe();");
                default ->
                {
                    // mutations never auto-execute
                }
            }
        }

        return body;
    }

    // ── Methods ────────────────────────────────────────────────────────

    @Override
    default List<String> methods()
    {
        List<String> methods = IComponent.super.methods();
        NgGraphQL gq = getAnnotation();

        String typeName = resolveResponseTypeName();
        String fullType = gq.responseArray() ? typeName + "[]" : typeName;
        String signalType = gq.responseArray() ? fullType : fullType + " | undefined";

        methods.add(buildDefaultVariablesMethod());

        switch (gq.operationType())
        {
            case QUERY ->
            {
                methods.add(buildQueryExecuteMethod(gq, signalType));
                methods.add(buildRefetchMethod());
                methods.add(buildStartPollingMethod());
                methods.add(buildStopPollingMethod());
            }
            case MUTATION -> methods.add(buildMutateMethod(gq, signalType));
            case SUBSCRIPTION -> methods.add(buildSubscribeMethod(gq, signalType));
        }

        methods.add(buildHandleResultMethod(gq, signalType));
        methods.add(buildResetMethod(gq));
        methods.add(buildNgOnDestroyMethod());

        return methods;
    }

    // ── Private builder helpers ────────────────────────────────────────

    private String resolveResponseTypeName()
    {
        NgGraphQL gq = getAnnotation();
        Class<? extends INgDataType> responseType = gq.responseType();
        if (responseType == INgDataType.class)
        {
            return "any";
        }
        return getTsFilename(responseType);
    }

    private String buildDefaultVariablesMethod()
    {
        NgGraphQLVariable[] vars = getClass().getAnnotationsByType(NgGraphQLVariable.class);
        StringBuilder sb = new StringBuilder();
        sb.append("private mergeVariables(variables?: Record<string, any>): Record<string, any> {\n");
        if (vars.length > 0)
        {
            sb.append("    const defaults: Record<string, any> = {\n");
            for (NgGraphQLVariable v : vars)
            {
                sb.append("        '").append(escapeTs(v.name())).append("': ").append(v.value()).append(",\n");
            }
            sb.append("    };\n");
            sb.append("    return { ...defaults, ...(variables ?? {}) };\n");
        }
        else
        {
            sb.append("    return variables ?? {};\n");
        }
        sb.append("}");
        return sb.toString();
    }

    private String buildQueryExecuteMethod(NgGraphQL gq, String signalType)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("execute(variables?: Record<string, any>): void {\n");
        sb.append("    this.loading.set(true);\n");
        sb.append("    this.error.set(undefined);\n");
        sb.append("    this.success.set(false);\n\n");
        sb.append("    this.operationSubscription?.unsubscribe();\n");
        sb.append("    this.queryRef = this.apollo.watchQuery<").append(signalType).append(">({\n");
        sb.append("        query: this.document,\n");
        sb.append("        variables: this.mergeVariables(variables),\n");
        sb.append("        fetchPolicy: '").append(gq.fetchPolicy().policy()).append("',\n");
        sb.append("        errorPolicy: '").append(gq.errorPolicy().policy()).append("',\n");
        if (gq.pollingEnabled())
        {
            sb.append("        pollInterval: this.pollingIntervalMs,\n");
        }
        sb.append("    });\n\n");
        sb.append("    this.operationSubscription = this.queryRef.valueChanges.subscribe({\n");
        sb.append("        next: (result: any) => this.handleResult(result),\n");
        sb.append("        error: (err: any) => {\n");
        sb.append("            this.error.set(err);\n");
        sb.append("            this.loading.set(false);\n");
        sb.append("            this.success.set(false);\n");
        sb.append("            console.error('[GraphQL] Query error:', err);\n");
        sb.append("        }\n");
        sb.append("    });\n");
        if (gq.pollingEnabled())
        {
            sb.append("    this.polling.set(true);\n");
        }
        sb.append("}");
        return sb.toString();
    }

    private String buildRefetchMethod()
    {
        return """
                refetch(variables?: Record<string, any>): void {
                    if (this.queryRef) {
                        this.queryRef.refetch(this.mergeVariables(variables));
                    } else {
                        this.execute(variables);
                    }
                }""";
    }

    private String buildStartPollingMethod()
    {
        return """
                startPolling(intervalMs?: number): void {
                    if (intervalMs !== undefined) {
                        this.pollingIntervalMs = intervalMs;
                    }
                    if (this.queryRef) {
                        this.queryRef.startPolling(this.pollingIntervalMs);
                        this.polling.set(true);
                    }
                }""";
    }

    private String buildStopPollingMethod()
    {
        return """
                stopPolling(): void {
                    if (this.queryRef) {
                        this.queryRef.stopPolling();
                    }
                    this.polling.set(false);
                }""";
    }

    private String buildMutateMethod(NgGraphQL gq, String signalType)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("mutate(variables?: Record<string, any>): void {\n");
        sb.append("    this.loading.set(true);\n");
        sb.append("    this.error.set(undefined);\n");
        sb.append("    this.success.set(false);\n\n");
        sb.append("    this.operationSubscription?.unsubscribe();\n");
        sb.append("    this.operationSubscription = this.apollo.mutate<").append(signalType).append(">({\n");
        sb.append("        mutation: this.document,\n");
        sb.append("        variables: this.mergeVariables(variables),\n");
        sb.append("        errorPolicy: '").append(gq.errorPolicy().policy()).append("',\n");
        sb.append("    }).subscribe({\n");
        sb.append("        next: (result: any) => this.handleResult(result),\n");
        sb.append("        error: (err: any) => {\n");
        sb.append("            this.error.set(err);\n");
        sb.append("            this.loading.set(false);\n");
        sb.append("            this.success.set(false);\n");
        sb.append("            console.error('[GraphQL] Mutation error:', err);\n");
        sb.append("        }\n");
        sb.append("    });\n");
        sb.append("}");
        return sb.toString();
    }

    private String buildSubscribeMethod(NgGraphQL gq, String signalType)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("subscribe(variables?: Record<string, any>): void {\n");
        sb.append("    this.loading.set(true);\n");
        sb.append("    this.error.set(undefined);\n");
        sb.append("    this.success.set(false);\n\n");
        sb.append("    this.operationSubscription?.unsubscribe();\n");
        sb.append("    this.operationSubscription = this.apollo.subscribe<").append(signalType).append(">({\n");
        sb.append("        query: this.document,\n");
        sb.append("        variables: this.mergeVariables(variables),\n");
        sb.append("        errorPolicy: '").append(gq.errorPolicy().policy()).append("',\n");
        sb.append("    }).subscribe({\n");
        sb.append("        next: (result: any) => this.handleResult(result),\n");
        sb.append("        error: (err: any) => {\n");
        sb.append("            this.error.set(err);\n");
        sb.append("            this.loading.set(false);\n");
        sb.append("            this.success.set(false);\n");
        sb.append("            console.error('[GraphQL] Subscription error:', err);\n");
        sb.append("        }\n");
        sb.append("    });\n");
        sb.append("}");
        return sb.toString();
    }

    private String buildHandleResultMethod(NgGraphQL gq, String signalType)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("private handleResult(result: any): void {\n");
        sb.append("    if (result?.errors && result.errors.length) {\n");
        sb.append("        this.error.set(result.errors);\n");
        sb.append("    }\n");
        sb.append("    this.data.set(result?.data as ").append(signalType).append(");\n");
        sb.append("    this.loading.set(!!result?.loading);\n");
        sb.append("    this.success.set(!result?.errors);\n");
        sb.append("}");
        return sb.toString();
    }

    private String buildResetMethod(NgGraphQL gq)
    {
        String defaultVal = gq.responseArray() ? "[] as any" : "undefined";
        StringBuilder sb = new StringBuilder();
        sb.append("reset(): void {\n");
        sb.append("    this.operationSubscription?.unsubscribe();\n");
        sb.append("    this.operationSubscription = undefined;\n");
        sb.append("    this.data.set(").append(defaultVal).append(");\n");
        sb.append("    this.loading.set(false);\n");
        sb.append("    this.error.set(undefined);\n");
        sb.append("    this.success.set(false);\n");
        if (gq.operationType() == NgGraphQL.OperationType.QUERY)
        {
            sb.append("    this.queryRef = undefined;\n");
            sb.append("    this.polling.set(false);\n");
        }
        sb.append("}");
        return sb.toString();
    }

    private String buildNgOnDestroyMethod()
    {
        return """
                ngOnDestroy(): void {
                    this.operationSubscription?.unsubscribe();
                }""";
    }

    // ── String escaping ────────────────────────────────────────────────

    /**
     * Escapes a string for safe inclusion in a TypeScript single-quoted string literal.
     */
    private String escapeTs(String value)
    {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    /**
     * Escapes a string for safe inclusion in a TypeScript template literal (gql`...`).
     */
    private String escapeTemplate(String value)
    {
        return value.replace("\\", "\\\\").replace("`", "\\`").replace("${", "\\${");
    }

    // ── Import resolution ──────────────────────────────────────────────

    @Override
    default List<NgImportReference> getAllImportAnnotations()
    {
        List<NgImportReference> out = IComponent.super.getAllImportAnnotations();
        NgGraphQL gq = getAnnotation();

        if (gq.responseType() != INgDataType.class)
        {
            @SuppressWarnings("unchecked")
            Class<? extends IComponent<?>> responseClass = (Class<? extends IComponent<?>>) (Class<?>) gq.responseType();
            NgComponentReference ref = AnnotationUtils.getNgComponentReference(responseClass);
            out.addAll(putRelativeLinkInMap(getClass(), ref));
        }

        return out;
    }

    @Override
    default String renderOnDestroyMethod()
    {
        // ngOnDestroy is rendered via the methods() list – suppress the default rendering
        return "";
    }
}

