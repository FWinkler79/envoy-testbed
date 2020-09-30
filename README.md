# Envoy-Testbed

This branch shows a very simple Envoy setup that includes a traffic shifting scenario.

The setup includes a single Envoy instance that is run from a Docker container and proxies requests from localhost to either Google.com or Bing.com. Based on a traffic shifting configuration in `envoy.yaml` 50% of all outgoing requests are routed to Google.com, the other 50% to Bing.com.

Currently this traffic shifting configuration is statically configured, but when the Envoy management plane APIs (basically REST or gRPC APIs) are used, this information can be adjusted dynamically, allowing gradually shifting an entire user-base from one service version to another.

All configuration is done statically in `envoy.yaml` and injected into the Docker image using a Docker build (see `Dockerfile`).
Of course, the configuration file could also be mapped into the Docker container, not requiring an explicit Docker image to be built everytime the configuration changes.

# Running the Sample

To run the sample, you need Docker installed. Execute the following steps to get things going:

1. `docker-compose build` - build the Docker image
2. `docker-compose up -d` - run the Docker container hosting envoy (as a daemon in background)
3. `docker container ls` - to confirm that the container is up and running.

Once up and running, you can send requests to `http://localhost:10000` and will be proxied to `www.google.com` or `www.bing.com`.

To shut down envoy, use:
1. `docker-compose down`
2. `docker container ls` - to confirm that the container is gone.

❗Note: whenever you make changes to `envoy.yaml` you need to rebuild the Docker image using `docker-compose build`!

# References

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
