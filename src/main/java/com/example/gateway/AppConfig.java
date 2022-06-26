package com.example.gateway;

import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.retry.annotation.EnableRetry;

@Configuration
@RefreshScope
public class AppConfig {
/*    @Bean
    ServiceInstanceListSupplier serviceInstanceListSupplier() {
        return new DemoServiceInstanceListSupplier("say-hello");
    }*/

    @Bean
    DiscoveryClient myDiscoverClient() {
        return new DemoDiscoveryClient();
    }

    @Bean
    ReactiveDiscoveryClient reactiveDiscoveryClient() {
        return new DemoReactiveDiscoveryClient();
    }

    @Bean
    public ServiceInstanceListSupplier discoveryClientServiceInstanceListSupplier(
            ConfigurableApplicationContext context) {
        return ServiceInstanceListSupplier.builder()
                .withDiscoveryClient()
                .withZonePreference()
                .withCaching()
                .withHealthChecks()
                .build(context);
    }
}
