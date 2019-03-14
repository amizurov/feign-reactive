package reactivefeign.cloud;

import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.hystrix.exception.HystrixBadRequestException;
import feign.Contract;
import feign.MethodMetadata;
import feign.Target;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.netflix.ribbon.RibbonLoadBalancerClient;
import org.springframework.cloud.netflix.ribbon.SpringClientFactory;
import reactivefeign.FallbackFactory;
import reactivefeign.ReactiveFeignBuilder;
import reactivefeign.ReactiveOptions;
import reactivefeign.client.ReactiveHttpRequestInterceptor;
import reactivefeign.client.ReactiveHttpResponse;
import reactivefeign.client.log.ReactiveLoggerListener;
import reactivefeign.client.statushandler.ReactiveStatusHandler;
import reactivefeign.cloud.methodhandler.HystrixMethodHandlerFactory;
import reactivefeign.cloud.publisher.LoadBalancerPublisherClient;
import reactivefeign.methodhandler.MethodHandlerFactory;
import reactivefeign.publisher.PublisherClientFactory;
import reactivefeign.publisher.PublisherHttpClient;
import reactivefeign.retry.ReactiveRetryPolicy;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.BiFunction;
import java.util.function.Function;

import static reactivefeign.ReactiveFeign.Builder.retry;
import static reactivefeign.retry.FilteredReactiveRetryPolicy.notRetryOn;

/**
 * Allows to specify ribbon {@link LoadBalancerClient}
 * and HystrixObservableCommand.Setter with fallback factory.
 *
 * @author Sergii Karpenko
 */
public class CloudReactiveFeign {

    private static final Logger logger = LoggerFactory.getLogger(CloudReactiveFeign.class);

    public static <T> Builder<T> builder(ReactiveFeignBuilder<T> builder) {
        return new Builder<>(builder);
    }

    public static class Builder<T> implements ReactiveFeignBuilder<T> {

        private ReactiveFeignBuilder<T> builder;
        private boolean hystrixEnabled = true;
        private SetterFactory commandSetterFactory = new DefaultSetterFactory();
        private FallbackFactory<T> fallbackFactory;
        private LoadBalancerClient loadBalancerClient;
        private ReactiveRetryPolicy retryOnNextPolicy;

        protected Builder(ReactiveFeignBuilder<T> builder) {
            this.builder = builder;
        }

        public Builder<T> disableHystrix() {
            this.hystrixEnabled = false;
            return this;
        }

        public Builder<T> setHystrixCommandSetterFactory(SetterFactory commandSetterFactory) {
            this.commandSetterFactory = commandSetterFactory;
            return this;
        }

        public Builder<T> enableLoadBalancer(LoadBalancerClient loadBalancerClient){
            return enableLoadBalancer(loadBalancerClient, null);
        }

        public Builder<T> enableLoadBalancer(LoadBalancerClient loadBalancerClient, ReactiveRetryPolicy retryOnNextPolicy){
            this.loadBalancerClient = loadBalancerClient;
            this.retryOnNextPolicy = retryOnNextPolicy;
            return this;
        }

        @Override
        public Builder<T> fallback(T fallback) {
            return fallbackFactory(throwable -> fallback);
        }

        @Override
        public Builder<T> fallbackFactory(FallbackFactory<T> fallbackFactory) {
            this.fallbackFactory = fallbackFactory;
            return this;
        }

        @Override
        public ReactiveFeignBuilder<T> contract(Contract contract) {
            builder = builder.contract(contract);
            return this;
        }

        @Override
        public ReactiveFeignBuilder<T> options(ReactiveOptions options) {
            builder = builder.options(options);
            return this;
        }

        @Override
        public ReactiveFeignBuilder<T> addRequestInterceptor(ReactiveHttpRequestInterceptor requestInterceptor) {
            builder = builder.addRequestInterceptor(requestInterceptor);
            return this;
        }

        @Override
        public ReactiveFeignBuilder<T> addLoggerListener(ReactiveLoggerListener loggerListener) {
            builder = builder.addLoggerListener(loggerListener);
            return this;
        }

        @Override
        public ReactiveFeignBuilder<T> decode404() {
            builder = builder.decode404();
            return this;
        }

        @Override
        public ReactiveFeignBuilder<T> statusHandler(ReactiveStatusHandler statusHandler) {
            builder = builder.statusHandler(statusHandler);
            return this;
        }

        @Override
        public ReactiveFeignBuilder<T> responseMapper(BiFunction<MethodMetadata, ReactiveHttpResponse, ReactiveHttpResponse> responseMapper) {
            builder =  builder.responseMapper(responseMapper);
            return this;
        }

        @Override
        public ReactiveFeignBuilder<T> retryWhen(ReactiveRetryPolicy retryPolicy) {
            builder =  builder.retryWhen(notRetryOn(retryPolicy, HystrixBadRequestException.class));
            return this;
        }

        @Override
        public Contract contract() {
            return builder.contract();
        }

        @Override
        public MethodHandlerFactory buildReactiveMethodHandlerFactory(PublisherClientFactory reactiveClientFactory) {
            MethodHandlerFactory methodHandlerFactory = builder.buildReactiveMethodHandlerFactory(reactiveClientFactory);
            return hystrixEnabled
                    ? new HystrixMethodHandlerFactory(
					methodHandlerFactory,
                    commandSetterFactory,
                    (Function<Throwable, Object>) fallbackFactory)
                    : methodHandlerFactory;
        }

        @Override
        public PublisherClientFactory buildReactiveClientFactory() {
            PublisherClientFactory publisherClientFactory = builder.buildReactiveClientFactory();
            if(loadBalancerClient != null) {
                return new PublisherClientFactory() {

                    private Target target;

                    @Override
                    public void target(Target target) {
                        this.target = target;
                        publisherClientFactory.target(target);
                    }

                    @Override
                    public PublisherHttpClient create(MethodMetadata methodMetadata) {
                        PublisherHttpClient publisherClient = publisherClientFactory.create(methodMetadata);
                        String serviceName = extractServiceName(target.url());
                        LoadBalancerPublisherClient loadBalancerPublisherClient = new LoadBalancerPublisherClient(
                                loadBalancerClient, serviceName, publisherClient);
                        if(retryOnNextPolicy != null){
                            return retry(loadBalancerPublisherClient, methodMetadata,
                                    notRetryOn(retryOnNextPolicy, HystrixBadRequestException.class).toRetryFunction());
                        } else {
                            return loadBalancerPublisherClient;
                        }
                    }
                };
            } else {
                return publisherClientFactory;
            }
        }

        private String extractServiceName(String url){
            try {
                return new URI(url).getHost();
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("Can't extract service name from url:"+url, e);
            }
        }

    }

    public interface SetterFactory {
        HystrixObservableCommand.Setter create(Target<?> target, MethodMetadata methodMetadata);
    }

    public static class DefaultSetterFactory implements SetterFactory {
        @Override
        public HystrixObservableCommand.Setter create(Target<?> target, MethodMetadata methodMetadata) {
            String groupKey = target.name();
            String commandKey = methodMetadata.configKey();
            return HystrixObservableCommand.Setter
                    .withGroupKey(HystrixCommandGroupKey.Factory.asKey(groupKey))
                    .andCommandKey(HystrixCommandKey.Factory.asKey(commandKey));
        }
    }

}
