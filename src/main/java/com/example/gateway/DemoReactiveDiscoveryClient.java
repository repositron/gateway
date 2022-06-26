package com.example.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import java.util.List;

@Component
public class DemoReactiveDiscoveryClient implements ReactiveDiscoveryClient {
    private static Logger log = LoggerFactory.getLogger(DemoReactiveDiscoveryClient.class);

    @Override
    public String description() {
        return "DemoReactiveDiscoveryClient";
    }

    @Override
    public Flux<ServiceInstance> getInstances(String serviceId) {
        return Flux.defer(() -> {

            log.info("DemoReactiveDiscoveryClient getInstances"); // this log line only is called once
            var serviceInstances = List.of(
                    new DefaultServiceInstance("say-hello" + "1", serviceId, "localhost", 8000, false),
                    new DefaultServiceInstance("say-hello"+ "2", serviceId, "localhost", 8001, false)
            );
            return Flux.fromIterable(serviceInstances);
        });
    }

    @Override
    public Flux<String> getServices() {
        return Flux.fromIterable(List.of("" +
                "say-hello"));

    }
}
