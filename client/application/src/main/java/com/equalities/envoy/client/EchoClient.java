package com.equalities.envoy.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "EchoServiceClient", url = "http://localhost")
public interface EchoClient {

  @GetMapping(path="/echo/{message}" )
  public String echo(@PathVariable String message);
}
