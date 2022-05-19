package com.ozangunalp;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

import javax.inject.Inject;

import org.keycloak.adapters.installed.KeycloakInstalled;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openshift.cloud.api.kas.DefaultApi;
import com.openshift.cloud.api.kas.invoker.ApiClient;
import com.openshift.cloud.api.kas.invoker.ApiException;
import com.openshift.cloud.api.kas.invoker.Configuration;
import com.openshift.cloud.api.kas.invoker.auth.HttpBearerAuth;
import com.openshift.cloud.api.kas.models.KafkaRequest;
import com.openshift.cloud.api.kas.models.KafkaRequestList;
import com.openshift.cloud.api.kas.models.KafkaRequestPayload;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "rhoas", mixinStandardHelpOptions = true)
public class RhoasCommand implements Runnable {

    @Inject
    ObjectMapper objectMapper;

    private static final Duration MIN_TOKEN_VALIDITY = Duration.ofSeconds(30);

    @Parameters(paramLabel = "tokens-file", description = "File for storing obtained tokens.", defaultValue = "C:\\tokens.json")
    Path tokensPath;

    @Override
    public void run() {
        ApiClient defaultClient = getApiClient();

        DefaultApi apiInstance = new DefaultApi(defaultClient);
        Boolean async = true; // Boolean | Perform the action in an asynchronous manner
        KafkaRequestPayload kafkaRequestPayload = new KafkaRequestPayload(); // KafkaRequestPayload | Kafka data
        kafkaRequestPayload.setName("my-kafka-instance3");
        try {
            KafkaRequest resultKafkaCreate = apiInstance.createKafka(async, kafkaRequestPayload);
            System.out.println(resultKafkaCreate);
            KafkaRequestList result = apiInstance.getKafkas(null, null, "created_at", "");
            System.out.println(result);
        } catch (ApiException e) {
            System.err.println("Exception when calling DefaultApi#createKafka");
            System.err.println("Status code: " + e.getCode());
            System.err.println("Reason: " + e.getResponseBody());
            System.err.println("Response headers: " + e.getResponseHeaders());
            e.printStackTrace();
        }
    }

    /**
     * From https://github.com/redhat-developer/app-services-sdk-java/tree/main/packages/kafka-management-sdk#getting-started
     */
    private ApiClient getApiClient() {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://api.openshift.com");

        String tokenString = getBearerToken();

        // Configure HTTP bearer authorization: Bearer
        HttpBearerAuth Bearer = (HttpBearerAuth) defaultClient.getAuthentication("Bearer");
        Bearer.setBearerToken(tokenString);
        return defaultClient;
    }

    /**
     * From https://www.keycloak.org/docs/latest/securing_apps/#_installed_adapter
     */
    private String getBearerToken() {
        try {
            // reads the configuration from classpath: META-INF/keycloak.json
            KeycloakInstalled keycloak = new KeycloakInstalled();
            // TODO set resteasyclient depending on the platform
            // keycloak.setResteasyClient();

            RhoasTokens storedTokens = getStoredTokenResponse();

            // ensure token is valid for at least 30 seconds
            if (storedTokens != null && storedTokens.accessTokenIsValidFor(MIN_TOKEN_VALIDITY)) {
                return storedTokens.access_token;
            } else if (storedTokens != null && storedTokens.refreshTokenIsValidFor(MIN_TOKEN_VALIDITY)) {
                keycloak.refreshToken(storedTokens.refresh_token);
                storeTokenResponse(keycloak);
                return keycloak.getTokenString();
            } else {
                // opens desktop browser
                // TODO this will create a callback server on localhost on a random port using Undertow. We may need to change that to a vertx server.
                keycloak.loginDesktop();
                storeTokenResponse(keycloak);
                return keycloak.getTokenString();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    RhoasTokens storeTokenResponse(KeycloakInstalled keycloak) throws IOException {
        RhoasTokens rhoasTokens = new RhoasTokens();
        rhoasTokens.refresh_token = keycloak.getRefreshToken();
        rhoasTokens.access_token = keycloak.getTokenString();
        long timeMillis = System.currentTimeMillis();
        rhoasTokens.refresh_expiration = timeMillis + keycloak.getTokenResponse().getRefreshExpiresIn() * 1000;
        rhoasTokens.access_expiration = timeMillis + keycloak.getTokenResponse().getExpiresIn() * 1000;
        // objectMapper.writeValue(tokensPath.toFile(), rhoasTokens);
        System.out.println(rhoasTokens.access_token);
        return rhoasTokens;
    }

    RhoasTokens getStoredTokenResponse() {
        try {
            return objectMapper.readValue(tokensPath.toFile(), RhoasTokens.class);
        } catch (Exception e) {
            return null;
        }
    }

    public static void main(String[] args) {
        RhoasCommand rhoasCommand = new RhoasCommand();
        rhoasCommand.run();
    }
}
