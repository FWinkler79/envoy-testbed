# Envoy-Testbed

This branch builds on top of branch `envoy-spring`.

It uses the same setup, but in contrast to the `envoy-spring` branch, the Spring Boot applications in this setup do not need to have an HTTP proxy explicitly configured. Instead, the logical names the Spring Boot applications use to address each other (i.e. `http://echo-server` and `http://echo-client`) are added a DNS aliases pointing to their respective Envoy proxies.

This makes the Envoy proxies transparent to the applications. 

Provided that the DNS aliases could be dynamically added at runtime (which might be supported Istio.io and Kubernetes), this would explain how Istio.io can inject the Envoy proxy sidecars and keep them completely transparent for applications.

# Setup

The setup is exactly the same as on branch `envoy-spring`, including the configurations of both Envoy instances.

The difference here is in the docker-compose.yaml (at the root of this project):

```yaml
services:
  server-envoy:
    container_name: server-envoy
    build:
      context: ./server/envoy
    ports:
    - "9002:9001"      # envoy admin port
    - "83:80"          # envoy egress port
    - "82:81"          # envoy ingress port
    #tty: true         # required to keep the container from exiting immediately.
    #privileged: true  # required so that routing tables can be manipulated.
    environment:
      - "ENVOY_UID=0"   # Run as root user (necessary since we bind to port 81)
    networks:
      envoy-sample-network:
        aliases: 
          - echo-client
  
  server:
    container_name: server
    image: sap.com/server:0.0.1-SNAPSHOT
    ports:
      - 8002:8002
    networks:
      envoy-sample-network:

  client-envoy:
    container_name: client-envoy
    build: 
      context: ./client/envoy
    ports:
    - "9001:9001"      # envoy admin port
    - "80:80"          # envoy egress port
    - "81:81"          # envoy ingress port
    #tty: true         # required to keep the container from exiting immediately.
    #privileged: true  # required so that routing tables can be manipulated.
    environment:
      - "ENVOY_UID=0"   # Run as root user (necessary since we bind to port 80)
    networks:
      envoy-sample-network:
        aliases: 
          - echo-server

  client:
    container_name: client
    image: sap.com/client:0.0.1-SNAPSHOT
    ports:
      - 8001:8001
    networks:
      envoy-sample-network:

# define the name of the network that Docker will 
# create for this project and add the containers to.
networks:
  envoy-sample-network:
```

Note that we have defined the name of the network (`envoy-sample-network`) that Docker will create at startup and join the containers to.

Also, we have assigned every of the containers to that network using the `networks` properties.

Finally, for the containers of `client-envoy` and `server-envoy` we have assigned network `aliases`. These are the alternative DNS names that a container within the network can use to address the respective Envoy instances.
Since the `client` application uses `http://echo-server` to communicate with the `server` application, but this request should be fired towards `client-envoy` proxy, we configured `client-envoy` proxy to be aliased as `echo-server`.

The same approach was used for the `server-envoy` which should be addressable as `echo-client` which is used by the `server` application to communicate with the `client` application.

As a result, without changing the applications, we could re-direct the HTTP requests performed with the logical name of the respective service to the sender's Envoy proxy. 

The (simplified) requests in this case, would look as follows:

```
GET /echo/HelloWorld HTTP/1.1
Host: echo-server
```

Note that the request line now contains a relative URL (only the path), since the HTTP client does not have a proxy configured.
The `Host` header is still set, however, and can be used by Envoy to match the request against a configured routing rule.

# How Could Istio.io Do It

Provided that every K8S pod had a local DNS server, that could be updated at runtime with new service aliases all pointing to the pod's Envoy side-car, Istio.io could use this mechanism to route all application traffic via the pod's Envoy proxy.
Using Envoy's management plane, i.e. changing Envoy proxy route configurations at runtime, Istio.io could react to joining or disappearing services at runtime - simply by changing routing configurations in the Envoy proxies and adjusting the DNS aliases in the local DNS servers.

This would be completely transparent to the applications.

However, this would mean that an application addresses every remote service by the same port (e.g. 80 as in this example).
That port would need to match the egress-port of the application's Envoy proxy.
DNS can only resolve host names, but it has no notion of the ports an application is using. For example, if the `client` application were using `http://echo-server:1234/echo/{message}` (note the port!), the approach sketched above would not work.
`echo-server` would be resolved to the `client-envoy` container, but since `client-envoy` does not use port `1234` as its egress port (actually does not expose it at all), the request would fail.

So Istio.io must use another mechanism than the one described above to keep the proxies transparent to the applications and at the same time overcome the port limitation described before.

It appears that Istio.io uses `iptables` instead. `iptables` is an administration tool for packet filtering and NAT.
It can be used to route / forward traffic based on IP addresses, including ports. In short, `iptables` can route all traffic from within a (pod) network to a specific gateway (e.g. an Envoy proxy), including port forwarding.

This is described in detail [here](https://jimmysong.io/en/blog/sidecar-injection-iptables-and-traffic-routing/).

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

**Istio.io**

* [How Istio does side-car injection and traffic hi-jacking](https://jimmysong.io/en/blog/sidecar-injection-iptables-and-traffic-routing/)