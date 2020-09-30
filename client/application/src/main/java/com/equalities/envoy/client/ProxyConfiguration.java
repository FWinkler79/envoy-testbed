package com.equalities.envoy.client;

import org.apache.http.HttpHost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.context.annotation.Bean;

public class ProxyConfiguration {
  @Bean
  public HttpClientBuilder proxiedHttpClient() {
    return HttpClientBuilder.create()
                            .setProxy(new HttpHost("client-envoy", 80, "http"));
  }
} 
