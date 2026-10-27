package it.unibo.hermes.e2e.support;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * Utility class for performing HTTP requests.
 */
public final class HttpClient {

    private HttpClient() {}

    public record Response(int status, String body) {}

    /**
     * Performs a GET request to the given path
     * with the given authorisation header and timeout.
     *
     * @param path the path to call
     * @param authorisation the authorisation header, or null if none
     * @param timeout the timeout for the request
     * @return the response, never null
     */
    public static Response get(String path, String authorisation, Duration timeout) {
        return call(HttpMethod.GET, path, authorisation, null, timeout);
    }

    /**
     * Performs a POST request to the given path with the given JSON body and timeout.
     *
     * @param path the path to call
     * @param json the JSON body to send
     * @param timeout the timeout for the request
     * @return the response, never null
     */
    public static Response postJson(String path, String json, Duration timeout) {
        return call(HttpMethod.POST, path, null, json, timeout);
    }

    /**
     * Creates a JSON string containing the given username and password.
     *
     * @param username the username
     * @param password the password
     * @return the JSON string
     */
    public static String credentials(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    private static Response call(HttpMethod method, String path, String authorisation, String json,
                                 Duration timeout) {
        WebClient.RequestBodySpec spec = WebClient.create(E2EConfig.get().baseUrl())
                .method(method).uri(path).accept(MediaType.ALL);
        if (authorisation != null) {
            spec.header("Authorization", authorisation);
        }
        if (json != null) {
            spec.contentType(MediaType.APPLICATION_JSON).bodyValue(json);
        }
        Response r = spec.exchangeToMono(resp -> resp.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(b -> new Response(resp.statusCode().value(), b)))
                .block(timeout);
        return r == null ? new Response(-1, "") : r;
    }
}
