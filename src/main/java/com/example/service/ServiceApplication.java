package com.example.service;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@SpringBootApplication
public class ServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ServiceApplication.class, args);
    }

    @Bean
    ApplicationListener<ApplicationReadyEvent> readyEventApplicationListener(CustomerService service) {
        return e -> {
            service.findAll().forEach(System.out::println);
        };
    }

}

@Controller
@ResponseBody
class CustomerController {
    private final CustomerService service;

    private final ObservationRegistry registry;

    CustomerController(CustomerService service, ObservationRegistry registry) {
        this.service = service;
        this.registry = registry;
    }

    @GetMapping("/customers")
    Collection<Customer> all() {
        return this.service.findAll();
    }


    @GetMapping("/customers/{name}")
    Customer byName(@PathVariable String name) {
        Assert.state(Character.isUpperCase(name.charAt(0)), "The name must start with a capital");
        return Observation
                .createNotStarted("byName", this.registry)
                .observe(() -> service.byName(name));
    }
}

@Controller
@ResponseBody
class BracketsController {
    private final BracketService service;

    BracketsController(BracketService service) {
        this.service = service;
    }

    @GetMapping("/brackets")
    Collection<Bracket> all() {
        return this.service.findAll();
    }
}


@Service
class BracketService {
    private final JdbcTemplate template;
    private final RowMapper<Bracket> bracketRowMapper =
            (rs, i) -> new Bracket(rs.getInt("id"), rs.getString("name"), rs.getInt("customer_id"));

    BracketService(JdbcTemplate template) {
        this.template = template;
    }

    Bracket getBracketByCustomerId(long customerId) {
        return this.template.queryForObject("select * from brackets where customer_id = ?", this.bracketRowMapper, customerId);
    }

    Bracket byName(String name) {
        return this.template.queryForObject("select * from brackets where name = ?", this.bracketRowMapper, name);
    }

    Collection<Bracket> findAll() {
        return this.template.query("select * from brackets", (rs, i) -> new Bracket(rs.getInt("id"), rs.getString("name"), rs.getInt("customer_id")));
    }
}

@ControllerAdvice
class ErrorHandlingControllerAdvice {

    @ExceptionHandler
    ProblemDetail handleIllegalArgumentException(IllegalStateException exception) {
        var pd = ProblemDetail.forStatus(HttpStatusCode.valueOf(404));
        pd.setDetail("The name must start with a capital letter");
        return pd;
    }

}

@Service
class CustomerService {
    private final JdbcTemplate template;
    private final RowMapper<Customer> customerRowMapper =
            (rs, i) -> new Customer(rs.getInt("id"), rs.getString("name"));

    private final BracketService bracketService;

    CustomerService(JdbcTemplate template, BracketService bracketService) {
        this.template = template;
        this.bracketService = bracketService;
    }

    Bracket getCustomerBracket(long customerId) {
        return bracketService.getBracketByCustomerId(customerId);
    }

    Customer byName(String name) {
        return this.template.queryForObject("select * from customers where name = ?", this.customerRowMapper, name);
    }

    Collection<Customer> findAll() {
        return this.template.query("select * from customers", (rs, i) -> new Customer(rs.getInt("id"), rs.getString("name")));
    }
}

record Customer(Integer id, String name) {
}

record Bracket(Integer id, String name, int customerId) {
}
