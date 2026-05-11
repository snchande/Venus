package com.venus.model;

public class ExecutionResult {

    private String sessionId;
    private String cellId;
    private String output = "";
    private String error = "";
    private String returnValue;
    private String status;
    private boolean success;
    private long executionTimeMs;
    private int executionCount;
    private String notebookId;
    /** True when this result was served from the notebook's saved output (not freshly executed). */
    private boolean cached;

    public ExecutionResult() {}

    public ExecutionResult(String sessionId, String cellId, String output, String error,
                           String returnValue, String status, boolean success,
                           long executionTimeMs, int executionCount) {
        this.sessionId = sessionId;
        this.cellId = cellId;
        this.output = output;
        this.error = error;
        this.returnValue = returnValue;
        this.status = status;
        this.success = success;
        this.executionTimeMs = executionTimeMs;
        this.executionCount = executionCount;
    }

    // Builder pattern
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final ExecutionResult r = new ExecutionResult();
        public Builder sessionId(String v)      { r.sessionId = v; return this; }
        public Builder cellId(String v)         { r.cellId = v; return this; }
        public Builder output(String v)         { r.output = v; return this; }
        public Builder error(String v)          { r.error = v; return this; }
        public Builder returnValue(String v)    { r.returnValue = v; return this; }
        public Builder status(String v)         { r.status = v; return this; }
        public Builder success(boolean v)       { r.success = v; return this; }
        public Builder executionTimeMs(long v)  { r.executionTimeMs = v; return this; }
        public Builder executionCount(int v)    { r.executionCount = v; return this; }
        public Builder notebookId(String v)     { r.notebookId = v; return this; }
        public Builder cached(boolean v)        { r.cached = v; return this; }
        public ExecutionResult build()          { return r; }
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getCellId() { return cellId; }
    public void setCellId(String cellId) { this.cellId = cellId; }

    public String getOutput() { return output; }
    public void setOutput(String output) { this.output = output; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public String getReturnValue() { return returnValue; }
    public void setReturnValue(String returnValue) { this.returnValue = returnValue; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public long getExecutionTimeMs() { return executionTimeMs; }
    public void setExecutionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; }

    public int getExecutionCount() { return executionCount; }
    public void setExecutionCount(int executionCount) { this.executionCount = executionCount; }

    public String getNotebookId() { return notebookId; }
    public void setNotebookId(String notebookId) { this.notebookId = notebookId; }

    public boolean isCached() { return cached; }
    public void setCached(boolean cached) { this.cached = cached; }
}
