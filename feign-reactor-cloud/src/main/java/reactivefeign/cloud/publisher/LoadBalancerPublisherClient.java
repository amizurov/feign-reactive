package reactivefeign.cloud.publisher;

import org.reactivestreams.Publisher;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import reactivefeign.client.ReactiveHttpRequest;
import reactivefeign.publisher.PublisherHttpClient;
import reactor.core.publisher.Mono;

import java.net.URI;

/**
 * @author Sergii Karpenko
 */
public class LoadBalancerPublisherClient implements PublisherHttpClient {

    private final LoadBalancerClient loadBalancerClient;
    private String serviceName;
    private final PublisherHttpClient publisherClient;

    public LoadBalancerPublisherClient(LoadBalancerClient loadBalancerClient,
                                       String serviceName,
                                       PublisherHttpClient publisherClient) {
        this.loadBalancerClient = loadBalancerClient;
        this.serviceName = serviceName;
        this.publisherClient = publisherClient;
    }

    @Override
    public Publisher<Object> executeRequest(ReactiveHttpRequest request) {
        return Mono.defer(() -> Mono.just(this.loadBalancerClient.choose(serviceName)))
                .flatMap(serviceInstance -> {
                    if(serviceInstance == null) {
                        return Mono.from(publisherClient.executeRequest(request));
                    }

                    URI lbUrl = this.loadBalancerClient.reconstructURI(serviceInstance, request.uri());
                    ReactiveHttpRequest lbRequest = new ReactiveHttpRequest(request.method(), lbUrl, request.headers(), request.body());
                    return Mono.from(publisherClient.executeRequest(lbRequest));
                });
    }
}
