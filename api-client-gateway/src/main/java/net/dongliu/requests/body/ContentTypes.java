package net.dongliu.requests.body;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static net.dongliu.requests.HttpHeaders.CONTENT_TYPE_BINARY;

class ContentTypes {
    static String probeContentType(File file) {
        String contentType;
        try {
            contentType = Files.probeContentType(file.toPath());
        } catch (IOException e) {
            contentType = null;
        }
        if (contentType == null) {
            contentType = CONTENT_TYPE_BINARY;
        }
        return contentType;
    }

    /**
     * If content type looks like a text content.
     */
    static boolean isText(String contentType) {
        return contentType.contains("text") || contentType.contains("json")
                || contentType.contains("xml") || contentType.contains("html");
    }
}
