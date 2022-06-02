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

@Command(name = "rhosak", mixinStandardHelpOptions = true, subcommands = {LoginCommand.class, KafkaCommand.class, ServiceAccountCommand.class})
public class Rhosak implements Callable<Integer> {

    @Override
    public Integer call() {
        return 0;
    }

    public static void main(String[] args) {
        Rhosak rhosak = new Rhosak();
        int exitCode = new CommandLine(rhosak).execute(args);

        System.exit(exitCode);

    }
}

@Command(name = "login", mixinStandardHelpOptions = true, description = "Getting redhat credentials")
class LoginCommand implements Callable<Integer>{

    public static final String DEFAULT_CREDENTIALS_FILENAME = "credentials.json";
    public static final String KEYCLOAK_CONFIG_FILE = "keycloak.json";
    private static final Duration MIN_TOKEN_VALIDITY = Duration.ofSeconds(30);



    final ObjectMapper objectMapper;
    private final KeycloakInstalled keycloak;

    public LoginCommand() {
        this.objectMapper = new ObjectMapper();
        InputStream config = Thread.currentThread().getContextClassLoader().getResourceAsStream(KEYCLOAK_CONFIG_FILE);
        this.keycloak = new KeycloakInstalled(config);
    }

    @CommandLine.Option(names = "-tf, --tokens-file", paramLabel = "tokens-file", description = "File for storing obtained tokens.", defaultValue = DEFAULT_CREDENTIALS_FILENAME)
    Path tokensPath;

    @Override
    public Integer call() throws IOException {
        try {
            keycloak.loginDesktop();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        storeTokenResponse(keycloak);
        return 0;
    }

//    private String getBearerToken() {
//        try {
//            RhoasTokens storedTokens = getStoredTokenResponse();
//
//            // ensure token is valid for at least 30 seconds
//            if (storedTokens != null && storedTokens.accessTokenIsValidFor(MIN_TOKEN_VALIDITY)) {
//                return storedTokens.access_token;
//            } else if (storedTokens != null && storedTokens.refreshTokenIsValidFor(MIN_TOKEN_VALIDITY)) {
//                keycloak.refreshToken(storedTokens.refresh_token);
//                return keycloak.getTokenString();
//            } else {
//                keycloak.loginDesktop();
//                return keycloak.getTokenString();
//            }
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//    }

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
}

@Command(name = "kafka", mixinStandardHelpOptions = true, subcommands = {KafkaCreateCommand.class, KafkaListCommand.class, KafkaTopicCommand.class})
class KafkaCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        return 0;
    }
}

@Command(name = "create", mixinStandardHelpOptions = true, description = "Create kafka instance")
class KafkaCreateCommand implements Callable<Integer> {

    private static final String API_CLIENT_BASE_PATH = "https://api.openshift.com";

    private final ObjectMapper objectMapper;
    private final DefaultApi managementAPI;
    private final ApiClient managementAPIClient;
    public KafkaCreateCommand(){
        this.objectMapper = new ObjectMapper();
        this.managementAPIClient = getManagementAPIClient();
        this.managementAPI = new DefaultApi(managementAPIClient);
    }
    @CommandLine.Option(names = "--name", paramLabel = "KAFKA_INSTANCE_NAME", description = "Name of the kafka instance")
    String instanceName;

    @Override
    public Integer call() {
        if (Objects.nonNull(instanceName)) {
            System.out.println(createInstance(instanceName));
        }

        return 0;
    }

    private LoginCommand.RhoasTokens getStoredTokenResponse() {
        try {
            return objectMapper.readValue(
                    Path.of(LoginCommand.DEFAULT_CREDENTIALS_FILENAME).toFile(),
                    LoginCommand.RhoasTokens.class
            );
        } catch (Exception e) {
            return null;
        }
    }
    private ApiClient getManagementAPIClient() {
        ApiClient apiClient = Configuration.getDefaultApiClient();
        apiClient.setBasePath(API_CLIENT_BASE_PATH);

        String tokenString = getStoredTokenResponse().access_token;

        // Configure HTTP bearer authorization: Bearer
        HttpBearerAuth bearer = (HttpBearerAuth) apiClient.getAuthentication("Bearer");
        bearer.setBearerToken(tokenString);
        return apiClient;
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
            throw new RuntimeException(e.getMessage());
        }
    }
}

@Command(name = "list", mixinStandardHelpOptions = true, description = "List all kafka instances")
class KafkaListCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        return 0;
    }
}

@Command(name = "topic", mixinStandardHelpOptions = true, subcommands = KafkaTopicCreateCommand.class)
class KafkaTopicCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        return 0;
    }
}

@Command(name = "create", mixinStandardHelpOptions = true)
class KafkaTopicCreateCommand implements Callable<Integer> {

    private final TopicsApi apiInstanceTopic;

    public KafkaTopicCreateCommand(){
        this.apiInstanceTopic = new TopicsApi();
    }
    @CommandLine.Option(names = "--topic", paramLabel = "KAFKA_TOPIC_NAME", description = "Create kafka instance topic")
    String instanceTopic;
    @Override
    public Integer call() {
        if (Objects.nonNull(instanceTopic)) {
            System.out.println(createInstanceTopic(instanceTopic));
        }
        return 0;
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
}

@Command(name = "service-account", mixinStandardHelpOptions = true, subcommands = {ServiceAccountCreateCommand.class, ServiceAccountListCommand.class, ServiceAccountResetCredentialsCommand.class})
class ServiceAccountCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        return 0;
    }
}

@Command(name = "create", mixinStandardHelpOptions = true)
class ServiceAccountCreateCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        return 0;
    }
}

@Command(name = "list", mixinStandardHelpOptions = true)
class ServiceAccountListCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        return 0;
    }
}

@Command(name = "reset-credentials", mixinStandardHelpOptions = true)
class ServiceAccountResetCredentialsCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        return 0;
    }
}
