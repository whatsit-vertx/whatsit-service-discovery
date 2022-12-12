package io.github.pangzixiang.whatsit.vertx.service.discovery.util;

import org.apache.commons.lang3.StringUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class WebsocketUtils {
    private WebsocketUtils() {
    }

    public static Map<String, String> parseQueryString(String query) {
        Map<String, String> result = new HashMap<>();
        if (query == null) {
            return result;
        }
        String[] temp = query.split("&");
        if (temp.length > 0) {
            Arrays.stream(temp).forEach(s -> {
                if (StringUtils.isNoneBlank(s)) {
                    String[] t = s.split("=");
                    if (t.length == 2) {
                        result.put(URLDecoder.decode(t[0], StandardCharsets.UTF_8),
                                URLDecoder.decode(t[1], StandardCharsets.UTF_8));
                    }
                }
            });
        }
        return result;
    }

    public static Map<String, String> parseCookie(String cookie) {
        Map<String, String> result = new HashMap<>();
        if (cookie == null) {
            return result;
        }
        String[] temp = cookie.split(";");
        if (temp.length > 0) {
            Arrays.stream(temp).map(StringUtils::trim).forEach(s -> {
                if (StringUtils.isNotBlank(s)) {
                    String[] t = s.split("=");
                    if (t.length == 2) {
                        result.put(t[0], t[1]);
                    }
                }
            });
        }
        return result;
    }
}
