# Envoy-Testbed

This branch shows a more elaborate sample of using Envoy to create a (very basic) service mesh.

The setup includes a two Spring Boot applications - `client` and `server` - that communicate with each other.
Both applications are behind a dedicated Envoy instance - much like is the case in an Istio.io service mesh.  
When the applications communicate with each other, they do so via their respective Envoy proxies.

The respective Envoy proxies are configured using static `envoy.yaml` configuration files.  
In these configurations, each Envoy proxy exposes an _ingress_ listener port (`http_ingress`) - used for communication from the remote to the local (i.e. proxied) Spring Boot application. Likewise, each Envoy proxy exposes an _egress_ listener port (`http_egress`) that is used by the proxied Spring Boot application to communicate with the remote application on the outside.

Both `client` and `server` Spring Boot applications provide the following REST endpoints:
* `/send/{message}` - endpoint used from a browser to make `client` or `server` send a message to its counterpart.
* `/echo/{message}` - endpoint used by `client` or `server` to send the message to its counterpart. The respective endpoint implementation will echo back the received message prefixed with `Client says: ` and `Server says: ` respectively to be able to distinguish who responded.

Within their containers, `client` runs and listens on port `8001`, `server` on port `8002`. These ports are also exposed via Docker to your Docker host machine, so you can call them from your browser directly. Note, however, that this is not required for the setup to work.

# Project Structure

```shell
startLandscape.sh       # script to start all components using docker-compose
stopLandscape.sh        # script to stop and remove all components using docker-compose
docker-compose.yaml     # the overall docker-compose project configuration to bring up all components. 
- client                # client application components
  | docker-compose.yaml # the docker-compose project configuration to bring up client and client-envoy only.
  |- application        # client Spring Boot application
  |- envoy              # client Envoy proxy
- server                # server application components
  | docker-compose.yaml # the docker-compose project configuration to bring up server and server-envoy only.
  |- application        # server Spring Boot application 
  |- envoy              # server Envoy proxy
```

Both Spring Boot applications are built using Maven and generate a Docker image named `sap.com/client:0.0.1-SNAPSHOT` and `sap.com/server:0.0.1-SNAPSHOT` respectively.

Both `client` and `server` contain a separate `docker-compose.yaml` file to bring up the Spring Boot application and Envoy proxy sidecar. 

❗Note: To run the sample, you need to start **all four compontents** (i.e. client application & proxy and server application & proxy) as a single `docker-compose` project. This is to make sure that all components are attached to the same (Docker-generated) network and are able to address each other. You can use the `docker-compose.yaml` file at the root of this project. See `startLandscape.sh` for an example of how to do that.

# Running the Sample

To run the sample, you need Docker installed. Execute the following steps to get things going:

1. `buildAll.sh` - to build `client` and `server` using Maven.
2. `startLandscape.sh` - build the Docker images and start all containers (of server and client).
3. `docker container ls` - to confirm that the containers are up and running

This will start the following containers:
* `client` - hosting the Spring Boot application of the client.
* `client-envoy` - hosting the Envoy proxy of the client application.
* `server` - hosting the Spring Boot application of the server.
* `server-envoy` - hosting the Envoy proxy of the server.

Once up and running, you can test the setup by doing the following:
1. Using your browser, send request to `http://localhost:81/send/HelloWorld`.  
   This sends a request to the ingress port (81) of the `client-envoy` proxy.  
   The proxy will forward the request to its downward `client` application on port `8001`.  
   The application will send a request to the `server`'s `/echo/{message}` endpoint via its own `client-envoy` proxy.  
   The `client-envoy` proxy receives the request and forwards it to the ingress port (81) of `server-envoy`, which will forward the request to its downward `server` application on port `8002`. The server's response will flow backwards in the same way.
2. As a result, you should see `Server says: HelloWorld`.

Note: you could also call `http://localhost:8001/send/HelloWorld` directly, thus bypassing the `client-envoy` ingress and communicating directly from your browser with the `client` application's `/send/{message}` endpoint.

To shut down the sample, use:
1. `stopLandscape.sh`
2. `docker container ls` - to confirm that the containers are gone.

❗Note: whenever you make changes to any of the `envoy.yaml` files you need to rebuild the Docker images using `docker-compose build`. The `startLandscape.sh` script makes sure this always happens.

# Explanation of the Setup

When you execute `startLandscape.sh` the following containers will be started:
* `client`
* `client-envoy`
* `server`
* `server-envoy`

These containers are joined to the same network, and hence can address each other by their container name!  
This is a Docker feature and makes building up a network of containers easy. All containers need to be part of the same `docker-compose` project however, i.e. declared in the same `docker-compose.yaml`.

The setup of the containers is as shown below:

![setup](./.documentation/setup.png)

The `client` application runs on port 8002 inside the Docker container.  
Envoy `client-envoy` exposes two ports: 
- port 80 is the egress port, which is used by the client application to communicate outbound.
- port 81 is the ingress port, which remote applications use to call into the `client` application via its proxy.

Likewise the same setup is chosen for the server-side.

Both applications `client` and `server` have an HTTP proxy explicitly configured which points to their respective Envoy proxy.
Note that this is different to an Istio.io setup, where the Envoy proxies are completely transparent to the applications they proxy.

Whenever, `client` communicates outbound, it sends requests to `http://echo-server` and `client-envoy` will proxy the request on to the `echo-server-cluster` which points to the ingress port (81) of `server-envoy`.
On the server side, `server-envoy` will then forward the incoming request to its downward `server` application on port `8002`.

Note that `client` uses `http://echo-server` as the URL to communicate with the server. This may be surprising, since `echo-server` can actually not be resolved as a DNS name on the Docker network. There, only `client`, `client-envoy`, `server-envoy` and `server` are available DNS names.

The reason why the request does NOT fail, however, is the proxy-configuration in the `client` application itself.
The proxy configuration in `client` points to the `client-envoy` proxy. So when `client` sends a request to `http://echo-server` it sends a request of the following (simplified) form:

```
GET http://echo-server/echo/HelloWorld HTTP/1.1
Host: echo-server
<some more headers>
```

This request will be sent (by the HTTP client stack) to `client-envoy`, and there the ingress listener matches the request against the configured `route_config`'s `virtual_hosts`. Since there is one virtual host defined, whose `domain` matches the `HOST` header of the request, the request is accepted by `client-envoy` and forwarded to the `cluster` configured for that `virtual_host`.

❗**Note:** this makes it possible to use purely _logical_ names for services (e.g. `echo-server`) and rely on the Envoy proxy infrastructure to do the proper "resolution" and routing. That's one piece of the magic introduced by Istio.io and the reason it is so popular. With Envoy's control plane APIs the currently static listener and route configurations can be dynamically adjusted at runtime, thus the mechanism above will even work as instances, services or entire clusters come and go.

# Implementation Details

## Spring Feign Client Proxy Configuration

### Using Apache HTTP Client Underneath OpenFeign

Both `client` and `server` Spring Boot applications use [Spring Cloud OpenFeign](https://cloud.spring.io/spring-cloud-openfeign/reference/html/) as their REST client.

Spring Cloud OpenFeign can use a variety of popular HTTP client underneath, but by defaul uses `java.net.URLConnection` for sending requests. To configure a proxy for Feign client to use, you need to configure it for the underlying HTTP client stack.

In this sample, we changed OpenFeign client's HTTP client stack from `java.net.URLConnection` to the Apache HTTP Client stack for OpenFeign. All you need to do to make this work, is to add the following to your Maven dependencies:

```xml
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>
<!-- Dependency to switch HttpClient implementation from java.net.URLConnection to Apache HTTP Client -->
<!-- See also: FeignAutoConfiguration for details. -->
<!-- See also: https://cloud.spring.io/spring-cloud-commons/reference/html/#http-clients -->
<!-- See also: https://cloud.spring.io/spring-cloud-openfeign/reference/html/#spring-cloud-feign-overriding-defaults -->
<dependency>
  <groupId>io.github.openfeign</groupId>
  <artifactId>feign-httpclient</artifactId>
</dependency>
```

Note that the first dependency adds Spring Cloud OpenFeign to your project in general, while the second one adds the dependency to Apache HTTP Client.

With Apache HTTP Client on the classpath, Spring Cloud OpenFeign's [`FeignAutoConfiguration`](https://github.com/spring-cloud/spring-cloud-openfeign/blob/master/spring-cloud-openfeign-core/src/main/java/org/springframework/cloud/openfeign/FeignAutoConfiguration.java) will wire the `FeignClient` together using this HTTP client stack.

If you wanted to use OkHttp Client instead, you would have to add the following as a dependency instead:

```xml
<dependency>
  <groupId>io.github.openfeign</groupId>
  <artifactId>feign-okhttp</artifactId>
</dependency>
```
Additionally, you would have to enable OkHttp client in you application.yaml like this:

```yaml
spring:
 cloud:
   httpclientfactories:
     ok:
       enabled: true
     apache:
       enabled: false

feign:
 okhttp:
   enabled: true
 httpclient:
   enabled: false
```

### Configuring the Proxy

Configuring the proxy is as easy as exposing a single bean like this:

```java
// see: https://cloud.spring.io/spring-cloud-commons/reference/html/#http-clients
@Bean
public HttpClientBuilder proxiedHttpClient() {
  String proxyHost   = "client-envoy";
  Integer proxyPort  = 80
  String proxyScheme = "http";

  return HttpClientBuilder.create()
                          .setProxy(new HttpHost(proxyHost, proxyPort, proxyScheme));
}
```
This is best described in the [Spring Cloud Commons HTTP Factories](https://cloud.spring.io/spring-cloud-commons/reference/html/#http-clients) section. You might also want to consult [Overriding Feign Defaults](https://cloud.spring.io/spring-cloud-openfeign/reference/html/#spring-cloud-feign-overriding-defaults) of the official [Spring Cloud OpenFeign documentation](https://cloud.spring.io/spring-cloud-openfeign/reference/html/).

With the bean configuration as given above, Spring Cloud OpenFeign will pick up the `HttpClientBuilder` that has the proxy configured and will inject it into the wiring of the Apache HTTP client underlying OpenFeign now.

❗Note, that for OkHttp client you need to use a similar approach using `OkHttpClient.Builder` instead of `HttpClientBuilder`. See the documentation referenced above.

## Envoy Configurations

In the following we will explain the configurations of the `client` application's Envoy instance. The explanation is equivalent for the `server` Envoy. We will focus on the relevant parts in `envoy.yaml`, for more details please consult [Envoy's extensive documentation](https://www.envoyproxy.io/docs/envoy/latest/api-v3/extensions/filters/network/http_connection_manager/v3/http_connection_manager.proto#envoy-v3-api-msg-extensions-filters-network-http-connection-manager-v3-httpconnectionmanager).

```yaml
...

static_resources:
  listeners:
  - name: http_egress             # for traffic from the client to the server.
    traffic_direction: OUTBOUND   # to give a hint to Envoy what this listener is used for.
    address:                      # the address and port this listener binds to.
      socket_address:
        address: 0.0.0.0
        port_value: 80
    filter_chains:                # The filters to apply when a request is coming on the configured port:
    - filters:
        # Name must match the qualified name as given here: 
        # https://www.envoyproxy.io/docs/envoy/latest/api-v3/extensions/filters/network/http_connection_manager/v3/http_connection_manager.proto#envoy-v3-api-msg-extensions-filters-network-http-connection-manager-v3-httpconnectionmanager
      - name: envoy.filters.network.http_connection_manager
        typed_config:
          # Use the typed API schema of HttpConnectionManager to describe the configuration.
          "@type": type.googleapis.com/envoy.extensions.filters.network.http_connection_manager.v3.HttpConnectionManager
          stat_prefix: client_egress_http  # a prefix used for statistics gathering.
          codec_type: AUTO
          route_config:
            name: client_envoy_routes
            virtual_hosts:
            # To test HTTP-based routing. Define a 'loopback' virtual host.
            # If the client sends a request to this Envoy instance with the 'Host' header 
            # set to 'loopback' this route will be chosen and the request is redirected
            # back to the client itself.
            - name: loopback      # This is just the name of the route. It has nothing to do with matching the request host.
              domains: 
                - "loopback"      # This is important, as this domain is matched against the Host header of the proxied request.
              routes:
              - match: 
                  prefix: "/"     # A path prefix that the request is matched by. Currently matches all paths.
                route:
                  host_rewrite_literal: client  # if this route matches, re-write the Host header to 'client'...
                  cluster: local                # ... then forward the request to the local cluster defined below.

            # To test HTTP-based routing. Define an 'echo-server' virtual host.
            # If the client sends a request to this Envoy instance with the 'Host' header 
            # set to 'envoy-server' this route will be chosen and the request is forwarded 
            # to the 'echo-server-cluster' defined below.
            - name: server        # This is just the name of the route. It has nothing to do with matching the request host.
              domains: 
                - "echo-server"   # This is important, as this domain is matched against the Host header of the proxied request.
              routes:
              - match: 
                  prefix: "/"     # A path prefix that the request is matched by. Currently matches all paths.
                route:
                  host_rewrite_literal: server   # if this route matches, re-write the Host header to 'server'...
                  cluster: echo-server-cluster   # ... then forward the request to the echo-server-cluster defined below.
          http_filters:
          - name: envoy.filters.http.router
    
  - name: http_ingress # for traffic from the server to the client.
    traffic_direction: INBOUND
    address:
      socket_address:
        address: 0.0.0.0
        port_value: 81
    filter_chains:
    - filters:
        # Name must match the qualified name as given here: 
        # https://www.envoyproxy.io/docs/envoy/latest/api-v3/extensions/filters/network/http_connection_manager/v3/http_connection_manager.proto#envoy-v3-api-msg-extensions-filters-network-http-connection-manager-v3-httpconnectionmanager
      - name: envoy.filters.network.http_connection_manager 
        typed_config:
          "@type": type.googleapis.com/envoy.extensions.filters.network.http_connection_manager.v3.HttpConnectionManager
          stat_prefix: client_ingress_http
          codec_type: AUTO
          route_config:
            name: route-to-client
            virtual_hosts:
            - name: client  # Route to the client. This is an arbitrary name. Requests are matched agains the domains below.
              domains: 
                - "*"       # Matches all Host header values. You could use this to block requests based on the requested host.
              routes:
              - match: 
                  prefix: "/"
                route:
                  host_rewrite_literal: client
                  cluster: local
          http_filters:
          - name: envoy.filters.http.router

  # Here the clusters / destinations are defined.
  # These clusters are referenced in the listener / route configurations above.
  clusters:
  - name: echo-server-cluster       # The cluster where the server is located.
    connect_timeout: 0.25s          # The connection timeout for reaching this cluster.
    type: LOGICAL_DNS               
    dns_lookup_family: V4_ONLY
    lb_policy: ROUND_ROBIN
    load_assignment:
      cluster_name: echo-server-cluster
      endpoints:                    # The entrance points to the cluster. Access to them will be loadbalanced.
      - lb_endpoints:
        - endpoint:
            address:
              socket_address:
                address: server-envoy # Address of server-envoy that the request will be forwarded to.
                port_value: 81        # Ingress port of server envoy.

  - name: local                     # The cluster where the client itself is located. Used for loopback route and ingress routing.
    connect_timeout: 0.25s          # The connection timeout for reaching this cluster.
    type: LOGICAL_DNS
    dns_lookup_family: V4_ONLY
    lb_policy: ROUND_ROBIN
    load_assignment:
      cluster_name: local
      endpoints:                    # The entrance points to the cluster. Access to them will be loadbalanced.
      - lb_endpoints:
        - endpoint:
            address:
              socket_address:
                address: client     # Address of client application.
                port_value: 8001    # Port of client application.
```

With the comments inline, the file should be pretty self-explanatory.
Note that the cluster configurations use the DNS names of the deployed components on the Docker-generated network, i.e. `client`, `client-envoy`, `server` and `server-envoy`.

The listener configuration's `domains` reference _logical_ service names (e.g `loopback` and `echo-server`) which are used to match against a request's `Host` header. If the `Host` header of a request matches the respective listener's `domain`, that route configuration is chosen to decide where to foward the request to.

# References

**Envoy**

* [Envoy Documentation by Version](https://www.envoyproxy.io/docs)
* [Envoy Latest Documentation](https://www.envoyproxy.io/docs/envoy/latest/)
* [Getting Started](https://www.envoyproxy.io/docs/envoy/latest/start/start)
* [Envoy Dynamic Configurations](https://www.envoyproxy.io/docs/envoy/latest/intro/arch_overview/operations/dynamic_configuration#arch-overview-dynamic-config)
* [Envoy Configuration Sources](https://www.envoyproxy.io/docs/envoy/latest/api-v2/api/v2/core/config_source.proto#envoy-api-field-core-configsource-api-config-source)
* [Envoy xDS Management Server API Definitions](https://www.envoyproxy.io/docs/envoy/latest/configuration/overview/xds_api#config-overview-management-server)
* [Envoy xDS Management Server API Reference Implementation - Java](https://github.com/envoyproxy/java-control-plane)
* [Envoy xDS Management Server API Reference Implementation - Go](https://github.com/envoyproxy/go-control-plane)
* [Envoy HTTP Connection Manager Filter](https://www.envoyproxy.io/docs/envoy/latest/api-v3/extensions/filters/network/http_connection_manager/v3/http_connection_manager.proto#envoy-v3-api-msg-extensions-filters-network-http-connection-manager-v3-httpconnectionmanager)
* [Envoy Deployment Types (Ingress, Egress, FrontProxy)](https://www.envoyproxy.io/docs/envoy/latest/intro/deployment_types/deployment_types)
* [Envoy Ciruit Breaking](https://www.envoyproxy.io/docs/envoy/latest/intro/arch_overview/upstream/circuit_breaking#arch-overview-circuit-break)
* [Envoy Network Filters (Reference)](https://www.envoyproxy.io/docs/envoy/latest/configuration/listeners/network_filters/network_filters)
* [Envoy HTTP Filters(Reference)](https://www.envoyproxy.io/docs/envoy/latest/configuration/http/http_filters/http_filters)
* [Envoy example configurations](https://www.envoyproxy.io/docs/envoy/latest/install/ref_configs#install-ref-configs)

**Spring Cloud OpenFeign**

* [Spring Cloud OpenFeign](https://cloud.spring.io/spring-cloud-openfeign/reference/html/)
* [Spring Cloud Commons HTTP Client Configurations](https://docs.spring.io/spring-cloud-commons/docs/2.2.4.RELEASE/reference/html/#http-clients)
* [FeignAutoConfiguration Class](https://github.com/spring-cloud/spring-cloud-openfeign/blob/master/spring-cloud-openfeign-core/src/main/java/org/springframework/cloud/openfeign/FeignAutoConfiguration.java)
* [OpenFeign HTTP Client (Apache HTTP Client-based)](https://mvnrepository.com/artifact/io.github.openfeign/feign-httpclient/9.3.1)
* [OpenFeign HTTP Client (OkHttp Client-based)](https://mvnrepository.com/artifact/io.github.openfeign/feign-okhttp/9.2.0)
