package com.tienda.pos.common;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public class NormalModeWebTestInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        // Apply after the setup environment processor; MVC slices need no installation config.
        TestPropertyValues.of("tienda.setup-mode=false").applyTo(context.getEnvironment());
    }
}
