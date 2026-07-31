package com.zrlog.common.vo;

import java.io.Serializable;
import java.util.Date;

/**
 * 这个对应了 last.version.json 里面的字段
 */
public class Version implements Serializable {

    private String buildId;
    private String releaseDate;
    private String version;
    private Date buildDate;
    private long warFileSize;

    public long getWarFileSize() {
        return warFileSize;
    }

    public void setWarFileSize(long warFileSize) {
        this.warFileSize = warFileSize;
    }

    public long getZipFileSize() {
        return zipFileSize;
    }

    public void setZipFileSize(long zipFileSize) {
        this.zipFileSize = zipFileSize;
    }

    private long zipFileSize;
    private String changeLog;
    private String type;
    private String warDownloadUrl;

    public String getZipDownloadUrl() {
        return zipDownloadUrl;
    }

    public void setZipDownloadUrl(String zipDownloadUrl) {
        this.zipDownloadUrl = zipDownloadUrl;
    }

    private String zipDownloadUrl;
    private String warMd5sum;
    private String checksumAlgorithm;
    private String zipSha256;
    private String warSha256;
    private String sourceCommit;
    private String sourceRepository;
    private String buildWorkflow;

    public String getZipMd5sum() {
        return zipMd5sum;
    }

    public void setZipMd5sum(String zipMd5sum) {
        this.zipMd5sum = zipMd5sum;
    }

    public String getChecksumAlgorithm() {
        return checksumAlgorithm;
    }

    public void setChecksumAlgorithm(String checksumAlgorithm) {
        this.checksumAlgorithm = checksumAlgorithm;
    }

    public String getZipSha256() {
        return zipSha256;
    }

    public void setZipSha256(String zipSha256) {
        this.zipSha256 = zipSha256;
    }

    public String getWarSha256() {
        return warSha256;
    }

    public void setWarSha256(String warSha256) {
        this.warSha256 = warSha256;
    }

    public String getSourceCommit() {
        return sourceCommit;
    }

    public void setSourceCommit(String sourceCommit) {
        this.sourceCommit = sourceCommit;
    }

    public String getSourceRepository() {
        return sourceRepository;
    }

    public void setSourceRepository(String sourceRepository) {
        this.sourceRepository = sourceRepository;
    }

    public String getBuildWorkflow() {
        return buildWorkflow;
    }

    public void setBuildWorkflow(String buildWorkflow) {
        this.buildWorkflow = buildWorkflow;
    }

    private String zipMd5sum;

    public String getBuildId() {
        return buildId;
    }

    public void setBuildId(String buildId) {
        this.buildId = buildId;
    }

    public String getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(String releaseDate) {
        this.releaseDate = releaseDate;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getChangeLog() {
        return changeLog;
    }

    public void setChangeLog(String changeLog) {
        this.changeLog = changeLog;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getWarDownloadUrl() {
        return warDownloadUrl;
    }

    public void setWarDownloadUrl(String warDownloadUrl) {
        this.warDownloadUrl = warDownloadUrl;
    }

    public String getWarMd5sum() {
        return warMd5sum;
    }

    public void setWarMd5sum(String warMd5sum) {
        this.warMd5sum = warMd5sum;
    }

    public Date getBuildDate() {
        return buildDate;
    }

    public void setBuildDate(Date buildDate) {
        this.buildDate = buildDate;
    }
}
