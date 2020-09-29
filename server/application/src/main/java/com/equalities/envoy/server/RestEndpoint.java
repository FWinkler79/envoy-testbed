package com.equalities.envoy.server;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class RestEndpoint {

  @GetMapping(path = "/echo/{message}")
  public String echoMessage(@PathVariable String message) {
    log.info("Received message {}. Echoing back...", message);
    return message;
  }
}
