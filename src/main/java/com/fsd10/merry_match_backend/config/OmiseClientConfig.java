package com.fsd10.merry_match_backend.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import co.omise.Client;
import co.omise.ClientException;

@Configuration
@ConditionalOnProperty(prefix = "omise", name = "secret-key")
public class OmiseClientConfig {

    @Bean
    public Client omiseClient(OmiseProperties props) throws ClientException {
        return new Client.Builder()
                .publicKey(props.publicKey() != null ? props.publicKey() : "")
                .secretKey(props.secretKey())
                .build();
    }
}
