package com.redhat.mcp.languagetools.installer.task;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.redhat.mcp.languagetools.installer.InstallerContext;
import com.redhat.mcp.languagetools.installer.download.ContentLengthAware;
import com.redhat.mcp.languagetools.progress.AbstractProgressMonitor;
import com.redhat.mcp.languagetools.progress.ProgressMonitor;
import com.redhat.mcp.languagetools.installer.download.AssetFetcher;
import com.redhat.mcp.languagetools.installer.download.DecompressorUtils;
import com.redhat.mcp.languagetools.installer.download.DownloadUtils;
import com.redhat.mcp.languagetools.trace.TraceCollector;
import org.jboss.logging.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Function;

/**
 * Task that downloads and extracts a file.
 * Supports direct URL, GitHub releases, and Maven artifacts.
 */
public class DownloadTask implements InstallerTask {
    private static final Logger LOG = Logger.getLogger(DownloadTask.class);

    private final String name;
    private final String url;  // Fallback URL if asset fetcher fails
    private final AssetFetcherInfo assetFetcherInfo;  // GitHub or Maven fetcher
    private final OutputInfo outputInfo;
    private final InstallerTask onSuccessTask;

    public DownloadTask(String name, String url, AssetFetcherInfo assetFetcherInfo,
                       OutputInfo outputInfo, InstallerTask onSuccessTask) {
        this.name = name;
        this.url = url;
        this.assetFetcherInfo = assetFetcherInfo;
        this.outputInfo = outputInfo;
        this.onSuccessTask = onSuccessTask;
    }

    /**
     * Info for GitHub or Maven asset fetcher.
     */
    public record AssetFetcherInfo(AssetFetcher assetFetcher,
                                   Function<JsonObject, Boolean> releaseMatcher,
                                   Function<JsonObject, Boolean> assetMatcher) {
    }

    /**
     * Output information for download task.
     */
    public record OutputInfo(String outputDir, String outputFileName, boolean executable) {
    }

    @Override
    public boolean execute(InstallerContext context) {
        context.checkCanceled();

        // Get download URL (try asset fetcher first, fallback to direct URL)
        String resolvedUrl = getDownloadUrl(context);
        if (resolvedUrl == null) {
            LOG.error("No download URL available");
            return false;
        }

        String resolvedOutputDir = context.resolveVariables(outputInfo.outputDir());

        TraceCollector trace = context.getConfig().getTraceCollector();
        if (trace != null) {
            trace.info("Downloading from: " + resolvedUrl);
        }

        context.getProgress().reportProgress("Downloading " + name);

        try {
            Path outputPath = Paths.get(resolvedOutputDir);
            Files.createDirectories(outputPath);

            // Determine file extension from URL
            String fileName = resolvedUrl.substring(resolvedUrl.lastIndexOf('/') + 1);
            if (fileName.contains("?")) {
                fileName = fileName.substring(0, fileName.indexOf('?'));
            }
            Path downloadedFile = Files.createTempFile("download-", fileName);

            try {
                // Download file with progress tracking
                // Note: Don't enable sendProgressUpdates on TraceProgressMonitor because
                // ProgressMonitorWrapper already sends its own UPDATE messages with MB/MB display
                ProgressMonitorWrapper downloadProgress = new ProgressMonitorWrapper(context, trace, name);

                // Download (contentLength will be set automatically via ContentLengthAware interface)
                DownloadUtils.DownloadResult result = DownloadUtils.download(resolvedUrl, downloadedFile, downloadProgress);

                // Decompress based on file extension, or simply copy if not an archive
                DecompressorUtils.Decompressor decompressor = DecompressorUtils.getDecompressor(downloadedFile);
                if (decompressor != null) {
                    // Archive file - extract it
                    // Don't call reportProgress here - keep the current progress from download
                    if (trace != null) {
                        trace.update("Extracting " + name);
                    }

                    Path rootDir = decompressor.decompress(downloadedFile, outputPath);
                } else {
                    // Not an archive - copy the file directly
                    // Don't call reportProgress here - keep the current progress from download
                    if (trace != null) {
                        trace.update("Installing " + name);
                    }

                    // Determine the target file name
                    String targetFileName = outputInfo.outputFileName() != null ? context.resolveVariables(outputInfo.outputFileName()) : fileName;
                    Path targetFile = outputPath.resolve(targetFileName);

                    // Ensure parent directory exists
                    Files.createDirectories(targetFile.getParent());

                    // Copy the downloaded file
                    Files.copy(downloadedFile, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                    if (trace != null) {
                        trace.info("File copied to: " + targetFile);
                    }

                    // Set executable permission if needed
                    if (outputInfo.executable()) {
                        if (trace != null) {
                            trace.info("Setting executable permission: " + targetFile);
                        }
                        targetFile.toFile().setExecutable(true, false);
                    }
                }

                // Store output dir and file name in context for onSuccess tasks
                context.setVariable("output.dir", resolvedOutputDir);
                if (outputInfo.outputFileName() != null) {
                    String resolvedFileName = context.resolveVariables(outputInfo.outputFileName());
                    context.setVariable("output.file.name", resolvedFileName);
                }

                if (trace != null) {
                    trace.info("Downloaded and extracted to: " + resolvedOutputDir);
                }

                // Execute onSuccess task
                if (onSuccessTask != null) {
                    return onSuccessTask.execute(context);
                }

                return true;

            } finally {
                // Clean up temp file
                Files.deleteIfExists(downloadedFile);
            }

        } catch (Exception e) {
            LOG.errorf(e, "Download failed: %s", resolvedUrl);
            if (trace != null) {
                trace.error("Download failed: " + e.getMessage());
            }
            return false;
        }
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * Get download URL - try asset fetcher (GitHub/Maven) first, then fallback to direct URL.
     */
    private String getDownloadUrl(InstallerContext context) {
        // Try asset fetcher first (GitHub or Maven)
        if (assetFetcherInfo != null) {
            TraceCollector trace = context.getConfig().getTraceCollector();
            AssetFetcherReporter reporter = new AssetFetcherReporter(trace);

            String fetchedUrl = assetFetcherInfo.assetFetcher().getDownloadUrl(
                assetFetcherInfo.releaseMatcher(),
                assetFetcherInfo.assetMatcher(),
                reporter
            );

            if (fetchedUrl != null) {
                return context.resolveVariables(fetchedUrl);
            }

            if (trace != null) {
                trace.info("Asset fetcher failed, falling back to direct URL");
            }
        }

        // Fallback to direct URL
        return url != null ? context.resolveVariables(url) : null;
    }

    /**
     * Factory for DownloadTask.
     */
    public static class Factory implements InstallerTaskFactory {
        private static final String URL_JSON_PROPERTY = "url";
        private static final String GITHUB_JSON_PROPERTY = "github";
        private static final String GITHUB_OWNER_JSON_PROPERTY = "owner";
        private static final String GITHUB_REPOSITORY_JSON_PROPERTY = "repository";
        private static final String GITHUB_ASSET_JSON_PROPERTY = "asset";
        private static final String GITHUB_PRERELEASE_JSON_PROPERTY = "prerelease";
        private static final String MAVEN_JSON_PROPERTY = "maven";
        private static final String MAVEN_GROUP_ID_JSON_PROPERTY = "groupId";
        private static final String MAVEN_ARTIFACT_ID_JSON_PROPERTY = "artifactId";
        private static final String OUTPUT_JSON_PROPERTY = "output";
        private static final String OUTPUT_DIR_JSON_PROPERTY = "dir";
        private static final String OUTPUT_FILE_JSON_PROPERTY = "file";
        private static final String OUTPUT_FILE_NAME_JSON_PROPERTY = "name";
        private static final String OUTPUT_FILE_EXECUTABLE_JSON_PROPERTY = "executable";

        // Lazy singleton registry to avoid circular initialization
        private static volatile InstallerTaskRegistry registry;

        public Factory() {
        }

        private static InstallerTaskRegistry getRegistry() {
            if (registry == null) {
                synchronized (Factory.class) {
                    if (registry == null) {
                        registry = new InstallerTaskRegistry();
                    }
                }
            }
            return registry;
        }

        @Override
        public String getType() {
            return "download";
        }

        @Override
        public InstallerTask createTask(JsonElement config) {
            JsonObject obj = config.getAsJsonObject();
            String name = obj.has("name") ? obj.get("name").getAsString() : "Download";

            String url = getStringFromOs(obj, URL_JSON_PROPERTY);
            AssetFetcherInfo assetFetcherInfo = getAssetFetcher(obj);
            OutputInfo outputInfo = getOutputInfo(obj);

            // Parse onSuccess tasks
            InstallerTask onSuccessTask = null;
            if (obj.has("onSuccess")) {
                JsonElement onSuccess = obj.get("onSuccess");
                onSuccessTask = parseTaskNode(onSuccess);
            }

            return new DownloadTask(name, url, assetFetcherInfo, outputInfo, onSuccessTask);
        }

        private String getStringFromOs(JsonObject json, String property) {
            if (!json.has(property)) {
                return null;
            }
            JsonElement element = json.get(property);
            if (element.isJsonPrimitive()) {
                return element.getAsString();
            }
            if (element.isJsonObject()) {
                JsonObject osMap = element.getAsJsonObject();
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win") && osMap.has("windows")) {
                    return osMap.get("windows").getAsString();
                } else if (os.contains("mac") && osMap.has("mac")) {
                    return osMap.get("mac").getAsString();
                } else if ((os.contains("nix") || os.contains("nux")) && osMap.has("linux")) {
                    return osMap.get("linux").getAsString();
                } else if (osMap.has("default")) {
                    return osMap.get("default").getAsString();
                }
            }
            return null;
        }

        private AssetFetcherInfo getAssetFetcher(JsonObject json) {
            AssetFetcherInfo assetFetcher = getGithubAssetFetcher(json);
            if (assetFetcher != null) {
                return assetFetcher;
            }
            return getMavenArtifactFetcher(json);
        }

        private AssetFetcherInfo getGithubAssetFetcher(JsonObject json) {
            if (!json.has(GITHUB_JSON_PROPERTY)) {
                return null;
            }
            JsonElement githubElement = json.get(GITHUB_JSON_PROPERTY);
            if (!githubElement.isJsonObject()) {
                return null;
            }
            JsonObject githubObj = githubElement.getAsJsonObject();
            if (!githubObj.has(GITHUB_OWNER_JSON_PROPERTY) || !githubObj.has(GITHUB_REPOSITORY_JSON_PROPERTY)) {
                return null;
            }
            String owner = githubObj.get(GITHUB_OWNER_JSON_PROPERTY).getAsString();
            String repository = githubObj.get(GITHUB_REPOSITORY_JSON_PROPERTY).getAsString();
            String assetPattern = getStringFromOs(githubObj, GITHUB_ASSET_JSON_PROPERTY);
            if (assetPattern == null) {
                return null;
            }
            boolean prerelease = githubObj.has(GITHUB_PRERELEASE_JSON_PROPERTY) && githubObj.get(GITHUB_PRERELEASE_JSON_PROPERTY).getAsBoolean();

            var assetFetcher = InstallerTaskFactory.getGitHubAssetFetcherManager().getAssetFetcher(owner, repository);
            return new AssetFetcherInfo(assetFetcher,
                    new com.redhat.mcp.languagetools.installer.download.GitHubAssetFetcher.ReleaseMatcher(prerelease),
                    new com.redhat.mcp.languagetools.installer.download.GitHubAssetFetcher.AssetMatcher(assetPattern));
        }

        private AssetFetcherInfo getMavenArtifactFetcher(JsonObject json) {
            if (!json.has(MAVEN_JSON_PROPERTY)) {
                return null;
            }
            JsonElement mavenElement = json.get(MAVEN_JSON_PROPERTY);
            if (!mavenElement.isJsonObject()) {
                return null;
            }
            JsonObject mavenObj = mavenElement.getAsJsonObject();
            if (!mavenObj.has(MAVEN_GROUP_ID_JSON_PROPERTY) || !mavenObj.has(MAVEN_ARTIFACT_ID_JSON_PROPERTY)) {
                return null;
            }
            String groupId = mavenObj.get(MAVEN_GROUP_ID_JSON_PROPERTY).getAsString();
            String artifactId = mavenObj.get(MAVEN_ARTIFACT_ID_JSON_PROPERTY).getAsString();

            var assetFetcher = InstallerTaskFactory.getMavenArtifactFetcherManager().getArtifactFetcher(groupId, artifactId);
            return new AssetFetcherInfo(assetFetcher,
                    obj -> true,
                    obj -> true);
        }

        private OutputInfo getOutputInfo(JsonObject json) {
            if (!json.has(OUTPUT_JSON_PROPERTY)) {
                return null;
            }
            JsonElement outputElement = json.get(OUTPUT_JSON_PROPERTY);
            if (!outputElement.isJsonObject()) {
                return null;
            }
            JsonObject outputObj = outputElement.getAsJsonObject();
            String dir = getStringFromOs(outputObj, OUTPUT_DIR_JSON_PROPERTY);

            String fileName = null;
            boolean executable = false;
            if (outputObj.has(OUTPUT_FILE_JSON_PROPERTY)) {
                JsonElement fileElement = outputObj.get(OUTPUT_FILE_JSON_PROPERTY);
                if (fileElement.isJsonObject()) {
                    JsonObject fileObj = fileElement.getAsJsonObject();
                    fileName = getStringFromOs(fileObj, OUTPUT_FILE_NAME_JSON_PROPERTY);
                    executable = fileObj.has(OUTPUT_FILE_EXECUTABLE_JSON_PROPERTY) && fileObj.get(OUTPUT_FILE_EXECUTABLE_JSON_PROPERTY).getAsBoolean();
                }
            }
            return new OutputInfo(dir, fileName, executable);
        }

        private InstallerTask parseTaskNode(JsonElement taskNode) {
            if (taskNode == null || !taskNode.isJsonObject()) {
                return null;
            }

            JsonObject taskObj = taskNode.getAsJsonObject();
            if (taskObj.size() == 0) {
                return null;
            }

            String taskType = taskObj.keySet().iterator().next();
            JsonElement taskConfig = taskObj.get(taskType);

            return getRegistry().createTask(taskType, taskConfig);
        }
    }

    /**
     * Wrapper for ProgressMonitor that tracks download progress with TraceCollector.
     */
    private static class ProgressMonitorWrapper extends AbstractProgressMonitor implements ContentLengthAware {
        private final InstallerContext context;
        private final TraceCollector trace;
        private final String name;
        private long contentLength = -1;

        public ProgressMonitorWrapper(InstallerContext context, TraceCollector trace, String name) {
            this.context = context;
            this.trace = trace;
            this.name = name;
        }

        public void setContentLength(long contentLength) {
            this.contentLength = contentLength;
        }

        @Override
        public void reportProgress(double progress, String message) {
            // Build a message that includes the download percentage
            String progressMessage;
            if (contentLength > 0) {
                double fraction = progress / 100.0;
                long downloaded = (long) (fraction * contentLength);
                String downloadedMB = String.format("%.1f", downloaded / 1024.0 / 1024.0);
                String totalMB = String.format("%.1f", contentLength / 1024.0 / 1024.0);
                progressMessage = String.format("Downloading %s: %s MB / %s MB (%.0f%%)",
                        name, downloadedMB, totalMB, progress);
            } else {
                progressMessage = String.format("Downloading %s (%.0f%%)", name, progress);
            }

            // Update the progress monitor (for badge display and MCP progress)
            context.getProgress().reportProgress(progress, progressMessage);

            // Update trace console
            if (trace != null) {
                trace.update(progressMessage);
            }
        }

        @Override
        public void reportProgress(String message) {
            context.getProgress().reportProgress(message);
        }

        @Override
        public void setComplete() {
            context.getProgress().setComplete();
        }

        @Override
        public boolean isCancelled() {
            return context.getProgress().isCancelled();
        }

        @Override
        public void checkCancelled() {
            context.checkCanceled();
        }

        @Override
        public <T> java.util.concurrent.CompletableFuture<T> executeWithCancellation(java.util.concurrent.CompletableFuture<T> future) {
            return context.getProgress().executeWithCancellation(future);
        }

        @Override
        public boolean isSupported() {
            return context.getProgress().isSupported();
        }
    }

    /**
     * Reporter implementation for AssetFetcher that logs to TraceCollector.
     */
    private static class AssetFetcherReporter implements AssetFetcher.Reporter {
        private final TraceCollector trace;

        public AssetFetcherReporter(TraceCollector trace) {
            this.trace = trace;
        }

        @Override
        public void setText(String text) {
            if (trace != null) {
                trace.info(text);
            }
        }

        @Override
        public void setText(String text, Exception e) {
            if (trace != null) {
                trace.error(text + ": " + e.getMessage());
            }
        }
    }
}
