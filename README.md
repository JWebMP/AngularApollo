# JWebMP Angular Apollo GraphQL

[![Maven Central](https://img.shields.io/maven-central/v/com.jwebmp.plugins/angular-graphql)](https://central.sonatype.com/artifact/com.jwebmp.plugins/angular-graphql)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue)](https://www.apache.org/licenses/LICENSE-2.0)

![Java 25+](https://img.shields.io/badge/Java-25%2B-green)
![Modular](https://img.shields.io/badge/Modular-JPMS-green)
![Angular](https://img.shields.io/badge/Angular-21-DD0031?logo=angular)
![TypeScript](https://img.shields.io/badge/TypeScript-5-3178C6?logo=typescript)

<!-- Tech icons row -->
![Apollo](https://img.shields.io/badge/apollo--angular-11-311C87?logo=apollographql)
![GraphQL](https://img.shields.io/badge/GraphQL-16-E10098?logo=graphql)
![JWebMP](https://img.shields.io/badge/JWebMP-2.0-0A7)

Apollo GraphQL client generation for JWebMP. Annotate a Java class with `@NgGraphQL`, declare the GraphQL operation, and the compiler emits a fully typed, signal-based `@Injectable` Angular service backed by [`apollo-angular`](https://the-guild.dev/graphql/apollo-angular) — no handwritten TypeScript. One annotated class per operation (query, mutation, or subscription), mirroring the `@NgRestClient` pattern.

Built on [apollo-angular 11](https://the-guild.dev/graphql/apollo-angular) · [Angular 21](https://angular.dev/) · [JWebMP Core](https://jwebmp.com/) · JPMS module `com.jwebmp.angular.graphql` · Java 25+

## 📦 Installation

```xml
<dependency>
  <groupId>com.jwebmp.plugins</groupId>
  <artifactId>angular-graphql</artifactId>
</dependency>
```

> Version is managed by the `com.jwebmp:jwebmp-bom` imported in the parent POM.

### NPM Dependencies

The plugin declares the Apollo dependencies automatically via `@TsDependency`:

```json
{
  "dependencies": {
    "apollo-angular": "^11.0.0",
    "@apollo/client": "^4.0.0",
    "graphql": "^16.9.0"
  }
}
```

## ✨ Features

- **`@NgGraphQL` annotation** — One annotation per GraphQL operation generates a complete `@Injectable` Angular service at build time
- **Query / Mutation / Subscription** — `operationType` selects the trigger method: `execute()`, `mutate()`, or `subscribe()`
- **Signal-based state** — Every service exposes `data()`, `loading()`, `error()`, and `success()` as Angular `WritableSignal`s
- **`gql` document embedding** — The GraphQL operation text is embedded into a `gql` template literal in the generated service
- **Default variables** — `@NgGraphQLVariable` declares default operation variables merged with call-time variables
- **Polling & refetch** — Queries support `startPolling()` / `stopPolling()` and `refetch()` via Apollo's `watchQuery`
- **Fetch & error policies** — Configure Apollo `FetchPolicy` and `ErrorPolicy` per operation
- **Typed responses** — Bind a `responseType` (`INgDataType`) and `responseArray` for typed signal results
- **Automatic cleanup** — Generated `ngOnDestroy` and `DestroyRef` registration unsubscribe on teardown
- **JPMS modular** — Registers itself for GuicedEE scanning; new client classes need no extra registration

## 🚀 Quick Start

Implement `INgGraphQLClient<Self>` and annotate the class with `@NgGraphQL`. The class body is empty — the
TypeScript is generated entirely from the annotation.

### Query

```java
@NgGraphQL(
    operation = """
        query Users($active: Boolean) {
          users(active: $active) { id name email }
        }
        """,
    operationType = NgGraphQL.OperationType.QUERY,
    fetchOnCreate = true,
    pollingEnabled = true,
    pollingIntervalMs = 10_000)
@NgGraphQLVariable(name = "active", value = "true")
public class UsersQuery implements INgGraphQLClient<UsersQuery> { }
```

### Mutation

```java
@NgGraphQL(
    operation = """
        mutation CreateOrder($input: OrderInput!) {
          createOrder(input: $input) { id status }
        }
        """,
    operationType = NgGraphQL.OperationType.MUTATION,
    errorPolicy = NgGraphQL.ErrorPolicy.ALL)
public class CreateOrderMutation implements INgGraphQLClient<CreateOrderMutation> { }
```

### Subscription

```java
@NgGraphQL(
    operation = """
        subscription OnNotification {
          notificationAdded { id message }
        }
        """,
    operationType = NgGraphQL.OperationType.SUBSCRIPTION,
    responseArray = true,
    fetchOnCreate = true)
public class NotificationSubscription implements INgGraphQLClient<NotificationSubscription> { }
```

### Consume the generated service

```java
@NgComponent("app-users")
public class UsersComponent extends DivSimple<UsersComponent>
        implements INgComponent<UsersComponent> {
    // inject UsersQuery in the generated component and read usersQuery.data() / loading() / error()
}
```

## 🧩 Generated Output

For the `UsersQuery` example above, the plugin produces an Angular service with this layout:

```typescript
import { Injectable } from '@angular/core';
import { inject } from '@angular/core';
import { signal, WritableSignal } from '@angular/core';
import { DestroyRef } from '@angular/core';
import { OnDestroy } from '@angular/core';
import { Apollo, gql } from 'apollo-angular';
import { Subscription } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class UsersQuery implements OnDestroy
{
    private readonly apollo = inject(Apollo);
    private readonly destroyRef = inject(DestroyRef);
    readonly data: WritableSignal<any | undefined> = signal<any | undefined>(undefined);
    readonly loading: WritableSignal<boolean> = signal<boolean>(false);
    readonly error: WritableSignal<any> = signal<any>(undefined);
    readonly success: WritableSignal<boolean> = signal<boolean>(false);
    private readonly document = gql`
query Users($active: Boolean) {
  users(active: $active) { id name email }
}
`;
    private operationSubscription?: Subscription;
    private queryRef?: any;
    private pollingIntervalMs = 10000;
    readonly polling: WritableSignal<boolean> = signal<boolean>(false);

    constructor()
    {
        this.destroyRef.onDestroy(() => {
            this.operationSubscription?.unsubscribe();
        });
        this.execute();
    }

    private mergeVariables(variables?: Record<string, any>): Record<string, any> {
        const defaults: Record<string, any> = {
            'active': true,
        };
        return { ...defaults, ...(variables ?? {}) };
    }

    execute(variables?: Record<string, any>): void {
        this.loading.set(true);
        this.error.set(undefined);
        this.success.set(false);

        this.operationSubscription?.unsubscribe();
        this.queryRef = this.apollo.watchQuery<any | undefined>({
            query: this.document,
            variables: this.mergeVariables(variables),
            fetchPolicy: 'cache-first',
            errorPolicy: 'none',
            pollInterval: this.pollingIntervalMs,
        });

        this.operationSubscription = this.queryRef.valueChanges.subscribe({
            next: (result: any) => this.handleResult(result),
            error: (err: any) => { /* ... */ }
        });
        this.polling.set(true);
    }

    refetch(variables?: Record<string, any>): void { /* ... */ }
    startPolling(intervalMs?: number): void { /* ... */ }
    stopPolling(): void { /* ... */ }
    private handleResult(result: any): void { /* sets data/loading/error/success */ }
    reset(): void { /* resets signals + queryRef */ }
    ngOnDestroy(): void { this.operationSubscription?.unsubscribe(); }
}
```

The generated service is written under the Angular app's `src/app/<package>/<ClassName>/` directory by the
Angular Maven plugin, alongside other generated components and services.

## ⚙️ @NgGraphQL Attributes

| Attribute | Default | Purpose |
|---|---|---|
| `operation` | (required) | Raw GraphQL document; embedded into a `gql` template literal |
| `operationType` | `QUERY` | `QUERY`, `MUTATION`, or `SUBSCRIPTION` |
| `value` | `""` | Optional friendly service name |
| `responseType` | `INgDataType.class` (→ `any`) | Typed result class; imported automatically |
| `responseArray` | `false` | Result is an array of `responseType` |
| `singleton` | `true` | `providedIn: 'root'` vs `'any'` |
| `fetchOnCreate` | `false` | Auto-run on inject (QUERY → `execute()`, SUBSCRIPTION → `subscribe()`; never mutations) |
| `pollingEnabled` | `false` | Query polling via Apollo `pollInterval` |
| `pollingIntervalMs` | `30000` | Polling interval in ms |
| `fetchPolicy` | `CACHE_FIRST` | `CACHE_FIRST`, `CACHE_AND_NETWORK`, `NETWORK_ONLY`, `CACHE_ONLY`, `NO_CACHE`, `STANDBY` |
| `errorPolicy` | `NONE` | `NONE`, `IGNORE`, `ALL` |

`@NgGraphQLVariable(name, value)` is repeatable. The `value` is emitted as a raw TypeScript expression
(e.g. `"true"`, `"42"`, `"'active'"`). Defaults merge with call-time variables (call-time wins).

## 🧪 Generated Service API

All operation types expose the `data()`, `loading()`, `error()`, `success()` signals plus `reset()` and
`ngOnDestroy`. The trigger method depends on `operationType`:

| Operation | Trigger | Extra methods | Apollo call |
|---|---|---|---|
| `QUERY` | `execute(variables?)` | `refetch()`, `startPolling()`, `stopPolling()`, `polling` signal | `apollo.watchQuery` |
| `MUTATION` | `mutate(variables?)` | — (never auto-executes) | `apollo.mutate` |
| `SUBSCRIPTION` | `subscribe(variables?)` | — | `apollo.subscribe` |

## 🗺️ JPMS Module

```
module com.jwebmp.angular.graphql {
    requires transitive com.jwebmp.core.angular;
    requires transitive com.jwebmp.core;
    requires transitive com.guicedee.client;

    exports com.jwebmp.angular.graphql;
    exports com.jwebmp.angular.graphql.annotations;

    provides IGuiceScanModuleInclusions with AngularGraphQLScanModule;
}
```

The plugin registers `com.jwebmp.angular.graphql` for GuicedEE classpath scanning via
`AngularGraphQLScanModule`. Client classes only need to live in a scanned module — no per-client registration.

### Key Classes

| Class | Role |
|---|---|
| `NgGraphQL` | Annotation declaring the operation, type, response, policies, polling |
| `NgGraphQLVariable` / `NgGraphQLVariables` | Repeatable default operation variables |
| `INgGraphQLClient<J>` | Generator interface — renders the apollo-angular service from the annotation |
| `AngularGraphQLScanModule` | `IGuiceScanModuleInclusions` registering the module for scanning |

## 🔗 Dependencies

```
com.jwebmp.angular.graphql
 ├── com.jwebmp.core.angular              (Angular integration + TypeScript compiler)
 ├── com.jwebmp.core.base.angular.client  (TypeScript client — annotations, IComponent, INgDataType)
 ├── com.jwebmp.core                      (JWebMP Core)
 ├── com.jwebmp.vertx                     (JWebMP Vert.x bridge)
 └── com.guicedee.client                  (Guice DI + classpath scanning)
```

## 🛠️ Build

- **Java**: 25 LTS
- **Maven**: inherits `com.jwebmp:parent:2.0.3-SNAPSHOT`
- **JPMS**: module descriptor at `src/main/java/module-info.java`

```bash
mvn clean install
```

Run tests:

```bash
mvn test
```

## 📄 License

[Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0)

---

**JWebMP Angular Apollo GraphQL** — typed, signal-based Apollo services for Java web applications, generated from a single annotation.
