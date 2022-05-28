package com.ozangunalp;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

import com.openshift.cloud.api.kas.SecurityApi;
import com.openshift.cloud.api.kas.auth.models.ConfigEntry;
import com.openshift.cloud.api.kas.auth.models.TopicSettings;
import com.openshift.cloud.api.kas.models.*;
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

import picocli.CommandLine;
import picocli.CommandLine.Command;

import javax.ws.rs.core.GenericType;

@Command(name = "rhoas", mixinStandardHelpOptions = true)
public class RhoasCommand implements Runnable {

    private static final Duration MIN_TOKEN_VALIDITY = Duration.ofSeconds(30);
    private static final String API_CLIENT_BASE_PATH = "https://api.openshift.com";
    private static final String API_INSTANCE_CLIENT_BASE_PATH = "https://identity.api.openshift.com";
    private static final String SA_FILE_NAME = System.getProperty("user.dir") + File.separator + ".rhosak-sa.json";

    ObjectMapper objectMapper = new ObjectMapper();

    private static final CommandParameters parameters = new CommandParameters();

    private final ApiClient kafkaManagementClient = getApiClient();
    private final com.openshift.cloud.api.kas.auth.invoker.ApiClient kafkaInstanceClient = getKafkaInstanceClient();
    private final DefaultApi managementAPI = new DefaultApi(kafkaManagementClient);
    private final TopicsApi topicsAPI = new TopicsApi(kafkaInstanceClient);
    private final SecurityApi securityAPI = new SecurityApi(kafkaManagementClient);

    private String secondAccessToken = null;

    @Override
    public void run() {
        if (Objects.nonNull(parameters)) {
            ServiceAccount serviceAccount;
            if (Objects.nonNull(parameters.serviceAccount)) {
                serviceAccount = createServiceAccount(parameters.serviceAccount);
            } else {
                serviceAccount = getServiceAccount();
            }
            secondAccessToken = getSecondToken(serviceAccount);
            if (Objects.nonNull(parameters.instanceName)) {
//                System.out.println(createInstance(parameters.instanceName));
            }
            if (Objects.nonNull(parameters.instanceTopic)) {
                System.out.println(createInstanceTopic(parameters.instanceTopic));
            }
        }
    }

    private String getSecondToken(ServiceAccount serviceAccount) {
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
        GenericType<LinkedHashMap<String, String>> returnTypeClass = new GenericType<>() {
        };
        LinkedHashMap<String, String> res = null;
        try {
            String URL = "/auth/realms/rhoas/protocol/openid-connect/token";
            res = kafkaInstanceClient.invokeAPI(URL,
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
        } catch (com.openshift.cloud.api.kas.auth.invoker.ApiException e) {
            e.printStackTrace();
        }

        String accessToken = Objects.requireNonNull(res).get("access_token");
        System.out.println(">>> accessToken: " + accessToken);
        System.out.println(">>> serviceAccount: " + serviceAccount);

        return accessToken;
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
        TopicSettings ts = new TopicSettings();
        List<ConfigEntry> list = new ArrayList<>();
        ts.setConfig(list);
        ts.setNumPartitions(1);
        topicInput.setSettings(ts);

        String serverUrl = null;
        try {
            serverUrl = "https://admin-server-" + managementAPI.getKafkas(null, null, null, null).getItems().get(0).getBootstrapServerHost();
        } catch (ApiException e) {
            e.printStackTrace();
        }

        try {
            topicsAPI.getApiClient().setBasePath(serverUrl);
//            secondAccessToken = "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJnakluN0lUY3BrMGlMN0QwTU9jT2xZVkc5c1pwOWR2c2dBOHJUb2FWd0s0In0.eyJleHAiOjE2NTM1NzMzNzUsImlhdCI6MTY1MzU3MzA3NSwianRpIjoiNjNmNmY5YjQtZTI3Yy00ZmQ1LTlhOTctNWNlNDA2NGI1YjI4IiwiaXNzIjoiaHR0cHM6Ly9pZGVudGl0eS5hcGkub3BlbnNoaWZ0LmNvbS9hdXRoL3JlYWxtcy9yaG9hcyIsInN1YiI6IjFmNDIyN2Q1LWY0ODMtNGVmYy1hYjExLTE4YTY2ZDg0OGFmZiIsInR5cCI6IkJlYXJlciIsImF6cCI6InNydmMtYWNjdC0wZjcxMTM5NS0zYTRlLTQ1ZjMtODFhOS01ZGVlZDY2NzYxNmYiLCJhY3IiOiIxIiwicmVhbG1fYWNjZXNzIjp7InJvbGVzIjpbIm9mZmxpbmVfYWNjZXNzIiwiZGVmYXVsdC1yb2xlcy1yaG9hcyIsInVtYV9hdXRob3JpemF0aW9uIl19LCJzY29wZSI6Im9wZW5pZCBwcm9maWxlIGVtYWlsIiwiY2xpZW50SWQiOiJzcnZjLWFjY3QtMGY3MTEzOTUtM2E0ZS00NWYzLTgxYTktNWRlZWQ2Njc2MTZmIiwiY2xpZW50SG9zdCI6IjE3Ni4zNi45LjM5IiwiZW1haWxfdmVyaWZpZWQiOmZhbHNlLCJyaC11c2VyLWlkIjoiNTUxNzQ3OTUiLCJyaC1vcmctaWQiOiIxNTk5MDgxMSIsInByZWZlcnJlZF91c2VybmFtZSI6InNlcnZpY2UtYWNjb3VudC1zcnZjLWFjY3QtMGY3MTEzOTUtM2E0ZS00NWYzLTgxYTktNWRlZWQ2Njc2MTZmIiwiY2xpZW50QWRkcmVzcyI6IjE3Ni4zNi45LjM5IiwidXNlcm5hbWUiOiJzZXJ2aWNlLWFjY291bnQtc3J2Yy1hY2N0LTBmNzExMzk1LTNhNGUtNDVmMy04MWE5LTVkZWVkNjY3NjE2ZiJ9.GlMkLIkVmDl_VM-ytMoEGKsza0gXAb8rJSWEOUmCZcVIaJ6lcHgboxx1Sb9zE8gMPirxss2Ydyk3mnW72wc2v1ySiHinJwvwRSDNxQaxwbuiB4PpHt6hbMg26LW7fZCAQm1tEPGdSyzOywGfyFoMBr2DtUJbzGQpNocq9ZsM9nq7Jc_GwtkxOJt_LBUn0legMT-BqPjLMPSa5PE9aXpgl6lWP-oNLiTRG0qmvzfOa4irySIMev2z9K5gf6w2as6lPKU64SZZos2l7fOhOxhm-ykj-ap7P1ryhdPjnwGHsQiOrXgfzPTxXnBd0J9i3QyAMa9BkKjVaeQ8XTPaJ9e_IA";
            topicsAPI.getApiClient().setAccessToken(secondAccessToken);
            Topic topic = topicsAPI.createTopic(topicInput);
            System.out.println(">>> topic: " + topic);
            return topic;
        } catch (com.openshift.cloud.api.kas.auth.invoker.ApiException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return null;
    }

    /**
     * From https://github.com/redhat-developer/app-services-sdk-java/tree/main/packages/kafka-management-sdk#getting-started
     */
    private ApiClient getApiClient() {
        ApiClient apiClient = Configuration.getDefaultApiClient();
        apiClient.setBasePath(API_CLIENT_BASE_PATH);

        String tokenString = getBearerToken(null);

        // Configure HTTP bearer authorization: Bearer
        HttpBearerAuth bearer = (HttpBearerAuth) apiClient.getAuthentication("Bearer");
        bearer.setBearerToken(tokenString);
        return apiClient;
    }

    private ServiceAccount getServiceAccount() {
        ServiceAccount serviceAccount = null;
        try {
            // check if "~/.rhosak-sa.json" file exists
            File saFile = new File(SA_FILE_NAME);
            if (saFile.exists()) {
                // read client_id & client_secret
                serviceAccount  = objectMapper.readValue(saFile, ServiceAccount.class);
            } else {
                String newServiceAccountName = "new-service-account";
                serviceAccount = createServiceAccount(newServiceAccountName);
                if (serviceAccount == null) {
                    throw new RuntimeException("Can't create service account: " + newServiceAccountName);
                } else {
                    saveServiceAccountToFile(serviceAccount);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return serviceAccount;
    }

    private void saveServiceAccountToFile(ServiceAccount serviceAccount) throws IOException {
        Path saFile = Path.of(SA_FILE_NAME);
        serviceAccount.setCreatedAt(null); // otherwise .rhosak-sa.json file will be broken
        objectMapper.writeValue(saFile.toFile(), serviceAccount);
    }

    private ServiceAccount createServiceAccount(String name) {
        ServiceAccountRequest serviceAccountRequest = new ServiceAccountRequest();
        serviceAccountRequest.setName(name);
        serviceAccountRequest.setDescription("My super service account");

        ServiceAccount serviceAccount = null;
        try {
            serviceAccount = securityAPI.createServiceAccount(serviceAccountRequest);
        } catch (ApiException e) {
            e.printStackTrace();
        }

        return serviceAccount;
    }

    private com.openshift.cloud.api.kas.auth.invoker.ApiClient getKafkaInstanceClient() {
        com.openshift.cloud.api.kas.auth.invoker.ApiClient apiClient
                = com.openshift.cloud.api.kas.auth.invoker.Configuration.getDefaultApiClient();

        // https://admin-server-my-instanc-ca-nv-d-aqfmd-jmum-g.bf2.kafka.rhcloud.com/api/v1/topics
        //                      my-instanc-ca-nv-d-aqfmd-jmum-g.bf2.kafka.rhcloud.com:443

//        String serverUrl = null;
//        try {
//            serverUrl = "https://admin-server-" + apiInstance.getKafkas(null, null, null, null).getItems().get(0).getBootstrapServerHost();
//        } catch (ApiException e) {
//            e.printStackTrace();
//        }
        apiClient.setBasePath(API_INSTANCE_CLIENT_BASE_PATH);

        // Configure HTTP bearer authorization: Bearer
        com.openshift.cloud.api.kas.auth.invoker.auth.OAuth bearer =
                (com.openshift.cloud.api.kas.auth.invoker.auth.OAuth) apiClient.getAuthentication("Bearer");
        bearer.setAccessToken(secondAccessToken);
//        HttpBearerAuth auth = (HttpBearerAuth) apiClient.getAuthentication("Bearer");
//        auth.setBearerToken(tokenString);
        return apiClient;
    }

    /**
     * From https://www.keycloak.org/docs/latest/securing_apps/#_installed_adapter
     */
    private String getBearerToken(String fileName) {
        try {
            KeycloakInstalled keycloak;
            RhoasTokens storedTokens = null;
            if (fileName == null) {
                // reads the configuration from classpath: META-INF/keycloak.json
                keycloak = new KeycloakInstalled();
                storedTokens = getStoredTokenResponse(null);
            } else {
                InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(fileName);
                keycloak = new KeycloakInstalled(is);
            }
            // TODO set resteasyclient depending on the platform
            // keycloak.setResteasyClient();


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

    RhoasTokens getStoredTokenResponse(String fileName) {
        try {
            if (fileName == null) {
                return objectMapper.readValue(parameters.tokensPath.toFile(), RhoasTokens.class);
            } else {
                return objectMapper.readValue(parameters.tokensPath2.toFile(), RhoasTokens.class);
            }
        } catch (Exception e) {
            return null;
        }
    }

    public static void main(String[] args) {
        new CommandLine(parameters).parseArgs(args);
        RhoasCommand rhoasCommand = new RhoasCommand();
        rhoasCommand.run();
    }
}
