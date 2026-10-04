package com.ftpsystem.client;

/**
 * Giao dien nhan thong bao tien do truyen tai file (Progress Callback)
 */
public interface TransferProgressCallback {
    void onProgress(long transferredBytes, long totalBytes, double speedKbps, int percent);
    void onComplete(long totalBytes, long durationMs, double avgSpeedKbps, String checksumSHA256);
    void onError(String errorMessage);
}
