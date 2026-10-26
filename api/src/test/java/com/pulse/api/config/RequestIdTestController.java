
package com.pulse.api.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RequestIdTestController {

    @GetMapping("/__test/request-id/boom")
    public String simulateFailure() {
        throw new RuntimeException("Intentional test failure");
    }
}
