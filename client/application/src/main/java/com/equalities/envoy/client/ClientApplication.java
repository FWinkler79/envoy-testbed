package com.equalities.envoy.client;

import org.apache.http.HttpHost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;

@EnableFeignClients
@SpringBootApplication
public class ClientApplication {
  public static void main(String[] args) {
    SpringApplication.run(ClientApplication.class, args);
  }
  
  @Bean
  public HttpClientBuilder proxiedHttpClient() {
    return HttpClientBuilder.create()
                            .setProxy(new HttpHost("client-envoy", 80, "http"));
  }
}
