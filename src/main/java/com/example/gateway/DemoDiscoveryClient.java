package com.example.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

//@Component

public class DemoDiscoveryClient implements DiscoveryClient {
    private static Logger log = LoggerFactory.getLogger(DemoDiscoveryClient.class);
    @Override
    public String description() {
        return "say-hello-DemoDiscoveryClient";
    }

    @Override
    public List<ServiceInstance> getInstances(String serviceId) {
        log.debug("DemoDiscoveryClient getInstances");
        return Arrays
                .asList(new DefaultServiceInstance("say-hello" + "1", serviceId, "localhost", 8000, false),
                        new DefaultServiceInstance("say-hello"+ "2", serviceId, "localhost", 9092, false),
                        new DefaultServiceInstance("say-hello" + "3", serviceId, "localhost", 9999, false));

    }

    @Override
    public List<String> getServices() {
        return List.of("say-hello");
    }
}

