import com.guicedee.client.services.config.IGuiceScanModuleInclusions;
import com.jwebmp.angular.graphql.implementations.AngularGraphQLScanModule;

module com.jwebmp.angular.graphql {

    exports com.jwebmp.angular.graphql;
    exports com.jwebmp.angular.graphql.annotations;

    requires transitive com.jwebmp.core.angular;
    requires transitive com.jwebmp.core;
    requires transitive com.guicedee.client;

    provides IGuiceScanModuleInclusions with AngularGraphQLScanModule;

    opens com.jwebmp.angular.graphql to com.google.guice, com.jwebmp.core, com.jwebmp.core.angular, com.fasterxml.jackson.databind;
    opens com.jwebmp.angular.graphql.annotations to com.google.guice, com.jwebmp.core, com.jwebmp.core.angular, com.fasterxml.jackson.databind;
    opens com.jwebmp.angular.graphql.implementations to com.google.guice, com.jwebmp.core, com.jwebmp.core.angular, com.fasterxml.jackson.databind;
}

