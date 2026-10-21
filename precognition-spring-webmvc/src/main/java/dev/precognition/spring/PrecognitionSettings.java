package dev.precognition.spring;

/** Bound to precognition.* by the Boot adapter. No global mutable request state lives here. */
public final class PrecognitionSettings {
    public enum NormalErrors { LARAVEL, PRESERVE }
    private boolean enabled = true;
    private NormalErrors normalErrors = NormalErrors.LARAVEL;
    private final Limits limits = new Limits();
    private final Observability observability = new Observability();
    public Observability getObservability() { return observability; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public NormalErrors getNormalErrors() { return normalErrors; }
    public void setNormalErrors(NormalErrors value) { normalErrors = value; }
    public Limits getLimits() { return limits; }
    public void validate() {
        if (normalErrors == null || limits.maxBodyBytes < 1 || limits.maxSelectionHeaderBytes < 1
                || limits.maxSelectedFields < 1 || limits.maxFieldNameLength < 1 || limits.maxDtoFields < 1
                || limits.maxViolationCount < 1 || limits.maxResponseBytes < 256
                || limits.maxSelectedFields > limits.maxDtoFields)
            throw new IllegalStateException("Invalid precognition limits or normal-errors setting");
    }
    public static final class Observability {
        private boolean metricsEnabled = true;
        public boolean isMetricsEnabled() { return metricsEnabled; }
        public void setMetricsEnabled(boolean value) { metricsEnabled = value; }
    }
    public static final class Limits {
        private int maxBodyBytes = 65536, maxSelectionHeaderBytes = 4096, maxSelectedFields = 64,
                maxFieldNameLength = 128, maxDtoFields = 128, maxViolationCount = 512, maxResponseBytes = 65536;
        public int getMaxBodyBytes() { return maxBodyBytes; }
        public void setMaxBodyBytes(int v) { maxBodyBytes = v; }
        public int getMaxSelectionHeaderBytes() { return maxSelectionHeaderBytes; }
        public void setMaxSelectionHeaderBytes(int v) { maxSelectionHeaderBytes = v; }
        public int getMaxSelectedFields() { return maxSelectedFields; }
        public void setMaxSelectedFields(int v) { maxSelectedFields = v; }
        public int getMaxFieldNameLength() { return maxFieldNameLength; }
        public void setMaxFieldNameLength(int v) { maxFieldNameLength = v; }
        public int getMaxDtoFields() { return maxDtoFields; }
        public void setMaxDtoFields(int v) { maxDtoFields = v; }
        public int getMaxViolationCount() { return maxViolationCount; }
        public void setMaxViolationCount(int v) { maxViolationCount = v; }
        public int getMaxResponseBytes() { return maxResponseBytes; }
        public void setMaxResponseBytes(int v) { maxResponseBytes = v; }
    }
}
