package com.zrlog.business.plugin;

import com.hibegin.common.util.http.handle.CloseResponseHandle;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.plugin.IPlugin;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Map;

/**
 * 运行 ZrLog 的插件，当 conf/plugins/ 这里目录下面不存在插件核心服务时，会通过网络请求下载最新的插件核心服务，也可以通过
 * 这种方式进行插件的及时更新。
 * 插件核心服务通过调用系统命令的命令进行启动的。
 */
public interface PluginCorePlugin extends IPlugin {

    /** Only hosts implementing the launch ownership contract opt into schedule migration. */
    default boolean supportsHostScheduler() { return false; }

    /** Configure before start; plugin process maintenance remains owned by plugin-core. */
    default void setExternallyScheduled(boolean externallyScheduled) { }

    default boolean startWithinBudget(long remainingMillis) throws InterruptedException { return false; }

    boolean refreshCache(String cacheVersion, HttpRequest request);

    /** Bounded, single-attempt refresh for request-lifetime jobs. Unsupported hosts fail explicitly. */
    default boolean refreshCacheWithinBudget(String cacheVersion, String triggerId, int actorId, long remainingMillis)
            throws IOException, InterruptedException {
        return false;
    }

    /** One authenticated tick, including bounded startup when needed. Null means unavailable before dispatch. */
    default PluginSchedulerTickResult tickScheduledTasks(String triggerId, int actorId, long remainingMillis)
            throws IOException, InterruptedException {
        return null;
    }

    CloseResponseHandle getContext(String uri, HttpMethod method, HttpRequest request, AdminTokenVO adminTokenVO) throws IOException, URISyntaxException, InterruptedException;

    /** Public inline view rendering owned by the invoking static-generation request. */
    default String renderViewWithinBudget(String uri, HttpRequest request, StaticGenerationBudget budget)
            throws IOException, InterruptedException {
        throw new IOException("Plugin host does not support completed view rendering");
    }

    <T> T requestService(HttpRequest inputRequest, Map<String, String[]> params, AdminTokenVO adminTokenVO, Class<T> clazz) throws IOException, URISyntaxException, InterruptedException;

    /**
     * 代理中转HTTP请求，目前仅支持，GET，POST 请求方式的中转。
     *
     * @param uri
     * @param request
     * @param response
     * @return true 表示请求正常执行，false 代表遇到了一些问题
     * @throws IOException
     * @throws URISyntaxException
     * @throws InterruptedException
     */
    boolean accessPlugin(String uri, HttpRequest request, HttpResponse response, AdminTokenVO adminTokenVO) throws IOException, URISyntaxException, InterruptedException;

    String getToken();

}
