// Run the Java suite first, then pass a directory containing Angular 22, Apollo Angular 14,
// Apollo Client 4, GraphQL 16, RxJS 7 and TypeScript 6 in node_modules.
import assert from 'node:assert/strict';
import {copyFileSync, mkdirSync, writeFileSync} from 'node:fs';
import {resolve, join} from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';
import {spawnSync} from 'node:child_process';
import {createRequire} from 'node:module';

assert.ok(process.argv[2], 'Pass the Angular 22 npm workspace path');
const workspace = resolve(process.argv[2]);
const generated = fileURLToPath(new URL('../../../target/angular22-generated/', import.meta.url));
const output = join(workspace, 'jwebmp-graphql-validation');
mkdirSync(output, {recursive: true});
writeFileSync(join(output, 'package.json'), '{"type":"module"}\n');
for (const name of ['UsersQuery', 'CreateOrderMutation', 'NotificationSubscription']) {
    copyFileSync(join(generated, name + '.ts'), join(output, name + '.ts'));
}
writeFileSync(join(output, 'tsconfig.json'), JSON.stringify({
    compilerOptions: {strict: true, target: 'ES2022', module: 'ES2022', moduleResolution: 'bundler',
        experimentalDecorators: true, skipLibCheck: true, outDir: 'dist'},
    angularCompilerOptions: {strictTemplates: true, strictInjectionParameters: true},
    include: ['*.ts']
}));
const require = createRequire(join(workspace, 'package.json'));
const ngc = join(workspace, 'node_modules', '@angular', 'compiler-cli', 'bundles', 'src', 'bin', 'ngc.js');
const result = spawnSync(process.execPath, [ngc, '-p', join(output, 'tsconfig.json')], {encoding: 'utf8'});
assert.equal(result.status, 0, result.stdout + result.stderr);
await import(pathToFileURL(require.resolve('@angular/compiler')).href);
const {createEnvironmentInjector, runInInjectionContext} = await import(pathToFileURL(require.resolve('@angular/core')).href);
const {Apollo} = await import(pathToFileURL(require.resolve('apollo-angular')).href);
const {Subject} = await import(pathToFileURL(require.resolve('rxjs')).href);
const query = new Subject(), mutation = new Subject(), subscription = new Subject();
const fakeApollo = {
    watchQuery: () => ({valueChanges: query, startPolling() {}, stopPolling() {}}),
    mutate: () => mutation,
    subscribe: () => subscription
};
const injector = createEnvironmentInjector([{provide: Apollo, useValue: fakeApollo}], null);
for (const [name, results, start] of [
    ['UsersQuery', query, null], ['CreateOrderMutation', mutation, 'mutate'],
    ['NotificationSubscription', subscription, null]
]) {
    const { [name]: Client } = await import(pathToFileURL(join(output, 'dist', name + '.js')).href);
    const client = runInInjectionContext(injector, () => new Client());
    if (start) client[start]();
    results.next({data: {value: 42}, loading: false});
    assert.equal(client.success(), true, name);
    assert.deepEqual(client.data(), {value: 42}, name);
    const error = new Error('GraphQL error');
    results.next({data: {value: 1}, error, loading: false});
    assert.equal(client.error(), error, name);
    assert.equal(client.success(), false, name);
    client.ngOnDestroy();
}
injector.destroy();
console.log('Angular 22 AOT and Apollo result handling passed for generated query, mutation and subscription services.');
