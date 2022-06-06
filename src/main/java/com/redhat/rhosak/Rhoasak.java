//DEPS org.keycloak:keycloak-installed-adapter:18.0.0
//DEPS com.redhat.cloud:kafka-management-sdk:0.20.2
//DEPS com.redhat.cloud:kafka-instance-sdk:0.20.2
//DEPS info.picocli:picocli:4.6.3
//DEPS com.fasterxml.jackson.core:jackson-core:2.13.3
//DEPS com.fasterxml.jackson.core:jackson-annotations:2.13.3
//FILES ../../../../resources/META-INF/keycloak.json

package com.redhat.rhosak;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openshift.cloud.api.kas.DefaultApi;
import com.openshift.cloud.api.kas.SecurityApi;
import com.openshift.cloud.api.kas.auth.TopicsApi;
import com.openshift.cloud.api.kas.auth.invoker.auth.OAuth;
import com.openshift.cloud.api.kas.auth.models.ConfigEntry;
import com.openshift.cloud.api.kas.auth.models.NewTopicInput;
import com.openshift.cloud.api.kas.auth.models.Topic;
import com.openshift.cloud.api.kas.auth.models.TopicSettings;
import com.openshift.cloud.api.kas.invoker.ApiClient;
import com.openshift.cloud.api.kas.invoker.ApiException;
import com.openshift.cloud.api.kas.invoker.Configuration;
import com.openshift.cloud.api.kas.invoker.auth.HttpBearerAuth;
import com.openshift.cloud.api.kas.models.KafkaRequest;
import com.openshift.cloud.api.kas.models.KafkaRequestPayload;
import com.openshift.cloud.api.kas.models.ServiceAccount;
import com.openshift.cloud.api.kas.models.ServiceAccountRequest;
import org.keycloak.adapters.installed.KeycloakInstalled;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import javax.ws.rs.core.GenericType;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Callable;

@Command(name = "rhoasak", mixinStandardHelpOptions = true, subcommands = {LoginCommand.class, KafkaCommand.class, ServiceAccountCommand.class})
public class Rhoasak implements Callable<Integer> {

    @Override
    public Integer call() {
        return 0;
    }

    public static void main(String[] args) {
        Rhoasak rhoasak = new Rhoasak();
        int exitCode = new CommandLine(rhoasak).execute(args);

        System.exit(exitCode);
    }
}

@Command(name = "login", mixinStandardHelpOptions = true, description = "Login into RHOASAK")
class LoginCommand implements Callable<Integer> {

    private final ObjectMapper objectMapper;
    private final KeycloakInstalled keycloak;

    public LoginCommand() {
        this.objectMapper = new ObjectMapper();
        this.keycloak = KeycloakInstance.getKeycloakInstance();
    }

    @CommandLine.Option(names = "-tf, --tokens-file", paramLabel = "tokens-file", description = "File for storing obtained tokens.", defaultValue = Files.DEFAULT_CREDENTIALS_FILENAME)
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

    private void storeTokenResponse(KeycloakInstalled keycloak) throws IOException {
        RhoasTokens rhoasTokens = new RhoasTokens();
        rhoasTokens.refresh_token = keycloak.getRefreshToken();
        rhoasTokens.access_token = keycloak.getTokenString();
        long timeMillis = System.currentTimeMillis();
        rhoasTokens.refresh_expiration = timeMillis + keycloak.getTokenResponse().getRefreshExpiresIn() * 1000;
        rhoasTokens.access_expiration = timeMillis + keycloak.getTokenResponse().getExpiresIn() * 1000;
        objectMapper.writeValue(tokensPath.toFile(), rhoasTokens);
    }
}

@Command(name = "kafka", mixinStandardHelpOptions = true, description = "Create, view and manage your Kafka instances", subcommands = {KafkaCreateCommand.class, KafkaListCommand.class, KafkaTopicCommand.class, KafkaDeleteCommand.class})
class KafkaCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        return 0;
    }
}

@Command(name = "create", mixinStandardHelpOptions = true, description = "Create kafka instance")
class KafkaCreateCommand implements Callable<Integer> {

    private final DefaultApi managementAPI;

    public KafkaCreateCommand() {
        this.managementAPI =
                new DefaultApi(
                        KafkaManagementClient.getKafkaManagementAPIClientInstance()
                );
    }

    @CommandLine.Option(names = "--name", paramLabel = "string", description = "Name of the kafka instance")
    String instanceName;

    @Override
    public Integer call() {
        if (Objects.nonNull(instanceName)) {
            System.out.println(createInstance(instanceName));
        }

        return 0;
    }

    private KafkaRequest createInstance(String name) {
        KafkaRequestPayload kafkaRequestPayload = new KafkaRequestPayload();
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

    private final DefaultApi managementAPI;

    public KafkaListCommand() {
        this.managementAPI =
                new DefaultApi(
                        KafkaManagementClient.getKafkaManagementAPIClientInstance()
                );
    }

    @Override
    public Integer call() {
        try {
            System.out.println(managementAPI.getKafkas(null, null, null, null).getItems());
        } catch (ApiException e) {
            throw new RuntimeException(e.getMessage());
        }
        return 0;
    }
}

@Command(name = "delete", mixinStandardHelpOptions = true, description = "Delete a kafka instance")
class KafkaDeleteCommand implements Callable<Integer> {

    private final DefaultApi managementAPI;

    @CommandLine.Option(names = "--id", paramLabel = "string", required = true, description = "Unique ID of the Kafka instance you want to delete")
    String kafkaId;

    public KafkaDeleteCommand() {
        this.managementAPI =
                new DefaultApi(
                        KafkaManagementClient.getKafkaManagementAPIClientInstance()
                );
    }

    @Override
    public Integer call() {
        try {
            System.out.println(managementAPI.deleteKafkaById(kafkaId, true));
        } catch (ApiException e) {
            throw new RuntimeException(e.getMessage());
        }
        return 0;
    }
}

@Command(name = "acl", mixinStandardHelpOptions = true, description = "Manage Kafka ACLs for users and service accounts", subcommands = KafkaAclCreateCommand.class)
class KafkaAclCommand implements Callable<Integer> {

    public KafkaAclCommand() {}

    @Override
    public Integer call() {
        return 0;
    }
}

@Command(name = "create", mixinStandardHelpOptions = true, description = "Create a Kafka ACL")
class KafkaAclCreateCommand implements Callable<Integer> {

    @CommandLine.Option(names = "--operation", paramLabel = "string", required = true, description = "Set the ACL operation. Choose from: \"all\", \"alter\", \"alter-configs\", \"create\", \"delete\", \"describe\", \"describe-configs\", \"read\", \"write\"")
    String operation;

    @CommandLine.Option(names = "--permission", paramLabel = "string", required = true, description = "Set the ACL permission. Choose from: \"allow\", \"deny\"")
    String permission;

    @CommandLine.Option(names = "--service-account", paramLabel = "string", description = "Service account client ID used as principal for this operation")
    String serviceAccountClientID;

    @CommandLine.Option(names = "--topic", paramLabel = "string", description = "Set the topic resource")
    String topicResource;

    public KafkaAclCreateCommand() {}

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

    private final ObjectMapper objectMapper;
    private final com.openshift.cloud.api.kas.auth.invoker.ApiClient apiInstanceClient;
    private final ApiClient apiManagementClient;
    private final TopicsApi apiInstanceTopic;
    private final DefaultApi managementApi;

    public KafkaTopicCreateCommand() {
        this.objectMapper = new ObjectMapper();
        this.apiInstanceClient = KafkaInstanceClient.getKafkaInstanceAPIClient();
        this.apiManagementClient = KafkaManagementClient.getKafkaManagementAPIClientInstance();
        this.apiInstanceTopic = new TopicsApi(apiInstanceClient);
        this.managementApi = new DefaultApi(apiManagementClient);
    }

    @CommandLine.Option(names = "--name", paramLabel = "string", required = true, description = "Topic name")
    String topicName;

    @Override
    public Integer call() {
        try {
            System.out.println(createInstanceTopic(topicName));
        } catch (com.openshift.cloud.api.kas.auth.invoker.ApiException e) {
            throw new RuntimeException(e);
        }
        return 0;
    }

    private Topic createInstanceTopic(String topicName) throws com.openshift.cloud.api.kas.auth.invoker.ApiException {
        NewTopicInput topicInput = getTopicInput(topicName);
        String serverUrl = getServerUrl();

        apiInstanceClient.setBasePath(serverUrl);
        apiInstanceClient.setAccessToken(rhosakApiToken());

        try {
            Topic topic = apiInstanceTopic.createTopic(topicInput);
            System.out.println(">>> topic: " + topic);
            return topic;
        } catch (com.openshift.cloud.api.kas.auth.invoker.ApiException e) {
            if (e.getCode() == 401 || e.getCode() == 403) {

                //refresh

            } else {
                e.printStackTrace();
                throw e;
            }
        }

        return null;
    }

    private String getServerUrl() {
        try {
            return  "https://admin-server-" + managementApi.getKafkas(null, null, null, null).getItems().get(0).getBootstrapServerHost();
        } catch (ApiException e) {
            throw new RuntimeException("Cannot get kafka url", e.getCause());
        }
    }

    private NewTopicInput getTopicInput(String topicName) {
        NewTopicInput topicInput = new NewTopicInput();
        topicInput.setName(topicName);
        TopicSettings ts = new TopicSettings();
        List<ConfigEntry> list = new ArrayList<>();
        ts.setConfig(list);
        ts.setNumPartitions(1);
        topicInput.setSettings(ts);
        return topicInput;
    }

    private String rhosakApiToken() {
        try {
            RhoasTokens tokens = objectMapper.readValue(Path.of(Files.RHOSAK_API_CREDS_FILE_NAME + ".json").toFile(), RhoasTokens.class);
            return tokens.access_token;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

@Command(name = "service-account", mixinStandardHelpOptions = true, subcommands = {ServiceAccountCreateCommand.class, ServiceAccountListCommand.class, ServiceAccountResetCredentialsCommand.class, ServiceAccountDeleteCommand.class})
class ServiceAccountCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        return 0;
    }
}

@Command(name = "create", mixinStandardHelpOptions = true)
class ServiceAccountCreateCommand implements Callable<Integer> {
    private final ObjectMapper objectMapper;
    private final com.openshift.cloud.api.kas.auth.invoker.ApiClient kafkaAPIInstanceClient;
    private final SecurityApi securityAPI;

    public ServiceAccountCreateCommand() {
        this.objectMapper = new ObjectMapper();
        this.kafkaAPIInstanceClient = KafkaInstanceClient.getKafkaInstanceAPIClient();
        this.securityAPI = new SecurityApi(KafkaManagementClient.getKafkaManagementAPIClientInstance());
    }

    @CommandLine.Option(names = "--name", paramLabel = "string", description = "Service account name")
    String name;
    @CommandLine.Option(names = "--file-format", paramLabel = "string", description = "Format in which to save the service account credentials (default: \"json\")", defaultValue = "json")
    String fileFormat;

    @CommandLine.Option(names = "--short-description", paramLabel = "string", description = "Short description of the service account")
    String shortDescription;

    @Override
    public Integer call() {
        try {
            saveServiceAccountToFile(
                    createServiceAccount(name, shortDescription)
            );
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
        return 0;
    }

    private ServiceAccount createServiceAccount(String name, String shortDescription) {
        ServiceAccountRequest serviceAccountRequest = new ServiceAccountRequest();
        serviceAccountRequest.setName(name);
        serviceAccountRequest.setDescription(shortDescription);

        try {
            return securityAPI.createServiceAccount(serviceAccountRequest);
        } catch (ApiException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private void saveServiceAccountToFile(ServiceAccount serviceAccount) throws IOException {
        Path saFile = Path.of(Files.SA_FILE_NAME + "." + fileFormat);
        serviceAccount.setCreatedAt(null); // otherwise .rhosak-sa file will be broken
        objectMapper.writeValue(saFile.toFile(), serviceAccount);

        String clientId = serviceAccount.getClientId();
        String clientSecret = serviceAccount.getClientSecret();

        Map<String, Object> formParametersMap = new HashMap<>() {{
            put("grant_type", "client_credentials");
            put("client_id", clientId);
            put("client_secret", clientSecret);
            put("scope", "openid");
        }};
        String acceptString = "application/json";
        String contentTypeString = "application/x-www-form-urlencoded";
        GenericType<Map<String, String>> returnTypeClass = new GenericType<>() {
        };
        String URL = "/auth/realms/rhoas/protocol/openid-connect/token";
        try {
            Map<String, String> res = kafkaAPIInstanceClient.invokeAPI(URL,
                    "POST",
                    null,
                    null,
                    new HashMap<>(),
                    new HashMap<>(),
                    formParametersMap,
                    acceptString,
                    contentTypeString,
                    new String[]{"Bearer"},
                    returnTypeClass
            );

            Path apiTokensFile = Path.of(Files.RHOSAK_API_CREDS_FILE_NAME + "." + fileFormat);
            RhoasTokens tokens = new RhoasTokens();
            tokens.access_token = res.get("access_token");
            objectMapper.writeValue(apiTokensFile.toFile(), tokens);
        } catch (com.openshift.cloud.api.kas.auth.invoker.ApiException e) {
            throw new RuntimeException(e);
        }
    }
}

@Command(name = "list", mixinStandardHelpOptions = true)
class ServiceAccountListCommand implements Callable<Integer> {

    private final SecurityApi securityAPI;

    public ServiceAccountListCommand(){
        this.securityAPI = new SecurityApi(KafkaManagementClient.getKafkaManagementAPIClientInstance());
    }
    @Override
    public Integer call() {
        try {
            System.out.println(securityAPI.getServiceAccounts(null).getItems());
        } catch (ApiException e) {
            throw new RuntimeException(e);
        }
        return 0;
    }
}

@Command(name = "delete", mixinStandardHelpOptions = true)
class ServiceAccountDeleteCommand implements Callable<Integer> {

    private final SecurityApi securityAPI;

    @CommandLine.Option(names = "--id", paramLabel = "string", required = true, description = "The unique ID of the service account to delete")
    String name;

    public ServiceAccountDeleteCommand(){
        this.securityAPI = new SecurityApi(KafkaManagementClient.getKafkaManagementAPIClientInstance());
    }
    @Override
    public Integer call() {
        try {
            securityAPI.deleteServiceAccountById(name);
        } catch (ApiException e) {
            throw new RuntimeException(e);
        }
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

class KafkaManagementClient {

    private static final String API_CLIENT_BASE_PATH = "https://api.openshift.com";
    private static final Duration MIN_TOKEN_VALIDITY = Duration.ofSeconds(30);
    private static ApiClient kafkaManagementAPIClientInstance;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final KeycloakInstalled keycloak = KeycloakInstance.getKeycloakInstance();

    private KafkaManagementClient() {}

    public static ApiClient getKafkaManagementAPIClientInstance() {
        if (kafkaManagementAPIClientInstance == null) {
            kafkaManagementAPIClientInstance = Configuration.getDefaultApiClient();
            kafkaManagementAPIClientInstance.setBasePath(API_CLIENT_BASE_PATH);
            String tokenString = getBearerToken();

            // Configure HTTP bearer authorization: Bearer
            HttpBearerAuth bearer = (HttpBearerAuth) kafkaManagementAPIClientInstance.getAuthentication("Bearer");
            bearer.setBearerToken(tokenString);
        }
        return kafkaManagementAPIClientInstance;
    }

    private static RhoasTokens getStoredTokenResponse() {
        try {
            return objectMapper.readValue(
                    Path.of(Files.DEFAULT_CREDENTIALS_FILENAME).toFile(),
                    RhoasTokens.class
            );
        } catch (Exception e) {
            return null;
        }
    }

    public static String getBearerToken() {
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
}
class KafkaInstanceClient {

    private static final String API_INSTANCE_CLIENT_BASE_PATH = "https://identity.api.openshift.com";
    private static com.openshift.cloud.api.kas.auth.invoker.ApiClient kafkaInstanceAPIClient;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private KafkaInstanceClient() {
    }

    public static com.openshift.cloud.api.kas.auth.invoker.ApiClient getKafkaInstanceAPIClient() {
        if (kafkaInstanceAPIClient == null) {
            kafkaInstanceAPIClient = com.openshift.cloud.api.kas.auth.invoker.Configuration.getDefaultApiClient();
            kafkaInstanceAPIClient.setBasePath(API_INSTANCE_CLIENT_BASE_PATH);
            String tokenString = KafkaManagementClient.getBearerToken();

            OAuth bearer = (OAuth) kafkaInstanceAPIClient.getAuthentication("Bearer");
            bearer.setAccessToken(tokenString);
        }
        return kafkaInstanceAPIClient;
    }
}

class KeycloakInstance {
    private static KeycloakInstalled keycloak;

    private KeycloakInstance(){}

    public static KeycloakInstalled getKeycloakInstance() {
        if (keycloak == null) {
            InputStream config = Thread.currentThread().getContextClassLoader().getResourceAsStream(Files.KEYCLOAK_CONFIG_FILE);
            keycloak = new KeycloakInstalled(config);
        }
        return keycloak;
    }
}
class RhoasTokens {

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

class Files{
    public static final String KEYCLOAK_CONFIG_FILE = "keycloak.json";
    public static final String DEFAULT_CREDENTIALS_FILENAME = "credentials.json";
    public static final String SA_FILE_NAME = System.getProperty("user.dir") + File.separator + "rhosak-sa";
    public static final String RHOSAK_API_CREDS_FILE_NAME = System.getProperty("user.dir") + File.separator + "rhosak-api-creds";
}