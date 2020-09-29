package com.equalities.envoy.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class RestEndpoint {
  @Autowired
  EchoClient echoServiceClient;

  @GetMapping(path = "/send/{message}")
  public String callServer(@PathVariable String message) {
    log.info("Sending message '{}' to client's echo endpoint.", message);
    String echo = echoServiceClient.echo(message);
    return echo + "<br>";
  }
  
  @GetMapping(path = "/echo/{message}")
  public String serverEcho(@PathVariable String message) {
    log.info("Server received message '{}' from client. Echoing back...", message);
    return "Server says: " + message;
  }
}
