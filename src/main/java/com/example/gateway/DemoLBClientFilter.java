package com.example.gateway;


import java.net.URI;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.CompletionContext;
import org.springframework.cloud.client.loadbalancer.DefaultRequest;
import org.springframework.cloud.client.loadbalancer.LoadBalancerLifecycle;
import org.springframework.cloud.client.loadbalancer.LoadBalancerLifecycleValidator;
import org.springframework.cloud.client.loadbalancer.LoadBalancerProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalancerUriTools;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.RequestData;
import org.springframework.cloud.client.loadbalancer.RequestDataContext;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.client.loadbalancer.ResponseData;
import org.springframework.cloud.gateway.config.GatewayLoadBalancerProperties;
import org.springframework.cloud.gateway.support.DelegatingServiceInstance;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.cloud.loadbalancer.core.ReactorLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_LOADBALANCER_RESPONSE_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_SCHEME_PREFIX_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.addOriginalRequestUrl;


@SuppressWarnings({ "rawtypes", "unchecked" })
@Component
public class DemoLBClientFilter implements GlobalFilter, Ordered {

    private static final Log log = LogFactory.getLog(org.springframework.cloud.gateway.filter.ReactiveLoadBalancerClientFilter.class);

    /**
     * Order of filter.
     */
    public static final int LOAD_BALANCER_CLIENT_FILTER_ORDER = 10150;

    private final LoadBalancerClientFactory clientFactory;

    private final GatewayLoadBalancerProperties properties;

    public DemoLBClientFilter(LoadBalancerClientFactory clientFactory,
                                            GatewayLoadBalancerProperties properties) {
        this.clientFactory = clientFactory;
        this.properties = properties;
    }

    @Override
    public int getOrder() {
        return LOAD_BALANCER_CLIENT_FILTER_ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        URI url = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
        String schemePrefix = exchange.getAttribute(GATEWAY_SCHEME_PREFIX_ATTR);
        if (url == null || (!"mylb".equals(url.getScheme()) && !"mylb".equals(schemePrefix))) {
            return chain.filter(exchange);
        }
        // preserve the original url
        addOriginalRequestUrl(exchange, url);

        if (log.isTraceEnabled()) {
            log.trace(org.springframework.cloud.gateway.filter.ReactiveLoadBalancerClientFilter.class.getSimpleName() + " url before: " + url);
        }

        URI requestUri = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
        String serviceId = requestUri.getHost();
        Set<LoadBalancerLifecycle> supportedLifecycleProcessors = LoadBalancerLifecycleValidator
                .getSupportedLifecycleProcessors(clientFactory.getInstances(serviceId, LoadBalancerLifecycle.class),
                        RequestDataContext.class, ResponseData.class, ServiceInstance.class);

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(httpHeaders -> httpHeaders.set("myheader", "value")).build();

        DefaultRequest<RequestDataContext> lbRequest = new DefaultRequest<>(
                new RequestDataContext(new RequestData(request), getHint(serviceId)));
        LoadBalancerProperties loadBalancerProperties = clientFactory.getProperties(serviceId);
        return choose(lbRequest, serviceId, supportedLifecycleProcessors).doOnNext(response -> {

                if (!response.hasServer()) {
                    supportedLifecycleProcessors.forEach(lifecycle -> lifecycle
                            .onComplete(new CompletionContext<>(CompletionContext.Status.DISCARD, lbRequest, response)));
                    throw NotFoundException.create(properties.isUse404(), "Unable to find instance for " + url.getHost());
                }

                ServiceInstance retrievedInstance = response.getServer();

                URI uri = exchange.getRequest().getURI();

                // if the `lb:<scheme>` mechanism was used, use `<scheme>` as the default,
                // if the loadbalancer doesn't provide one.
                String overrideScheme = retrievedInstance.isSecure() ? "https" : "http";
                if (schemePrefix != null) {
                    overrideScheme = url.getScheme();
                }

                DelegatingServiceInstance serviceInstance = new DelegatingServiceInstance(retrievedInstance,
                        overrideScheme);

                URI requestUrl = reconstructURI(serviceInstance, uri);

                if (log.isTraceEnabled()) {
                    log.trace("LoadBalancerClientFilter url chosen: " + requestUrl);
                }
                exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, requestUrl);
                exchange.getAttributes().put(GATEWAY_LOADBALANCER_RESPONSE_ATTR, response);
                supportedLifecycleProcessors.forEach(lifecycle -> lifecycle.onStartRequest(lbRequest, response));


            }).then(chain.filter(exchange))
            .doOnError(throwable -> supportedLifecycleProcessors.forEach(lifecycle -> lifecycle
                    .onComplete(new CompletionContext<ResponseData, ServiceInstance, RequestDataContext>(
                            CompletionContext.Status.FAILED, throwable, lbRequest,
                            exchange.getAttribute(GATEWAY_LOADBALANCER_RESPONSE_ATTR)))))
            .doOnSuccess(aVoid -> supportedLifecycleProcessors.forEach(lifecycle -> lifecycle
                    .onComplete(new CompletionContext<ResponseData, ServiceInstance, RequestDataContext>(
                            CompletionContext.Status.SUCCESS, lbRequest,
                            exchange.getAttribute(GATEWAY_LOADBALANCER_RESPONSE_ATTR), buildResponseData(exchange,
                            loadBalancerProperties.isUseRawStatusCodeInResponseData())))));
    }

    // from SetRequestHeaderGatewayFilterFactory
    private void addHeader(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(httpHeaders -> httpHeaders.set("myheader", "value")).build();

        //return chain.filter(exchange.mutate().request(request).build());


    }
    private ResponseData buildResponseData(ServerWebExchange exchange, boolean useRawStatusCodes) {
        if (useRawStatusCodes) {
            return new ResponseData(new RequestData(exchange.getRequest()), exchange.getResponse());
        }
        return new ResponseData(exchange.getResponse(), new RequestData(exchange.getRequest()));
    }

    protected URI reconstructURI(ServiceInstance serviceInstance, URI original) {
        return LoadBalancerUriTools.reconstructURI(serviceInstance, original);
    }

    private Mono<Response<ServiceInstance>> choose(Request<RequestDataContext> lbRequest, String serviceId,
                                                   Set<LoadBalancerLifecycle> supportedLifecycleProcessors) {
        ReactorLoadBalancer<ServiceInstance> loadBalancer = this.clientFactory.getInstance(serviceId,
                ReactorServiceInstanceLoadBalancer.class);
        if (loadBalancer == null) {
            throw new NotFoundException("No loadbalancer available for " + serviceId);
        }
        supportedLifecycleProcessors.forEach(lifecycle -> lifecycle.onStart(lbRequest));
        return loadBalancer.choose(lbRequest);
    }

    private String getHint(String serviceId) {
        LoadBalancerProperties loadBalancerProperties = clientFactory.getProperties(serviceId);
        Map<String, String> hints = loadBalancerProperties.getHint();
        String defaultHint = hints.getOrDefault("default", "default");
        String hintPropertyValue = hints.get(serviceId);
        return hintPropertyValue != null ? hintPropertyValue : defaultHint;
    }

}
