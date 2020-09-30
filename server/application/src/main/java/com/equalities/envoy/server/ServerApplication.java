package com.equalities.envoy.server;

import org.apache.http.HttpHost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;

@EnableFeignClients
@SpringBootApplication
public class ServerApplication {
  public static void main(String[] args) {
    SpringApplication.run(ServerApplication.class, args);
  }

  // see: https://cloud.spring.io/spring-cloud-commons/reference/html/#http-clients
  @Bean
  public HttpClientBuilder proxiedHttpClient(@Value("${com.equalities.feign.proxy.host}") String proxyHost,
                                             @Value("${com.equalities.feign.proxy.port}") Integer proxyPort,
                                             @Value("${com.equalities.feign.proxy.scheme}") String proxyScheme) {
    return HttpClientBuilder.create()
                            .setProxy(new HttpHost(proxyHost, proxyPort, proxyScheme));
  }
}
