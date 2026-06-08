package com.jwebmp.angular.graphql.implementations;

import com.guicedee.client.services.config.IGuiceScanModuleInclusions;

import java.util.Set;

/**
 * Registers the angular-graphql module for GuicedEE classpath scanning so that the
 * {@code @NgGraphQL} annotated client services are discovered and their TypeScript
 * is generated.
 */
public class AngularGraphQLScanModule implements IGuiceScanModuleInclusions<AngularGraphQLScanModule>
{
    @Override
    public Set<String> includeModules()
    {
        return Set.of("com.jwebmp.angular.graphql");
    }
}

