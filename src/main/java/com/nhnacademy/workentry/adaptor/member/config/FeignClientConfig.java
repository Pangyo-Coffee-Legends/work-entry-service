package com.nhnacademy.workentry.adaptor.member.config;


import com.nhnacademy.traceloggermodule.config.FeignTraceInterceptor;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignClientConfig {

    @Bean
    public RequestInterceptor feignTraceInterceptor() {
        return new FeignTraceInterceptor();
    }
}
