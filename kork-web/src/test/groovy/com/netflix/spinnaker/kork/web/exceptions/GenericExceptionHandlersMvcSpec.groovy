package com.netflix.spinnaker.kork.web.exceptions

import ch.qos.logback.classic.spi.ILoggingEvent
import com.netflix.spinnaker.config.ErrorConfiguration
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.read.ListAppender;
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import spock.lang.Specification

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestControllersConfiguration)
class GenericExceptionHandlersMvcSpec extends Specification {

  @LocalServerPort
  int port

  @Autowired
  TestRestTemplate restTemplate

  private ListAppender<ILoggingEvent> listAppender;

  def setup() {
    Logger logger = (Logger) LoggerFactory.getLogger(GenericExceptionHandlers);
    listAppender = new ListAppender<>();
    listAppender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
    logger.addAppender(listAppender);
    listAppender.start();
  }

  def "ok request"() {
    when:
    def entity = restTemplate.getForEntity("http://localhost:$port/test-controller", HashMap)

    then: "status is 200"
    entity.statusCode == HttpStatus.OK
  }

  def "not found"() {
    when:
    def entity = restTemplate.getForEntity("http://localhost:$port/knock-knock", HashMap)

    then: "status is 404"
    entity.statusCode == HttpStatus.NOT_FOUND
  }

  def "method not supported"() {
    when:
    def entity = restTemplate.postForEntity("http://localhost:$port/test-controller", null, HashMap)

    then: "status is 405"
    entity.statusCode == HttpStatus.METHOD_NOT_ALLOWED
  }

  def "missing request param"() {
    when:
    def entity = restTemplate.getForEntity("http://localhost:$port/test-controller/path1", HashMap.class)

    then: "status is 400"
    entity.statusCode == HttpStatus.BAD_REQUEST
  }

  def "should map IllegalArgumentException as 400"() {
    when:
    def entity = restTemplate.getForEntity("http://localhost:$port/test-controller/illegalArgumentException", HashMap.class)

    then: "status is 400"
    entity.statusCode == HttpStatus.BAD_REQUEST
  }

  def "should handle IllegalStateException"() {
    when:
    def entity = restTemplate.getForEntity("http://localhost:$port/test-controller/illegalStateException", HashMap.class)

    then: "status is 500"
    entity.statusCode == HttpStatus.INTERNAL_SERVER_ERROR

    and: "should log an error, but no warnings"
    countLogEventsForLevel(Level.ERROR) == 1
    countLogEventsForLevel(Level.WARN) == 0
  }

  private int countLogEventsForLevel(Level level) {
    return listAppender.list.findAll { event ->
      event.getLevel() == level
      }.size();
  }

  @Import(ErrorConfiguration)
  @Configuration
  @EnableAutoConfiguration
  static class TestControllersConfiguration {

    @Configuration
    @EnableWebSecurity
    class WebSecurityConfig extends WebSecurityConfigurerAdapter {
      @Override
      protected void configure(HttpSecurity http) throws Exception {
        http.csrf().disable()
          .headers().disable()
          .authorizeRequests().anyRequest().permitAll()
      }
    }

    @Bean
    TestController testController() {
      return new TestController()
    }

  }

  @RestController
  @RequestMapping("/test-controller")
  static class TestController {

    @GetMapping
    void get() {
    }

    @GetMapping("/path1")
    void get(@RequestParam("param") String param) {
    }

    @GetMapping("/illegalArgumentException")
    void illegalArgumentException() {
      throw new IllegalArgumentException()
    }

    @GetMapping("/illegalStateException")
    void illegalStateException() {
      throw new IllegalStateException()
    }
  }
}
