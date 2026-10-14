package com.zrlog.common.updater.docker;

import com.hibegin.common.util.http.HttpUtil;
import com.hibegin.common.util.http.handle.HttpStringHandle;
import com.zrlog.common.vo.Version;
import com.zrlog.updater.spi.UpdateCatalogs;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

/** Host HTTP adapter and compatible entry points for independently supplied metadata rules. */
public final class UpdateMetadata {
    private UpdateMetadata() { }

    public static String fetch(URI uri) throws IOException {
        try {
            HttpStringHandle response = new HttpStringHandle();
            HttpUtil.getInstance().sendGetRequest(uri.toString(), response, Map.of());
            int status = response.getStatusCode();
            if (status == 404) { throw new FileNotFoundException("Update metadata source returned HTTP 404"); }
            if (status != 200) { throw new IOException("Update metadata source returned HTTP " + status); }
            return response.getT();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("Update metadata request interrupted", error);
        } catch (URISyntaxException error) {
            throw new IOException("Invalid update metadata URL", error);
        }
    }

    public static URI uri(String origin, String filename, String channel) {
        return UpdateCatalogs.getInstance().metadataUri(origin, filename, channel);
    }

    public static void validateDocker(Version version, String channel) {
        UpdateCatalogs.getInstance().validateDocker(version, channel);
    }
}
