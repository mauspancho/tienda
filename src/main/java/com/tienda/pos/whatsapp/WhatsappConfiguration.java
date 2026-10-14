package com.tienda.pos.whatsapp;

import com.tienda.pos.common.NormalMode;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@NormalMode
@EnableConfigurationProperties(WhatsappProperties.class)
public class WhatsappConfiguration { }
