package com.equalities.envoy.server;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "Client", url = "http://localhost:83")
public interface EchoClient {

  @GetMapping(path="/echo/{message}" )
  public String echo(@PathVariable String message);
}
