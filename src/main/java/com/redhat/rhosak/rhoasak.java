//DEPS org.keycloak:keycloak-installed-adapter:18.0.0
//DEPS com.redhat.cloud:kafka-management-sdk:0.20.2
//DEPS com.redhat.cloud:kafka-instance-sdk:0.20.2
//DEPS info.picocli:picocli:4.6.3
//DEPS com.fasterxml.jackson.core:jackson-core:2.13.3
//DEPS com.fasterxml.jackson.core:jackson-annotations:2.13.3
//SOURCES ./RhoasTokens.java
//SOURCES ./CommandParameters.java
//FILES ../../../../resources/META-INF/keycloak.json

package com.redhat.rhosak;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;

import org.keycloak.adapters.installed.KeycloakInstalled;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openshift.cloud.api.kas.DefaultApi;
import com.openshift.cloud.api.kas.auth.TopicsApi;
import com.openshift.cloud.api.kas.auth.models.NewTopicInput;
import com.openshift.cloud.api.kas.auth.models.Topic;
import com.openshift.cloud.api.kas.invoker.ApiClient;
import com.openshift.cloud.api.kas.invoker.ApiException;
import com.openshift.cloud.api.kas.invoker.Configuration;
import com.openshift.cloud.api.kas.invoker.auth.HttpBearerAuth;
import com.openshift.cloud.api.kas.models.KafkaRequest;
import com.openshift.cloud.api.kas.models.KafkaRequestPayload;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "rhosak", mixinStandardHelpOptions = true)
public class rhoasak implements Callable<Integer> {
    
    private static final Duration MIN_TOKEN_VALIDITY = Duration.ofSeconds(30);
    private static final String API_CLIENT_BASE_PATH = "https://api.openshift.com";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final CommandParameters parameters = new CommandParameters();

    private final ApiClient managementAPIClient = getManagementAPIClient();
    private final DefaultApi managementAPI = new DefaultApi(managementAPIClient);
    private final TopicsApi apiInstanceTopic = new TopicsApi();

    @Override
    public Integer call() {
            if(Objects.nonNull(parameters.instanceName)){
                System.out.println(createInstance(parameters.instanceName));
            }
            if(Objects.nonNull(parameters.instanceTopic)){
                System.out.println(createInstanceTopic(parameters.instanceTopic));
            }
            return 0;
    }

    private KafkaRequest createInstance(String name){
        KafkaRequestPayload kafkaRequestPayload = new KafkaRequestPayload(); // KafkaRequestPayload | Kafka data
        kafkaRequestPayload.setName(name);

        try {
            return managementAPI.createKafka(true, kafkaRequestPayload);
        } catch (ApiException e) {
            System.err.println("Exception when calling DefaultApi#createKafka");
            System.err.println("Status code: " + e.getCode());
            System.err.println("Reason: " + e.getResponseBody());
            System.err.println("Response headers: " + e.getResponseHeaders());
            e.printStackTrace();
        }

        return null;
    }

    private Topic createInstanceTopic(String topicName){
        NewTopicInput topicInput = new NewTopicInput();
        topicInput.setName(topicName);

        try {
            return apiInstanceTopic.createTopic(topicInput);
        } catch (com.openshift.cloud.api.kas.auth.invoker.ApiException e) {
            throw new RuntimeException(e.getMessage());
        }
    }


    private ApiClient getManagementAPIClient() {
        ApiClient apiClient = Configuration.getDefaultApiClient();
        apiClient.setBasePath(API_CLIENT_BASE_PATH);

        String tokenString = getBearerToken();

        // Configure HTTP bearer authorization: Bearer
        HttpBearerAuth bearer = (HttpBearerAuth) apiClient.getAuthentication("Bearer");
        bearer.setBearerToken(tokenString);
        return apiClient;
    }

    private String getBearerToken() {
        try {
            // reads the configuration from classpath: META-INF/keycloak.json
            InputStream config = Thread.currentThread().getContextClassLoader().getResourceAsStream("keycloak.json");
            KeycloakInstalled keycloak = new KeycloakInstalled(config);
            // TODO set resteasyclient depending on the platform
//             keycloak.setResteasyClient();

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
        objectMapper.writeValue(parameters.tokensPath.toFile(), rhoasTokens);
        return rhoasTokens;
    }

    RhoasTokens getStoredTokenResponse() {
        try {
            return objectMapper.readValue(parameters.tokensPath.toFile(), RhoasTokens.class);
        } catch (Exception e) {
            return null;
        }
    }

    public static void main(String[] args) {
        new CommandLine(parameters).parseArgs(args);
        int exitCode = new CommandLine(new rhoasak()).execute(args);
        System.exit(exitCode);
    }
}
