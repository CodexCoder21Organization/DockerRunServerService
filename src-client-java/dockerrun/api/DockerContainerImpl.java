package dockerrun.api;

import foundation.url.sjvm.intrinsics.ServiceBridge;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Client-side DockerContainer implementation that runs inside SJVM.
 *
 * Property accessors dispatch to the server via ServiceBridge.rpc().
 */
public class DockerContainerImpl implements DockerContainer {

    private final String uuidString;

    public DockerContainerImpl(String uuidString) {
        this.uuidString = uuidString;
    }

    private Map<String, Object> makeParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("uuid", uuidString);
        return params;
    }

    @Override
    public UUID getUuid() {
        return UUID.fromString(uuidString);
    }

    @Override
    public String getImageReference() {
        Map<String, Object> result = ServiceBridge.rpc("getImageReference", makeParams());
        return String.valueOf(result.get("imageReference"));
    }

    @Override
    public Map<String, String> getEnvironmentVariables() {
        Map<String, Object> result = ServiceBridge.rpc("getEnvironmentVariables", makeParams());
        Object envObj = result.get("environmentVariables");
        if (envObj == null) {
            return new HashMap<>();
        }
        if (envObj instanceof Map) {
            Map<?, ?> rawMap = (Map<?, ?>) envObj;
            Map<String, String> envVars = new HashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    envVars.put(entry.getKey().toString(), entry.getValue().toString());
                }
            }
            return envVars;
        }
        return new HashMap<>();
    }

    @Override
    public ContainerStatus getStatus() {
        Map<String, Object> result = ServiceBridge.rpc("getStatus", makeParams());
        String statusStr = String.valueOf(result.get("status"));
        // Iterate through values instead of using valueOf() because SJVM
        // doesn't implement Class.isEnum() which valueOf() calls internally
        for (ContainerStatus status : ContainerStatus.values()) {
            if (status.name().equals(statusStr)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown container status: " + statusStr);
    }

    @Override
    public long getAutoTerminateSeconds() {
        Map<String, Object> result = ServiceBridge.rpc("getAutoTerminateSeconds", makeParams());
        Object val_ = result.get("autoTerminateSeconds");
        if (val_ instanceof Number) {
            return ((Number) val_).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(val_));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    @Override
    public long getCreatedAt() {
        Map<String, Object> result = ServiceBridge.rpc("getCreatedAt", makeParams());
        Object val_ = result.get("createdAt");
        if (val_ instanceof Number) {
            return ((Number) val_).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(val_));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    @Override
    public String getErrorMessage() {
        Map<String, Object> result = ServiceBridge.rpc("getErrorMessage", makeParams());
        Object msg = result.get("errorMessage");
        if (msg == null || "null".equals(msg.toString())) {
            return null;
        }
        return msg.toString();
    }

    @Override
    public String getDockerContainerId() {
        Map<String, Object> result = ServiceBridge.rpc("getDockerContainerId", makeParams());
        Object id = result.get("dockerContainerId");
        if (id == null || "null".equals(id.toString())) {
            return null;
        }
        return id.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof DockerContainer)) {
            return false;
        }
        return getUuid().equals(((DockerContainer) other).getUuid());
    }

    @Override
    public int hashCode() {
        return getUuid().hashCode();
    }

    @Override
    public String toString() {
        return "DockerContainer(uuid=" + getUuid() + ", image=" + getImageReference() + ", status=" + getStatus() + ")";
    }
}
