package com.tienda.pos.common;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.Duration;

@Controller
@NormalMode
public class FaviconController {

    @GetMapping("/favicon.ico")
    public ResponseEntity<Void> favicon() {
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)))
                .build();
    }
}