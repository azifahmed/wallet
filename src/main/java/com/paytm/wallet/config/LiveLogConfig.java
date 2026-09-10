package com.paytm.wallet.config;

import com.paytm.wallet.observability.LiveLogBuffer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LiveLogConfig {

    @Bean
    public LiveLogBuffer liveLogBuffer() {
        return LiveLogBuffer.getInstance();
    }
}
