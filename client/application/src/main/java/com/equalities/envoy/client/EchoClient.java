package com.equalities.envoy.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "Server", url = "http://echo-server")
public interface EchoClient {

  @GetMapping(path="/echo/{message}")
  public String echo(@PathVariable String message);
}