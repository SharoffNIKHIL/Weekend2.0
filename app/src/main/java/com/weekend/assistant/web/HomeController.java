package com.weekend.assistant.web;

import com.weekend.assistant.workspace.HomeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Home screen: category counts, folders and what's up next. */
@RestController
@RequestMapping("/api")
public class HomeController {

    private final HomeService home;

    public HomeController(HomeService home) {
        this.home = home;
    }

    @GetMapping("/home")
    public HomeService.Home home() {
        return home.home();
    }
}
