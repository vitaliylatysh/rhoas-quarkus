//DEPS org.keycloak:keycloak-installed-adapter:18.0.0
//DEPS com.redhat.cloud:kafka-management-sdk:0.20.2
//DEPS com.redhat.cloud:kafka-instance-sdk:0.20.2
//DEPS info.picocli:picocli:4.6.3
//DEPS com.fasterxml.jackson.core:jackson-core:2.13.3
//DEPS com.fasterxml.jackson.core:jackson-annotations:2.13.3
//FILES ../../../../resources/META-INF/keycloak.json

package com.redhat.rhosak;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
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
public class Rhosak implements Callable<Integer> {

    private final ObjectMapper objectMapper;

    private final KeycloakInstalled keycloak;
    private final ApiClient managementAPIClient;
    private final DefaultApi managementAPI;
    private final TopicsApi apiInstanceTopic;

    public Rhosak() {
        this.objectMapper = new ObjectMapper();
        InputStream config = Thread.currentThread().getContextClassLoader().getResourceAsStream("keycloak.json");
        this.keycloak = new KeycloakInstalled(config);
        this.managementAPIClient = getManagementAPIClient();
        this.managementAPI = new DefaultApi(managementAPIClient);
        this.apiInstanceTopic = new TopicsApi();
    }

    @CommandLine.Option(names = "-tf, --tokens-file", paramLabel = "tokens-file", description = "File for storing obtained tokens.", defaultValue = "credentials.json")
    Path tokensPath;

    @CommandLine.Option(names = "--create-instance", paramLabel = "Create kafka instance", description = "Create kafka instance")
    String instanceName;

    @CommandLine.Option(names = "--create-topic", paramLabel = "Create instance topic", description = "Create instance topic")
    String instanceTopic;

    private static final Duration MIN_TOKEN_VALIDITY = Duration.ofSeconds(30);
    private static final String API_CLIENT_BASE_PATH = "https://api.openshift.com";

    @Override
    public Integer call() {
        if (Objects.nonNull(instanceName)) {
            System.out.println(createInstance(instanceName));
        }
        if (Objects.nonNull(instanceTopic)) {
            System.out.println(createInstanceTopic(instanceTopic));
        }
        return 0;
    }

    private KafkaRequest createInstance(String name) {
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

    private Topic createInstanceTopic(String topicName) {
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
            RhoasTokens storedTokens = getStoredTokenResponse();

            // ensure token is valid for at least 30 seconds
            if (storedTokens != null && storedTokens.accessTokenIsValidFor(MIN_TOKEN_VALIDITY)) {
                return storedTokens.access_token;
            } else if (storedTokens != null && storedTokens.refreshTokenIsValidFor(MIN_TOKEN_VALIDITY)) {
                keycloak.refreshToken(storedTokens.refresh_token);
                return keycloak.getTokenString();
            } else {
                keycloak.loginDesktop();
                return keycloak.getTokenString();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static class RhoasTokens {

        public String refresh_token;
        public String access_token;
        public Long access_expiration;
        public Long refresh_expiration;

        boolean accessTokenIsValidFor(Duration duration) {
            return (access_expiration) - duration.toMillis() >= System.currentTimeMillis();
        }

        boolean refreshTokenIsValidFor(Duration duration) {
            return (refresh_expiration) - duration.toMillis() >= System.currentTimeMillis();
        }
    }

    private RhoasTokens storeTokenResponse(KeycloakInstalled keycloak) throws IOException {
        RhoasTokens rhoasTokens = new RhoasTokens();
        rhoasTokens.refresh_token = keycloak.getRefreshToken();
        rhoasTokens.access_token = keycloak.getTokenString();
        long timeMillis = System.currentTimeMillis();
        rhoasTokens.refresh_expiration = timeMillis + keycloak.getTokenResponse().getRefreshExpiresIn() * 1000;
        rhoasTokens.access_expiration = timeMillis + keycloak.getTokenResponse().getExpiresIn() * 1000;
        objectMapper.writeValue(tokensPath.toFile(), rhoasTokens);

        return rhoasTokens;
    }

    private RhoasTokens getStoredTokenResponse() {
        try {
            return objectMapper.readValue(
                    Objects.requireNonNullElseGet(
                            tokensPath,
                            () -> Path.of("credentials.json")
                    ).toFile(),
                    RhoasTokens.class
            );
        } catch (Exception e) {
            return null;
        }
    }

    public static void main(String[] args) throws IOException {
        Rhosak rhosak = new Rhosak();
        int exitCode = new CommandLine(rhosak).execute(args);
        rhosak.storeTokenResponse(rhosak.keycloak);

        System.exit(exitCode);

    }
}
