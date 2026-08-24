package org.project.loslite.controller;


import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/demo")
public class DemoController {
    @GetMapping("Testing")
    public String Testing() {
        String testing = "Testing";
//        System.out.println(testing);
        return testing;
    }
}
