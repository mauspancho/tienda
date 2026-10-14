package com.tienda.pos.whatsapp;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties("tienda.whatsapp")
public class WhatsappProperties {
    private String masterKey = "";
    private List<String> allowedHosts = new ArrayList<>(List.of("127.0.0.1", "localhost"));
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(12);
    private long maxUploadBytes = 10 * 1024 * 1024;
    private long monitorDelayMs = 300000;

    public String getMasterKey() { return masterKey; }
    public void setMasterKey(String masterKey) { this.masterKey = masterKey; }
    public List<String> getAllowedHosts() { return allowedHosts; }
    public void setAllowedHosts(List<String> allowedHosts) { this.allowedHosts = allowedHosts; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration value) { this.connectTimeout = value; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration value) { this.readTimeout = value; }
    public long getMaxUploadBytes() { return maxUploadBytes; }
    public void setMaxUploadBytes(long value) { this.maxUploadBytes = value; }
    public long getMonitorDelayMs() { return monitorDelayMs; }
    public void setMonitorDelayMs(long value) { this.monitorDelayMs = value; }
}
